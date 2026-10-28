package com.plakagozcusu.feature.monitor.session

import com.google.common.truth.Truth.assertThat
import com.plakagozcusu.core.common.AppError
import com.plakagozcusu.core.model.AnalysisLoad
import com.plakagozcusu.core.model.SessionStats
import org.junit.Test
import com.plakagozcusu.feature.monitor.session.MonitoringEvent as E
import com.plakagozcusu.feature.monitor.session.MonitoringState as S

class MonitoringSessionTest {
    private fun run(vararg events: E): S = events.fold(S.Idle as S) { s, e -> MonitoringStateMachine.reduce(s, e) }

    private val started = arrayOf(E.StartRequested, E.CameraReady, E.ModelsReady("s1", 100))

    @Test
    fun `happy path reaches running`() {
        assertThat(run(*started)).isEqualTo(S.Running("s1", 100, SessionStats(), null))
    }

    @Test
    fun `events out of order are ignored`() {
        assertThat(run(E.CameraReady)).isEqualTo(S.Idle)
        assertThat(run(E.StartRequested, E.ModelsReady("x", 1))).isEqualTo(S.InitializingCamera)
    }

    @Test
    fun `pause keeps stats and only the same cause resumes`() {
        val paused = run(*started, E.StatsUpdated(SessionStats(vehicles = 3)), E.Pause(PauseReason.SCENE_SHIFT))
        assertThat(paused).isInstanceOf(S.Paused::class.java)

        assertThat(MonitoringStateMachine.reduce(paused, E.Resume(PauseReason.CAMERA_INTERRUPTED))).isEqualTo(paused)
        val resumed = MonitoringStateMachine.reduce(paused, E.Resume(PauseReason.SCENE_SHIFT)) as S.Running
        assertThat(resumed.stats.vehicles).isEqualTo(3)
    }

    @Test
    fun `critical heat pauses and cooling resumes degraded`() {
        val hot = run(*started, E.Thermal(ThermalLevel.CRITICAL))
        assertThat((hot as S.Paused).reason).isEqualTo(PauseReason.OVERHEATING)

        val cooler = MonitoringStateMachine.reduce(hot, E.Thermal(ThermalLevel.SEVERE)) as S.Running
        assertThat(cooler.degraded).isEqualTo(ThermalLevel.SEVERE)
        assertThat(
            (MonitoringStateMachine.reduce(cooler, E.Thermal(ThermalLevel.NORMAL)) as S.Running).degraded,
        ).isNull()
    }

    @Test
    fun `errors fail the session and a new start is allowed afterwards`() {
        val failed = run(*started, E.Error(AppError.CameraUnavailable()))
        assertThat(failed).isInstanceOf(S.Failed::class.java)
        assertThat(MonitoringStateMachine.reduce(failed, E.StartRequested)).isEqualTo(S.InitializingCamera)
    }

    @Test
    fun `stop goes through stopping to idle`() {
        assertThat(run(*started, E.StopRequested)).isEqualTo(S.Stopping)
        assertThat(run(*started, E.StopRequested, E.Stopped)).isEqualTo(S.Idle)
    }

    @Test
    fun `scheduler throttles to the profile rate and adapts to heat`() {
        val s = FrameScheduler(AnalysisLoad.BALANCED)
        val accepted = (0L..1_000L step 50).count { s.accept(it) }
        assertThat(accepted).isEqualTo(5) // 4 fps → every 250 ms: 0,250,500,750,1000

        s.thermal = ThermalLevel.CRITICAL
        assertThat(s.accept(5_000)).isFalse()
    }

    @Test
    fun `watchdog reports a stall only after the timeout`() {
        val w = StallWatchdog(timeoutMs = 5_000)
        assertThat(w.isStalled(10_000)).isFalse()
        w.progress(1_000)
        assertThat(w.isStalled(5_999)).isFalse()
        assertThat(w.isStalled(6_001)).isTrue()
    }
}
