package com.plakagozcusu.core.camera

import androidx.camera.core.SurfaceRequest
import androidx.lifecycle.LifecycleOwner
import com.plakagozcusu.core.common.AppError
import com.plakagozcusu.core.common.AppResult
import com.plakagozcusu.vision.api.geometry.PointF
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * In-memory [CameraController] for unit/UI tests and the pipeline replay harness. It records calls
 * and returns scripted results; it never pretends to be a real device in production code paths
 * (it lives in the testFixtures source set and never ships in the APK).
 */
class FakeCameraController(
    private val capabilitiesOnBind: CameraCapabilities? = null,
) : CameraController {
    private val _state = MutableStateFlow<CameraState>(CameraState.Idle)
    override val state: StateFlow<CameraState> = _state.asStateFlow()
    override val surfaceRequest: StateFlow<SurfaceRequest?> = MutableStateFlow(null)
    private val _capabilities = MutableStateFlow<CameraCapabilities?>(null)
    override val capabilities: StateFlow<CameraCapabilities?> = _capabilities.asStateFlow()

    var bindResult: AppResult<Unit> = AppResult.Success(Unit)
    var stillResult: AppResult<StillImage> = AppResult.Failure(AppError.CameraUnavailable())
    val focusCalls = mutableListOf<Pair<PointF, Boolean>>()
    var analyzer: FrameAnalyzer? = null
        private set

    override suspend fun bind(
        owner: LifecycleOwner,
        config: CameraConfig,
        analyzer: FrameAnalyzer?,
    ): AppResult<Unit> {
        this.analyzer = analyzer
        val result = bindResult
        _state.value =
            when (result) {
                is AppResult.Success -> {
                    CameraState.Ready(
                        config.initialZoomRatio,
                        focusLocked = false,
                        exposureIndex = 0,
                    )
                }

                is AppResult.Failure -> {
                    CameraState.Failed(result.error)
                }
            }
        _capabilities.value = capabilitiesOnBind
        return result
    }

    override fun unbind() {
        analyzer = null
        _state.value = CameraState.Idle
    }

    override fun setZoomRatio(ratio: Float) =
        _state.update {
            if (it is CameraState.Ready) it.copy(zoomRatio = ratio) else it
        }

    override fun focusAt(
        scenePoint: PointF,
        lock: Boolean,
    ) {
        focusCalls += scenePoint to lock
        _state.update { if (it is CameraState.Ready) it.copy(focusLocked = lock) else it }
    }

    var targetRotation: Int = 0
        private set

    override fun setTargetRotation(rotation: Int) {
        targetRotation = rotation
    }

    override fun cancelFocusLock() = _state.update { if (it is CameraState.Ready) it.copy(focusLocked = false) else it }

    override fun setExposureCompensationIndex(index: Int) =
        _state.update { if (it is CameraState.Ready) it.copy(exposureIndex = index) else it }

    override suspend fun captureStill(): AppResult<StillImage> = stillResult

    /** Simulates another app taking the camera. */
    fun interrupt(error: AppError) {
        _state.value = CameraState.Interrupted(error)
    }
}
