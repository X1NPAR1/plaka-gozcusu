package com.plakagozcusu.feature.onboarding.calibration

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.viewModelScope
import com.google.common.truth.Truth.assertThat
import com.plakagozcusu.core.camera.CameraCapabilities
import com.plakagozcusu.core.camera.FakeCameraController
import com.plakagozcusu.core.camera.StillImage
import com.plakagozcusu.core.common.AppError
import com.plakagozcusu.core.common.AppResult
import com.plakagozcusu.core.datastore.SettingsRepository
import com.plakagozcusu.vision.api.PlateReader
import com.plakagozcusu.vision.api.geometry.PointF
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class CalibrationViewModelTest {
    private val caps =
        CameraCapabilities(
            cameraId = "0",
            focalLengthsMm = listOf(4.7f),
            sensorWidthMm = 6.4f,
            sensorHeightMm = 4.8f,
            minZoomRatio = 1f,
            maxZoomRatio = 8f,
            hasAutoFocus = true,
            minFocusDistanceDiopters = 10f,
            exposureIndexMin = -12,
            exposureIndexMax = 12,
            exposureStep = 1f / 6f,
            maxStillWidth = 4000,
            maxStillHeight = 3000,
            analysisWidth = 1280,
            analysisHeight = 960,
            physicalCameraIds = emptyList(),
        )
    private val camera = FakeCameraController(caps)
    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var viewModel: CalibrationViewModel
    private lateinit var settings: SettingsRepository

    @get:Rule val tmp = TemporaryFolder()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        val store = PreferenceDataStoreFactory.create { File(tmp.root, "cal.preferences_pb") }
        settings = SettingsRepository(store)
        viewModel = CalibrationViewModel(camera, settings) { mockk<PlateReader>(relaxed = true) }
    }

    @After
    fun tearDown() {
        // Stop the VM's own coroutines (settings load) before the Main dispatcher is reset.
        viewModel.viewModelScope.cancel()
        Dispatchers.resetMain()
    }

    @Test
    fun `taps add road polygon points in ROI mode`() =
        runTest(dispatcher) {
            val collector = backgroundScope.launch { viewModel.uiState.collect { } }
            viewModel.bind(mockk<LifecycleOwner>(relaxed = true))

            listOf(PointF(0.1f, 0.6f), PointF(0.9f, 0.6f), PointF(0.5f, 0.95f)).forEach(viewModel::onPreviewTap)

            assertThat(viewModel.uiState.value.roi.isComplete).isTrue()
            assertThat(camera.focusCalls).isEmpty()
            collector.cancel()
        }

    @Test
    fun `taps focus the camera in focus mode and lock toggles`() =
        runTest(dispatcher) {
            backgroundScope.launch { viewModel.uiState.collect { } }
            viewModel.bind(mockk(relaxed = true))
            viewModel.setTapMode(TapMode.FOCUS)

            viewModel.onPreviewTap(PointF(0.4f, 0.7f))
            viewModel.toggleFocusLock()

            assertThat(camera.focusCalls.last()).isEqualTo(PointF(0.4f, 0.7f) to true)
            assertThat(viewModel.uiState.value.focusLocked).isTrue()
            assertThat(viewModel.uiState.value.roi.points).isEmpty()
        }

    @Test
    fun `test capture reports the real still size`() =
        runTest(dispatcher) {
            backgroundScope.launch { viewModel.uiState.collect { } }
            viewModel.bind(mockk(relaxed = true))
            camera.stillResult = AppResult.Success(StillImage(ByteArray(1234), 4000, 3000, 90, 1L))

            viewModel.testCapture()

            val state = viewModel.uiState.first { it.lastCapture != null }
            assertThat(state.lastCapture).isEqualTo(TestCaptureInfo(4000, 3000, 1234))
            assertThat(state.capturing).isFalse()
        }

    @Test
    fun `failed capture is surfaced, not hidden`() =
        runTest(dispatcher) {
            backgroundScope.launch { viewModel.uiState.collect { } }
            viewModel.bind(mockk(relaxed = true))
            camera.stillResult = AppResult.Failure(AppError.CameraInUseByAnotherApp())

            viewModel.testCapture()

            assertThat(viewModel.uiState.value.captureError).isInstanceOf(AppError.CameraInUseByAnotherApp::class.java)
            assertThat(viewModel.uiState.value.lastCapture).isNull()
        }

    @Test
    fun `estimates come from reported optics and scale with zoom`() =
        runTest(dispatcher) {
            backgroundScope.launch { viewModel.uiState.collect { } }
            viewModel.bind(mockk(relaxed = true))
            val at1x =
                viewModel.uiState.value.estimates
                    .first { it.distanceM == 10 }
                    .pixels

            viewModel.onZoom(2f)

            val at2x =
                viewModel.uiState.value.estimates
                    .first { it.distanceM == 10 }
                    .pixels
            assertThat(at1x).isEqualTo(152)
            assertThat(at2x).isEqualTo(305)
        }

    @Test
    fun `save persists the calibration and completes onboarding`() =
        runTest(dispatcher) {
            backgroundScope.launch { viewModel.uiState.collect { } }
            viewModel.bind(mockk(relaxed = true))
            listOf(PointF(0.1f, 0.6f), PointF(0.9f, 0.6f), PointF(0.5f, 0.95f)).forEach(viewModel::onPreviewTap)
            viewModel.onZoom(2f)
            val saved = CompletableDeferred<Unit>()

            viewModel.save { saved.complete(Unit) }

            saved.await()
            val s = settings.settings.first()
            assertThat(s.onboardingDone).isTrue()
            assertThat(s.calibration.zoomRatio).isEqualTo(2f)
            assertThat(s.calibration.roiPoints).hasSize(3)
        }

    @Test
    fun `no optics reported means no estimate rather than a guess`() {
        assertThat(estimatePlateWidths(caps.copy(sensorWidthMm = null), 1f)).isEmpty()
        assertThat(estimatePlateWidths(null, 1f)).isEmpty()
    }
}
