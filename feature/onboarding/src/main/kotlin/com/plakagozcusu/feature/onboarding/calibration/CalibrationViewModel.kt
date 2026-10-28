package com.plakagozcusu.feature.onboarding.calibration

import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.plakagozcusu.core.camera.CameraCapabilities
import com.plakagozcusu.core.camera.CameraConfig
import com.plakagozcusu.core.camera.CameraController
import com.plakagozcusu.core.camera.CameraState
import com.plakagozcusu.core.camera.StillImage
import com.plakagozcusu.core.common.AppError
import com.plakagozcusu.core.common.AppResult
import com.plakagozcusu.core.datastore.SettingsRepository
import com.plakagozcusu.core.model.Calibration
import com.plakagozcusu.vision.api.PlateReader
import com.plakagozcusu.vision.api.geometry.PointF
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Provider

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
    val reading: Boolean = false,
    val testRead: TestReadResult? = null,
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
        private val settings: SettingsRepository,
        // Provider: the OCR engine is created only when a test read is requested.
        private val plateReader: Provider<PlateReader>,
    ) : ViewModel() {
        private val local = MutableStateFlow(CalibrationUiState())

        init {
            // Start from the saved calibration so re-calibrating only adjusts what changed.
            viewModelScope.launch {
                val saved = settings.settings.first().calibration
                local.update { s -> s.copy(roi = RoiEditor(saved.roiPoints.map { PointF(it.first, it.second) })) }
            }
        }

        /** Persists zoom, ROI, focus and exposure and marks onboarding as done. */
        fun save(onSaved: () -> Unit) {
            val ui = uiState.value
            val ready = ui.camera as? CameraState.Ready
            viewModelScope.launch {
                settings.update { s ->
                    s.copy(
                        onboardingDone = true,
                        calibration =
                            Calibration(
                                zoomRatio = ui.zoomRatio,
                                roiPoints =
                                    ui.roi.points
                                        .map { it.x to it.y }
                                        .takeIf { ui.roi.isComplete }
                                        .orEmpty(),
                                focusLocked = ui.focusLocked,
                                focusX = ui.focusPoint?.x ?: s.calibration.focusX,
                                focusY = ui.focusPoint?.y ?: s.calibration.focusY,
                                exposureIndex = ready?.exposureIndex ?: s.calibration.exposureIndex,
                            ),
                    )
                }
                onSaved()
            }
        }

        val surfaceRequest = camera.surfaceRequest

        val uiState: StateFlow<CalibrationUiState> =
            combine(local, camera.state, camera.capabilities) { l, cam, caps ->
                l.copy(camera = cam, capabilities = caps)
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), CalibrationUiState())

        private var bindJob: Job? = null

        private var boundOwner: LifecycleOwner? = null

        init {
            // If the monitoring service releases the camera after we bound (its stop is asynchronous),
            // the camera falls back to Idle; rebind while the calibration screen is still showing.
            viewModelScope.launch {
                camera.state.collect { state ->
                    val owner = boundOwner
                    if (state is CameraState.Idle && owner != null && bindJob?.isActive != true) {
                        bindJob = launch { camera.bind(owner, CameraConfig(withPreview = true), analyzer = null) }
                    }
                }
            }
        }

        fun bind(owner: LifecycleOwner) {
            boundOwner = owner
            bindJob?.cancel()
            bindJob = viewModelScope.launch { camera.bind(owner, CameraConfig(withPreview = true), analyzer = null) }
        }

        fun unbind() {
            boundOwner = null
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
                        readPlate(result.value)
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

        /** Runs OCR on the full-resolution still so the user can verify reading with a held/parked plate. */
        private fun readPlate(still: StillImage) {
            local.update { it.copy(reading = true, testRead = null) }
            viewModelScope.launch {
                val result =
                    withContext(Dispatchers.Default) {
                        runCatching {
                            val (image, scale) = TestPlateRead.decodeUpright(still.jpeg, still.rotationDegrees)
                            val reader = plateReader.get()
                            try {
                                TestPlateRead.summarize(reader.read(image), image, scale)
                            } finally {
                                reader.close()
                            }
                        }.getOrNull()
                    }
                local.update { it.copy(reading = false, testRead = result ?: TestReadResult(null, emptyList(), null)) }
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
