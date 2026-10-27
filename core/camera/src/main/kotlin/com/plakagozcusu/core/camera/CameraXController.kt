package com.plakagozcusu.core.camera

import android.content.Context
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceOrientedMeteringPointFactory
import androidx.camera.core.SurfaceRequest
import androidx.camera.core.UseCaseGroup
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import com.plakagozcusu.core.common.AppError
import com.plakagozcusu.core.common.AppLogger
import com.plakagozcusu.core.common.AppResult
import com.plakagozcusu.vision.api.geometry.FrameTransform
import com.plakagozcusu.vision.api.geometry.PointF
import com.plakagozcusu.vision.api.geometry.Size
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import javax.inject.Inject
import kotlin.coroutines.resume
import android.util.Size as AndroidSize
import androidx.camera.core.CameraState as XCameraState

/**
 * CameraX implementation: Preview (optional) + ImageAnalysis + ImageCapture bound as one group with
 * a shared 4:3 aspect ratio so analysis coordinates map onto stills (see [FrameTransform]).
 * If the device cannot run all three at once, it falls back to analysis + capture (no preview).
 */
class CameraXController
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val logger: AppLogger,
    ) : CameraController {
        private val _state = MutableStateFlow<CameraState>(CameraState.Idle)
        override val state: StateFlow<CameraState> = _state.asStateFlow()

        private val _surfaceRequest = MutableStateFlow<SurfaceRequest?>(null)
        override val surfaceRequest: StateFlow<SurfaceRequest?> = _surfaceRequest.asStateFlow()

        private val _capabilities = MutableStateFlow<CameraCapabilities?>(null)
        override val capabilities: StateFlow<CameraCapabilities?> = _capabilities.asStateFlow()

        // Single thread: analysis is strictly sequential and stale frames are dropped by CameraX.
        private var analysisExecutor: ExecutorService? = null
        private var provider: ProcessCameraProvider? = null
        private var camera: Camera? = null
        private var imageCapture: ImageCapture? = null
        private var imageAnalysis: ImageAnalysis? = null

        // Incremented by every bind/unbind; a bind that resumes after a newer call is stale and must
        // not touch the camera (fixes unbind-before-bind races on fast navigation). Main thread only.
        private var generation = 0L
        private var observedCameraState: LiveData<XCameraState>? = null
        private var cameraStateObserver: Observer<XCameraState>? = null

        // Still decoding copies multi-MB JPEGs; keep it off the main thread. Lives as long as the singleton.
        private val captureExecutor by lazy { Executors.newSingleThreadExecutor { r -> Thread(r, "pg-capture") } }

        override suspend fun bind(
            owner: LifecycleOwner,
            config: CameraConfig,
            analyzer: FrameAnalyzer?,
        ): AppResult<Unit> {
            val bindGeneration = ++generation
            releaseBinding()
            _state.value = CameraState.Initializing
            val cameraProvider =
                when (val result = obtainBackCameraProvider(bindGeneration, owner)) {
                    is AppResult.Success -> result.value
                    is AppResult.Failure -> return result
                }
            provider = cameraProvider

            val ratio43 =
                AspectRatioStrategy(
                    androidx.camera.core.AspectRatio.RATIO_4_3,
                    // No fallback: preview, analysis and stills must share one aspect so taps, ROI and
                    // boxes map 1:1 (Camera2 back cameras offer 4:3, the native sensor aspect).
                    AspectRatioStrategy.FALLBACK_RULE_NONE,
                )
            val analysis = buildAnalysis(config, ratio43, analyzer)
            val capture = buildCapture(ratio43)
            val preview = if (config.withPreview) buildPreview(ratio43) else null

            val bound =
                tryBind(cameraProvider, owner, listOfNotNull(preview, analysis, capture))
                    ?: preview?.let {
                        // Fallback: some low-end devices cannot stream 3 use cases at these sizes.
                        logger.warn(stage = "camera", code = "fallback_without_preview")
                        _surfaceRequest.value = null
                        tryBind(cameraProvider, owner, listOf(analysis, capture))
                    }
                    ?: run {
                        releaseBinding()
                        return failWith(AppError.CameraUnavailable(), "bind_failed")
                    }

            camera = bound
            imageCapture = capture
            imageAnalysis = analysis
            observeCameraState(owner, bound)
            _capabilities.value =
                runCatching { CapabilityReader.read(context, bound.cameraInfo, analysis.resolutionInfo?.resolution) }
                    .onFailure { logger.warn(stage = "camera", code = "capabilities_unreadable", throwable = it) }
                    .getOrNull()
            _state.value = bound.readyState(focusLocked = false)
            // Goes through the clamped path so the reported zoom is one the device supports.
            setZoomRatio(config.initialZoomRatio)
            logger.info(stage = "camera", code = "bound")
            return AppResult.Success(Unit)
        }

        /**
         * Provider with a usable back camera; failures also move the state to [CameraState.Failed].
         * Returns a failure without touching state when the bind became stale while suspended.
         */
        private suspend fun obtainBackCameraProvider(
            bindGeneration: Long,
            owner: LifecycleOwner,
        ): AppResult<ProcessCameraProvider> {
            val cameraProvider =
                try {
                    ProcessCameraProvider.awaitInstance(context)
                } catch (
                    @Suppress("TooGenericExceptionCaught") e: Exception,
                ) {
                    return failWith(AppError.CameraUnavailable(e), "provider_unavailable")
                }
            return when {
                bindGeneration != generation || owner.lifecycle.currentState == Lifecycle.State.DESTROYED -> {
                    logger.info(stage = "camera", code = "stale_bind_ignored")
                    AppResult.Failure(AppError.CameraUnavailable())
                }

                cameraProvider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) -> {
                    AppResult.Success(cameraProvider)
                }

                else -> {
                    failWith(AppError.CameraUnavailable(), "no_back_camera")
                }
            }
        }

        private fun buildPreview(ratio: AspectRatioStrategy): Preview =
            Preview
                .Builder()
                .setResolutionSelector(ResolutionSelector.Builder().setAspectRatioStrategy(ratio).build())
                .build()
                .also { it.setSurfaceProvider { request -> _surfaceRequest.value = request } }

        private fun buildAnalysis(
            config: CameraConfig,
            ratio: AspectRatioStrategy,
            analyzer: FrameAnalyzer?,
        ): ImageAnalysis {
            val analysis =
                ImageAnalysis
                    .Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                    .setResolutionSelector(
                        ResolutionSelector
                            .Builder()
                            .setAspectRatioStrategy(ratio)
                            .setResolutionStrategy(
                                ResolutionStrategy(
                                    AndroidSize(config.analysisWidth, config.analysisHeight),
                                    ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER,
                                ),
                            ).build(),
                    ).build()
            if (analyzer != null) {
                val executor = Executors.newSingleThreadExecutor { r -> Thread(r, "pg-analysis") }
                analysisExecutor = executor
                analysis.setAnalyzer(executor, ClosingAnalyzer(analyzer, logger))
            }
            return analysis
        }

        private fun tryBind(
            provider: ProcessCameraProvider,
            owner: LifecycleOwner,
            useCases: List<androidx.camera.core.UseCase>,
        ): Camera? {
            val group = UseCaseGroup.Builder().apply { useCases.forEach(::addUseCase) }.build()
            return try {
                provider.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, group)
            } catch (e: IllegalArgumentException) {
                logger.warn(stage = "camera", code = "use_case_combination_unsupported", throwable = e)
                provider.unbindAll()
                null
            } catch (e: IllegalStateException) {
                // Owner destroyed meanwhile, or a use case is bound elsewhere.
                logger.warn(stage = "camera", code = "bind_illegal_state", throwable = e)
                provider.unbindAll()
                null
            }
        }

        private fun observeCameraState(
            owner: LifecycleOwner,
            camera: Camera,
        ) {
            val liveData = camera.cameraInfo.cameraState
            val observer =
                Observer<XCameraState> { cameraState ->
                    val error = cameraState.error
                    if (error != null) {
                        logger.warn(stage = "camera", code = "state_error_${error.code}")
                        _state.value = CameraErrorMapper.map(error)
                    } else if (cameraState.type == androidx.camera.core.CameraState.Type.OPEN &&
                        _state.value is CameraState.Interrupted
                    ) {
                        // CameraX reopened the camera after an interruption.
                        _state.value = camera.readyState(focusLocked = false)
                    }
                }
            observedCameraState = liveData
            cameraStateObserver = observer
            liveData.observe(owner, observer)
        }

        override fun unbind() {
            generation++
            releaseBinding()
            _state.value = CameraState.Idle
        }

        /** Idempotent, non-blocking release of everything a bind created. */
        private fun releaseBinding() {
            cameraStateObserver?.let { observer -> observedCameraState?.removeObserver(observer) }
            cameraStateObserver = null
            observedCameraState = null
            provider?.unbindAll()
            imageAnalysis?.clearAnalyzer()
            // No awaitTermination: this runs on the main thread; the analyzer finishes its frame and exits.
            analysisExecutor?.shutdown()
            analysisExecutor = null
            camera = null
            imageCapture = null
            imageAnalysis = null
            _surfaceRequest.value = null
        }

        override fun setZoomRatio(ratio: Float) {
            val cam = camera ?: return
            val zoom = cam.cameraInfo.zoomState.value
            val clamped = if (zoom != null) ratio.coerceIn(zoom.minZoomRatio, zoom.maxZoomRatio) else ratio
            cam.cameraControl.setZoomRatio(clamped)
            _state.update { if (it is CameraState.Ready) it.copy(zoomRatio = clamped) else it }
        }

        override fun focusAt(
            scenePoint: PointF,
            lock: Boolean,
        ) {
            val cam = camera ?: return
            val analysis = imageAnalysis ?: return
            val resolution = analysis.resolutionInfo ?: return
            // Scene (upright, normalized) -> analysis buffer pixels, then a metering point relative
            // to the analysis surface so the tap lands on the same physical spot.
            val transform =
                FrameTransform(
                    Size(resolution.resolution.width, resolution.resolution.height),
                    rotationDegrees = resolution.rotationDegrees,
                )
            val bufferPoint = transform.sceneToBuffer(scenePoint)
            // Passing the use case makes the factory honour the analysis stream's crop/aspect.
            val factory =
                SurfaceOrientedMeteringPointFactory(
                    resolution.resolution.width.toFloat(),
                    resolution.resolution.height.toFloat(),
                    analysis,
                )
            val action =
                FocusMeteringAction
                    .Builder(
                        factory.createPoint(bufferPoint.x, bufferPoint.y),
                        FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE,
                    ).apply { if (lock) disableAutoCancel() }
                    .build()
            cam.cameraControl.startFocusAndMetering(action)
            _state.update { if (it is CameraState.Ready) it.copy(focusLocked = lock) else it }
        }

        override fun cancelFocusLock() {
            camera?.cameraControl?.cancelFocusAndMetering()
            _state.update { if (it is CameraState.Ready) it.copy(focusLocked = false) else it }
        }

        override fun setExposureCompensationIndex(index: Int) {
            val cam = camera ?: return
            val exposure = cam.cameraInfo.exposureState
            if (!exposure.isExposureCompensationSupported) return
            val clamped =
                index.coerceIn(
                    exposure.exposureCompensationRange.lower,
                    exposure.exposureCompensationRange.upper,
                )
            cam.cameraControl.setExposureCompensationIndex(clamped)
            _state.update { if (it is CameraState.Ready) it.copy(exposureIndex = clamped) else it }
        }

        override suspend fun captureStill(): AppResult<StillImage> {
            val capture = imageCapture ?: return AppResult.Failure(AppError.CameraUnavailable())
            return suspendCancellableCoroutine { cont ->
                capture.takePicture(
                    captureExecutor,
                    object : ImageCapture.OnImageCapturedCallback() {
                        override fun onCaptureSuccess(image: ImageProxy) {
                            val result = image.use { StillImageReader.read(it) }
                            cont.resume(result)
                        }

                        override fun onError(exception: ImageCaptureException) {
                            logger.warn(
                                stage = "capture",
                                code = "still_failed_${exception.imageCaptureError}",
                                throwable = exception,
                            )
                            cont.resume(AppResult.Failure(AppError.CameraUnavailable(exception)))
                        }
                    },
                )
            }
        }

        private fun <T> failWith(
            error: AppError,
            code: String,
        ): AppResult<T> {
            logger.error(stage = "camera", code = code, throwable = error.cause)
            _state.value = CameraState.Failed(error)
            return AppResult.Failure(error)
        }
    }

private fun Camera.readyState(focusLocked: Boolean) =
    CameraState.Ready(
        zoomRatio = cameraInfo.zoomState.value?.zoomRatio ?: 1f,
        focusLocked = focusLocked,
        exposureIndex = cameraInfo.exposureState.exposureCompensationIndex,
    )

/** Highest-resolution JPEG stills; originals are stored untouched. */
private fun buildCapture(ratio: AspectRatioStrategy): ImageCapture =
    ImageCapture
        .Builder()
        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
        .setResolutionSelector(
            ResolutionSelector
                .Builder()
                .setAspectRatioStrategy(ratio)
                .setResolutionStrategy(ResolutionStrategy.HIGHEST_AVAILABLE_STRATEGY)
                .build(),
        ).build()
