package com.plakagozcusu.core.camera

import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import androidx.annotation.OptIn
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.CameraInfo
import android.util.Size as AndroidSize

/** Reads the real optics/limits of the bound camera through Camera2 interop. */
internal object CapabilityReader {
    @OptIn(ExperimentalCamera2Interop::class)
    fun read(
        context: Context,
        info: CameraInfo,
        analysisResolution: AndroidSize?,
    ): CameraCapabilities {
        val c2 = Camera2CameraInfo.from(info)
        val physical = c2.getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)
        val afModes = c2.getCameraCharacteristic(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES) ?: IntArray(0)
        val streamMap = c2.getCameraCharacteristic(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        val largestJpeg = streamMap?.getOutputSizes(ImageFormat.JPEG)?.maxByOrNull { it.width.toLong() * it.height }
        val zoom = info.zoomState.value
        val exposure = info.exposureState
        val physicalIds =
            context
                .getSystemService(CameraManager::class.java)
                ?.getCameraCharacteristics(c2.cameraId)
                ?.physicalCameraIds
                ?.toList()
                .orEmpty()
        return CameraCapabilities(
            cameraId = c2.cameraId,
            focalLengthsMm =
                c2.getCameraCharacteristic(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.toList().orEmpty(),
            sensorWidthMm = physical?.width,
            sensorHeightMm = physical?.height,
            minZoomRatio = zoom?.minZoomRatio ?: 1f,
            maxZoomRatio = zoom?.maxZoomRatio ?: 1f,
            hasAutoFocus = afModes.any { it != CameraCharacteristics.CONTROL_AF_MODE_OFF },
            minFocusDistanceDiopters =
                c2.getCameraCharacteristic(
                    CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE,
                ),
            exposureIndexMin = exposure.exposureCompensationRange.lower,
            exposureIndexMax = exposure.exposureCompensationRange.upper,
            exposureStep = exposure.exposureCompensationStep.toFloat(),
            maxStillWidth = largestJpeg?.width,
            maxStillHeight = largestJpeg?.height,
            analysisWidth = analysisResolution?.width ?: 0,
            analysisHeight = analysisResolution?.height ?: 0,
            physicalCameraIds = physicalIds,
        )
    }
}
