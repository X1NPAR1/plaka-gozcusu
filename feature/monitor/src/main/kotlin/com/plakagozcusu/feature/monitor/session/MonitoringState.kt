package com.plakagozcusu.feature.monitor.session

import com.plakagozcusu.core.common.AppError
import com.plakagozcusu.core.model.SessionStats

/** Thermal pressure mapped from PowerManager thermal status. */
enum class ThermalLevel { NORMAL, MODERATE, SEVERE, CRITICAL }

enum class PauseReason { CAMERA_INTERRUPTED, SCENE_SHIFT, OVERHEATING, STORAGE_FULL, STALLED }

/** Monitoring session state machine (docs/ARCHITECTURE.md). */
sealed interface MonitoringState {
    data object Idle : MonitoringState

    data object InitializingCamera : MonitoringState

    data object LoadingModels : MonitoringState

    data class Running(
        val sessionId: String,
        val startedAtMillis: Long,
        val stats: SessionStats,
        val degraded: ThermalLevel?,
    ) : MonitoringState

    /** Temporarily not producing events; [resumeTo] is restored when the cause clears. */
    data class Paused(
        val reason: PauseReason,
        val resumeTo: Running,
    ) : MonitoringState

    data object Stopping : MonitoringState

    data class Failed(
        val error: AppError,
    ) : MonitoringState
}

sealed interface MonitoringEvent {
    data object StartRequested : MonitoringEvent

    data object CameraReady : MonitoringEvent

    data class ModelsReady(
        val sessionId: String,
        val nowMillis: Long,
    ) : MonitoringEvent

    data class Pause(
        val reason: PauseReason,
    ) : MonitoringEvent

    data class Resume(
        val reason: PauseReason,
    ) : MonitoringEvent

    data class Thermal(
        val level: ThermalLevel,
    ) : MonitoringEvent

    data class StatsUpdated(
        val stats: SessionStats,
    ) : MonitoringEvent

    data class Error(
        val error: AppError,
    ) : MonitoringEvent

    data object StopRequested : MonitoringEvent

    data object Stopped : MonitoringEvent
}

/** Pure transition function; events that do not apply leave the state unchanged. */
object MonitoringStateMachine {
    fun reduce(
        state: MonitoringState,
        event: MonitoringEvent,
    ): MonitoringState =
        when (event) {
            MonitoringEvent.StartRequested, MonitoringEvent.CameraReady, is MonitoringEvent.ModelsReady -> {
                startup(
                    state,
                    event,
                )
            }

            is MonitoringEvent.Pause -> {
                pause(state, event.reason)
            }

            is MonitoringEvent.Resume -> {
                resume(state, event.reason)
            }

            is MonitoringEvent.Thermal -> {
                thermal(state, event.level)
            }

            is MonitoringEvent.StatsUpdated -> {
                stats(state, event)
            }

            is MonitoringEvent.Error -> {
                MonitoringState.Failed(event.error)
            }

            MonitoringEvent.StopRequested -> {
                if (state is MonitoringState.Idle) state else MonitoringState.Stopping
            }

            MonitoringEvent.Stopped -> {
                MonitoringState.Idle
            }
        }

    private fun startup(
        state: MonitoringState,
        event: MonitoringEvent,
    ): MonitoringState =
        when {
            event == MonitoringEvent.StartRequested &&
                (state is MonitoringState.Idle || state is MonitoringState.Failed) -> {
                MonitoringState.InitializingCamera
            }

            event == MonitoringEvent.CameraReady && state is MonitoringState.InitializingCamera -> {
                MonitoringState.LoadingModels
            }

            event is MonitoringEvent.ModelsReady && state is MonitoringState.LoadingModels -> {
                MonitoringState.Running(event.sessionId, event.nowMillis, SessionStats(), degraded = null)
            }

            else -> {
                state
            }
        }

    private fun pause(
        state: MonitoringState,
        reason: PauseReason,
    ): MonitoringState = if (state is MonitoringState.Running) MonitoringState.Paused(reason, state) else state

    /** Only the cause that paused the session may resume it. */
    private fun resume(
        state: MonitoringState,
        reason: PauseReason,
    ): MonitoringState = if (state is MonitoringState.Paused && state.reason == reason) state.resumeTo else state

    private fun stats(
        state: MonitoringState,
        event: MonitoringEvent.StatsUpdated,
    ): MonitoringState =
        when (state) {
            is MonitoringState.Running -> state.copy(stats = event.stats)
            is MonitoringState.Paused -> state.copy(resumeTo = state.resumeTo.copy(stats = event.stats))
            else -> state
        }

    private fun thermal(
        state: MonitoringState,
        level: ThermalLevel,
    ): MonitoringState {
        val degraded = level.takeIf { it != ThermalLevel.NORMAL }
        val coolingDown = state is MonitoringState.Paused && state.reason == PauseReason.OVERHEATING
        return when {
            state is MonitoringState.Running && level == ThermalLevel.CRITICAL -> {
                MonitoringState.Paused(
                    PauseReason.OVERHEATING,
                    state,
                )
            }

            state is MonitoringState.Running -> {
                state.copy(degraded = degraded)
            }

            coolingDown && level != ThermalLevel.CRITICAL -> {
                state.resumeTo.copy(
                    degraded = degraded,
                )
            }

            else -> {
                state
            }
        }
    }
}
