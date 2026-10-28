package com.plakagozcusu.feature.monitor.service

import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.SystemClock
import androidx.core.app.ServiceCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.plakagozcusu.core.camera.CameraConfig
import com.plakagozcusu.core.camera.CameraController
import com.plakagozcusu.core.camera.CameraState
import com.plakagozcusu.core.camera.StillImage
import com.plakagozcusu.core.common.AppError
import com.plakagozcusu.core.common.AppLogger
import com.plakagozcusu.core.common.AppResult
import com.plakagozcusu.core.common.TimeSource
import com.plakagozcusu.core.data.EvidenceBytes
import com.plakagozcusu.core.data.ObservationRepository
import com.plakagozcusu.core.datastore.SettingsRepository
import com.plakagozcusu.core.model.AppSettings
import com.plakagozcusu.core.model.MonitoringSession
import com.plakagozcusu.core.model.RecognitionStatus
import com.plakagozcusu.core.model.SessionStats
import com.plakagozcusu.feature.monitor.MonitorController
import com.plakagozcusu.feature.monitor.R
import com.plakagozcusu.feature.monitor.session.FrameScheduler
import com.plakagozcusu.feature.monitor.session.MonitoringEvent
import com.plakagozcusu.feature.monitor.session.MonitoringState
import com.plakagozcusu.feature.monitor.session.PauseReason
import com.plakagozcusu.feature.monitor.session.StallWatchdog
import com.plakagozcusu.feature.monitor.session.ThermalLevel
import com.plakagozcusu.vision.api.capture.CaptureRateLimiter
import com.plakagozcusu.vision.api.geometry.PointF
import com.plakagozcusu.vision.api.geometry.RegionOfInterest
import com.plakagozcusu.vision.models.EfficientDetVehicleDetector
import com.plakagozcusu.vision.models.MlKitPlateReader
import com.plakagozcusu.vision.pipeline.AnalysisFrame
import com.plakagozcusu.vision.pipeline.DecisionThresholds
import com.plakagozcusu.vision.pipeline.DuplicateLinker
import com.plakagozcusu.vision.pipeline.FrameResult
import com.plakagozcusu.vision.pipeline.PipelineConfig
import com.plakagozcusu.vision.pipeline.PipelineEngine
import com.plakagozcusu.vision.pipeline.TrackOutcome
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject

/**
 * User-started camera foreground service that runs the whole monitoring loop, so it keeps working
 * with the screen off where the platform allows (behaviour per device is verified in Phase 9).
 * The analysis thread runs the pipeline synchronously; stale frames are dropped by CameraX.
 */
@AndroidEntryPoint
class MonitoringService : LifecycleService() {
    @Inject lateinit var camera: CameraController

    @Inject lateinit var controller: MonitorController

    @Inject lateinit var repository: ObservationRepository

    @Inject lateinit var settingsRepository: SettingsRepository

    @Inject lateinit var logger: AppLogger

    @Inject lateinit var time: TimeSource

    private lateinit var notifications: MonitoringNotifications
    private var session: Job? = null
    private var thermal: ThermalMonitor? = null
    private var engine: PipelineEngine? = null
    private val pendingStills = ConcurrentHashMap<Long, StillImage>()

    /** Everything a running session needs to persist outcomes, including at stop time. */
    private class SessionContext(
        val sessionId: String,
        val anchor: ClockAnchor,
        val linker: DuplicateLinker,
        val settings: AppSettings,
        val handler: (FrameResult) -> Unit,
    )

    private var context: SessionContext? = null
    private val stats = SessionStatsCounter()

    override fun onCreate() {
        super.onCreate()
        notifications = MonitoringNotifications(this)
        notifications.ensureChannels()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        super.onStartCommand(intent, flags, startId)
        if (intent?.action == ACTION_STOP) {
            stopSession(endReason = "user_stop")
            return START_NOT_STICKY
        }
        if (session?.isActive == true) return START_NOT_STICKY
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA else 0
        ServiceCompat.startForeground(
            this,
            MonitoringNotifications.ID_ONGOING,
            notifications.ongoing(controller.state.value),
            type,
        )
        controller.dispatch(MonitoringEvent.StartRequested)
        session = lifecycleScope.launch { runSession() }
        // Not sticky: restarting a camera service without the user present is not allowed anyway.
        return START_NOT_STICKY
    }

    private suspend fun runSession() {
        val settings = settingsRepository.settings.first()
        val sessionId = UUID.randomUUID().toString()
        val anchor = ClockAnchor(time.now().toEpochMilli(), SystemClock.elapsedRealtime())
        val scheduler = FrameScheduler(settings.analysisLoad)
        val watchdog = StallWatchdog(STALL_TIMEOUT_MS)
        val linker = DuplicateLinker(settings.duplicateWindowSec * MS_PER_S)
        stats.reset()

        val built = buildEngine(settings) ?: return
        engine = built
        val ctx = SessionContext(sessionId, anchor, linker, settings) { result -> onFrameResult(result) }
        context = ctx
        val analyzer = SessionAnalyzer(built, scheduler, watchdog, ctx.handler)
        if (camera.bind(this, CameraConfig(withPreview = true), analyzer) is AppResult.Failure) {
            fail(AppError.CameraUnavailable())
            return
        }
        applyCalibration(settings)
        controller.dispatch(MonitoringEvent.CameraReady)
        controller.dispatch(MonitoringEvent.ModelsReady(sessionId, anchor.wallMillis))
        repository.upsertSession(
            sessionRecord(sessionId, anchor.wallMillis, settings, stats.snapshot(), endedAt = null, endReason = null),
        )
        thermal =
            ThermalMonitor(this) { level ->
                scheduler.thermal = level
                controller.dispatch(MonitoringEvent.Thermal(level))
                if (level == ThermalLevel.CRITICAL) notifications.warn(R.string.pause_overheating)
            }.also { it.start() }
        observeCamera()
        superviseLoop(watchdog, settings)
    }

    private fun buildEngine(settings: AppSettings): PipelineEngine? =
        try {
            PipelineEngine(
                detector = EfficientDetVehicleDetector(this, settings.minDetectionConfidence),
                reader = MlKitPlateReader(),
                encoder = JpegEncoder(),
                config =
                    PipelineConfig(
                        roi = settings.calibration.roiPoints.toRoi(),
                        minMotionPxPerSec = settings.motionThresholdPxPerSec,
                        decision = DecisionThresholds(minQuality = settings.minQualityForHighConfidence),
                        duplicateWindowMs = settings.duplicateWindowSec * MS_PER_S,
                        capturePolicy =
                            CaptureRateLimiter.Policy(
                                STILL_COOLDOWN_MS,
                                settings.maxStillsPerTrack,
                                STILLS_PER_MINUTE,
                            ),
                    ),
            )
        } catch (e: java.io.IOException) {
            logger.error(stage = "models", code = "model_unavailable", throwable = e)
            fail(AppError.ModelUnavailable("efficientdet", e))
            null
        }

    private fun applyCalibration(s: AppSettings) {
        val c = s.calibration
        camera.setZoomRatio(c.zoomRatio)
        camera.setExposureCompensationIndex(c.exposureIndex)
        if (c.focusLocked) camera.focusAt(PointF(c.focusX, c.focusY), lock = true)
    }

    private fun observeCamera() {
        lifecycleScope.launch {
            camera.state.collect { s ->
                when (s) {
                    is CameraState.Interrupted -> pause(PauseReason.CAMERA_INTERRUPTED, R.string.pause_camera)
                    is CameraState.Ready -> controller.dispatch(MonitoringEvent.Resume(PauseReason.CAMERA_INTERRUPTED))
                    is CameraState.Failed -> fail(s.error)
                    else -> Unit
                }
            }
        }
    }

    /** Watchdog + periodic stats; rebinding the camera when no frame was processed for a while. */
    private suspend fun superviseLoop(
        watchdog: StallWatchdog,
        settings: AppSettings,
    ) {
        while (lifecycleScope.isActive && controller.state.value !is MonitoringState.Idle) {
            delay(SUPERVISE_INTERVAL_MS)
            controller.dispatch(MonitoringEvent.StatsUpdated(stats.snapshot()))
            notifications.notifyOngoing(controller.state.value)
            val running = controller.state.value is MonitoringState.Running
            if (running && watchdog.isStalled(SystemClock.elapsedRealtime())) rebind(watchdog, settings)
        }
    }

    private suspend fun rebind(
        watchdog: StallWatchdog,
        settings: AppSettings,
    ) {
        logger.warn(stage = "watchdog", code = "pipeline_stalled_rebinding")
        repository.logDiagnostic("watchdog", "stall_rebind", null)
        watchdog.reset()
        val engine = engine ?: return
        val handler = context?.handler ?: return
        camera.unbind()
        camera.bind(
            this,
            CameraConfig(withPreview = true),
            SessionAnalyzer(engine, FrameScheduler(settings.analysisLoad), watchdog, handler),
        )
        applyCalibration(settings)
    }

    private fun onFrameResult(result: FrameResult) {
        controller.publishOverlay(result.tracks)
        if (result.sceneShift) {
            pause(PauseReason.SCENE_SHIFT, R.string.pause_scene_shift)
        } else {
            controller.dispatch(MonitoringEvent.Resume(PauseReason.SCENE_SHIFT))
        }
        result.stillRequestForTrack?.let { trackId ->
            lifecycleScope.launch {
                val still = camera.captureStill()
                if (still is AppResult.Success) pendingStills[trackId] = still.value
            }
        }
        val ctx = context ?: return
        result.finalized.forEach { outcome -> lifecycleScope.launch { persist(outcome, ctx) } }
    }

    private suspend fun persist(
        outcome: TrackOutcome,
        ctx: SessionContext,
    ) {
        val id = UUID.randomUUID().toString()
        val r = outcome.recognition
        val linked =
            synchronized(ctx.linker) { ctx.linker.link(r.plateText, r.status, outcome.summary.timing.bestFrameMs, id) }
        val observation =
            OutcomeMapper.toObservation(
                id,
                ctx.sessionId,
                outcome,
                ctx.anchor,
                time.zone().id,
                MODEL_VERSIONS,
                appVersion(this),
                linked,
                time.now().toEpochMilli(),
            )
        // A matching high-res still (untouched camera JPEG) replaces the analysis-frame scene.
        val scene = pendingStills.remove(outcome.summary.trackId)?.jpeg ?: outcome.evidence.sceneJpeg
        val saved =
            repository.save(
                observation,
                EvidenceBytes(scene, outcome.evidence.vehicleCropJpeg, outcome.evidence.plateCropJpeg),
            )
        if (saved is AppResult.Failure) {
            logger.error(stage = "persist", code = "save_failed", throwable = saved.error.cause)
            if (saved.error is AppError.StorageFull) pause(PauseReason.STORAGE_FULL, R.string.pause_storage)
            return
        }
        stats.record(r.status)
    }

    private fun pause(
        reason: PauseReason,
        text: Int,
    ) {
        val before = controller.state.value
        controller.dispatch(MonitoringEvent.Pause(reason))
        if (before is MonitoringState.Running) notifications.warn(text)
    }

    private fun fail(error: AppError) {
        controller.dispatch(MonitoringEvent.Error(error))
        notifications.warn(R.string.notif_failed)
        stopSession(endReason = "failed_${error::class.simpleName}")
    }

    private fun stopSession(endReason: String) {
        controller.dispatch(MonitoringEvent.StopRequested)
        val running = controller.state.value
        thermal?.stop()
        thermal = null
        camera.unbind()
        session?.cancel()
        val e = engine
        val ctx = context
        engine = null
        context = null
        lifecycleScope.launch {
            // Finalize live tracks so a pass in progress at stop time is still recorded.
            if (e != null && ctx != null) e.flush(FLUSH_W, FLUSH_H).forEach { persist(it, ctx) }
            val sessionId = (running as? MonitoringState.Running)?.sessionId ?: ctx?.sessionId
            if (sessionId != null && ctx != null) {
                repository.upsertSession(
                    sessionRecord(
                        sessionId,
                        ctx.anchor.wallMillis,
                        ctx.settings,
                        stats.snapshot(),
                        time.now().toEpochMilli(),
                        endReason,
                    ),
                )
            }
            controller.dispatch(MonitoringEvent.Stopped)
            controller.publishOverlay(emptyList())
            ServiceCompat.stopForeground(this@MonitoringService, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    override fun onDestroy() {
        thermal?.stop()
        super.onDestroy()
    }

    /** Analysis-thread adapter: throttle → convert → pipeline (blocking on this thread only). */
    private inner class SessionAnalyzer(
        private val engine: PipelineEngine,
        private val scheduler: FrameScheduler,
        private val watchdog: StallWatchdog,
        private val onResult: (FrameResult) -> Unit,
    ) : com.plakagozcusu.core.camera.FrameAnalyzer {
        override fun analyze(frame: androidx.camera.core.ImageProxy) {
            val now = SystemClock.elapsedRealtime()
            watchdog.progress(now)
            stats.frameSeen()
            if (controller.state.value is MonitoringState.Paused &&
                (controller.state.value as MonitoringState.Paused).reason != PauseReason.SCENE_SHIFT
            ) {
                return
            }
            if (!scheduler.accept(now)) {
                stats.frameDropped()
                return
            }
            val image = FrameConverter.toUpright(frame)
            // The pipeline is suspend only for ML Kit's callback; blocking here is intended: this is
            // the dedicated analysis thread and CameraX drops frames while we work.
            val result = runBlocking { engine.process(AnalysisFrame(now, image)) }
            onResult(result)
        }
    }

    /** Thread-safe running counters for the session row and dashboard. */
    private class SessionStatsCounter {
        private val analyzed = AtomicLong()
        private val dropped = AtomicLong()
        private val vehicles = AtomicLong()
        private val read = AtomicLong()
        private val uncertain = AtomicLong()
        private val unreadable = AtomicLong()

        fun reset() = listOf(analyzed, dropped, vehicles, read, uncertain, unreadable).forEach { it.set(0) }

        fun frameSeen() = analyzed.incrementAndGet()

        fun frameDropped() = dropped.incrementAndGet()

        fun record(status: RecognitionStatus) {
            vehicles.incrementAndGet()
            when (status) {
                RecognitionStatus.HIGH_CONFIDENCE -> read.incrementAndGet()
                RecognitionStatus.NEEDS_REVIEW -> uncertain.incrementAndGet()
                RecognitionStatus.UNREADABLE, RecognitionStatus.NO_PLATE_VISIBLE -> unreadable.incrementAndGet()
                else -> Unit
            }
        }

        fun snapshot() =
            SessionStats(
                framesAnalyzed = analyzed.get(),
                framesDropped = dropped.get(),
                vehicles = vehicles.get().toInt(),
                platesRead = read.get().toInt(),
                uncertain = uncertain.get().toInt(),
                unreadable = unreadable.get().toInt(),
            )
    }

    companion object {
        const val ACTION_STOP = "com.plakagozcusu.action.STOP_MONITORING"
        private const val STALL_TIMEOUT_MS = 10_000L
        private const val SUPERVISE_INTERVAL_MS = 2_000L
        private const val MS_PER_S = 1_000L
        private const val STILL_COOLDOWN_MS = 1_500L
        private const val STILLS_PER_MINUTE = 20
        private const val FLUSH_W = 1280
        private const val FLUSH_H = 960
        private const val MODEL_VERSIONS = "vehicle=efficientdet-lite0-int8;plate=mlkit-text-latin-16.0.1"
    }
}

private fun List<Pair<Float, Float>>.toRoi(): RegionOfInterest =
    if (size >=
        RegionOfInterest.MIN_POINTS
    ) {
        RegionOfInterest(map { PointF(it.first, it.second) })
    } else {
        RegionOfInterest.FULL_FRAME
    }

@Suppress("LongParameterList") // Flat session row.
private fun sessionRecord(
    id: String,
    startedAt: Long,
    s: AppSettings,
    stats: SessionStats,
    endedAt: Long?,
    endReason: String?,
) = MonitoringSession(
    id = id,
    startedAt = startedAt,
    endedAt = endedAt,
    deviceInfo = "${Build.MANUFACTURER} ${Build.MODEL} API ${Build.VERSION.SDK_INT}",
    settingsSnapshot = "load=${s.analysisLoad} zoom=${s.calibration.zoomRatio} roi=${s.calibration.roiPoints.size}",
    stats = stats,
    endReason = endReason,
)

private fun appVersion(context: android.content.Context): String =
    context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "unknown"
