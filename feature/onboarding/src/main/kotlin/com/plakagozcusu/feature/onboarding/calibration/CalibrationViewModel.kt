package com.plakagozcusu.feature.onboarding.calibration

import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.plakagozcusu.core.camera.CameraCapabilities
import com.plakagozcusu.core.camera.CameraConfig
import com.plakagozcusu.core.camera.CameraController
import com.plakagozcusu.core.camera.CameraState
import com.plakagozcusu.core.common.AppError
import com.plakagozcusu.core.common.AppResult
import com.plakagozcusu.vision.api.geometry.PointF
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class TapMode { ROI, FOCUS }

/** Result of a test capture: real numbers read from the captured still. */
data class TestCaptureInfo(
    val width: Int,
    val height: Int,
    val sizeBytes: Int,
)

data class CalibrationUiState(
    val camera: CameraState = CameraState.Idle,
    val capabilities: CameraCapabilities? = null,
    val tapMode: TapMode = TapMode.ROI,
    val roi: RoiEditor = RoiEditor(),
    val focusPoint: PointF? = null,
    val capturing: Boolean = false,
    val lastCapture: TestCaptureInfo? = null,
    val captureError: AppError? = null,
) {
    val zoomRatio: Float get() = (camera as? CameraState.Ready)?.zoomRatio ?: 1f
    val focusLocked: Boolean get() = (camera as? CameraState.Ready)?.focusLocked ?: false
    val estimates: List<PlateWidthEstimate> get() = estimatePlateWidths(capabilities, zoomRatio)
}

/**
 * Mounting & calibration: zoom, ROI drawing, focus lock, exposure, test capture.
 * Calibration values are persisted by the settings DataStore (Phase 4); until then they live here.
 */
@HiltViewModel
class CalibrationViewModel
    @Inject
    constructor(
        private val camera: CameraController,
    ) : ViewModel() {
        private val local = MutableStateFlow(CalibrationUiState())

        val surfaceRequest = camera.surfaceRequest

        val uiState: StateFlow<CalibrationUiState> =
            combine(local, camera.state, camera.capabilities) { l, cam, caps ->
                l.copy(camera = cam, capabilities = caps)
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), CalibrationUiState())

        private var bindJob: Job? = null

        fun bind(owner: LifecycleOwner) {
            bindJob?.cancel()
            bindJob = viewModelScope.launch { camera.bind(owner, CameraConfig(withPreview = true), analyzer = null) }
        }

        fun unbind() {
            bindJob?.cancel()
            camera.unbind()
        }

        fun setTapMode(mode: TapMode) = local.update { it.copy(tapMode = mode) }

        fun onZoom(ratio: Float) = camera.setZoomRatio(ratio)

        fun onExposure(index: Int) = camera.setExposureCompensationIndex(index)

        fun onPreviewTap(scenePoint: PointF) {
            when (local.value.tapMode) {
                TapMode.ROI -> {
                    local.update { it.copy(roi = it.roi.add(scenePoint)) }
                }

                TapMode.FOCUS -> {
                    camera.focusAt(scenePoint, lock = uiState.value.focusLocked)
                    local.update { it.copy(focusPoint = scenePoint) }
                }
            }
        }

        fun toggleFocusLock() {
            val point = local.value.focusPoint ?: CENTER
            if (uiState.value.focusLocked) camera.cancelFocusLock() else camera.focusAt(point, lock = true)
        }

        fun undoRoi() = local.update { it.copy(roi = it.roi.undo()) }

        fun clearRoi() = local.update { it.copy(roi = it.roi.clear()) }

        fun testCapture() {
            if (local.value.capturing) return
            local.update { it.copy(capturing = true, captureError = null) }
            viewModelScope.launch {
                when (val result = camera.captureStill()) {
                    is AppResult.Success -> {
                        local.update {
                            it.copy(
                                capturing = false,
                                lastCapture =
                                    TestCaptureInfo(
                                        result.value.width,
                                        result.value.height,
                                        result.value.jpeg.size,
                                    ),
                            )
                        }
                    }

                    is AppResult.Failure -> {
                        local.update { it.copy(capturing = false, captureError = result.error) }
                    }
                }
            }
        }

        override fun onCleared() {
            // The screen's DisposableEffect normally unbinds first; unbind is idempotent.
            unbind()
        }

        private companion object {
            const val STOP_TIMEOUT_MS = 5_000L
            val CENTER = PointF(0.5f, 0.5f)
        }
    }
