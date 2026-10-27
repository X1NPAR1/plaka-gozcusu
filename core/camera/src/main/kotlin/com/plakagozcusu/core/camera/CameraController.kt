package com.plakagozcusu.core.camera

import androidx.camera.core.SurfaceRequest
import androidx.lifecycle.LifecycleOwner
import com.plakagozcusu.core.common.AppError
import com.plakagozcusu.core.common.AppResult
import com.plakagozcusu.vision.api.geometry.PointF
import kotlinx.coroutines.flow.StateFlow

/**
 * Camera boundary used by onboarding/calibration (Activity lifecycle) and by the monitoring
 * foreground service (service lifecycle, Phase 7). A fake implementation drives UI and pipeline
 * tests without hardware.
 */
interface CameraController {
    val state: StateFlow<CameraState>

    /** Preview surface requests for a viewfinder; null when no preview is bound (e.g. dim mode). */
    val surfaceRequest: StateFlow<SurfaceRequest?>

    val capabilities: StateFlow<CameraCapabilities?>

    /** Binds preview (optional), analysis (optional) and still capture to [owner]'s lifecycle. */
    suspend fun bind(
        owner: LifecycleOwner,
        config: CameraConfig,
        analyzer: FrameAnalyzer?,
    ): AppResult<Unit>

    fun unbind()

    fun setZoomRatio(ratio: Float)

    /**
     * Focus + meter at [scenePoint] (normalized upright coordinates). With [lock] the AF/AE stays
     * at that point instead of auto-cancelling, which keeps the road in focus at night.
     */
    fun focusAt(
        scenePoint: PointF,
        lock: Boolean,
    )

    fun cancelFocusLock()

    fun setExposureCompensationIndex(index: Int)

    suspend fun captureStill(): AppResult<StillImage>
}

data class CameraConfig(
    val withPreview: Boolean,
    /** Target analysis resolution (long edge first); the camera picks the closest supported size. */
    val analysisWidth: Int = DEFAULT_ANALYSIS_WIDTH,
    val analysisHeight: Int = DEFAULT_ANALYSIS_HEIGHT,
    val initialZoomRatio: Float = 1f,
) {
    companion object {
        // Starting point only; the real default is chosen from on-device measurements (Phase 10).
        const val DEFAULT_ANALYSIS_WIDTH = 1280
        const val DEFAULT_ANALYSIS_HEIGHT = 960
    }
}

sealed interface CameraState {
    data object Idle : CameraState

    data object Initializing : CameraState

    data class Ready(
        val zoomRatio: Float,
        val focusLocked: Boolean,
        val exposureIndex: Int,
    ) : CameraState

    /** Temporarily unavailable (another app, incoming call); CameraX reopens it automatically. */
    data class Interrupted(
        val error: AppError,
    ) : CameraState

    data class Failed(
        val error: AppError,
    ) : CameraState
}

/** A JPEG still exactly as delivered by the camera, plus what is needed to map coordinates. */
class StillImage(
    val jpeg: ByteArray,
    val width: Int,
    val height: Int,
    val rotationDegrees: Int,
    /** Sensor timestamp (ns, same clock as analysis frames) used to match the still to a track. */
    val sensorTimestampNanos: Long,
)
