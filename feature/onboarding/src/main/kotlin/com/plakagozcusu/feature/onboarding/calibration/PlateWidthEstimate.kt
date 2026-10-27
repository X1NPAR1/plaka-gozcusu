package com.plakagozcusu.feature.onboarding.calibration

import com.plakagozcusu.core.camera.CameraCapabilities
import com.plakagozcusu.vision.api.geometry.CameraOptics

/** Theoretical plate width in still pixels at a given distance; shown with an explicit "estimate" label. */
data class PlateWidthEstimate(
    val distanceM: Int,
    val pixels: Int,
)

/** Distances covering the assumed 10–40 m camera-to-road range. */
val ESTIMATE_DISTANCES_M = listOf(10, 20, 30, 40)

/**
 * Pinhole estimates for the current zoom, or empty when the phone does not report the optics needed
 * (focal length, sensor size, still width). Never substitutes made-up defaults.
 */
fun estimatePlateWidths(
    capabilities: CameraCapabilities?,
    zoomRatio: Float,
): List<PlateWidthEstimate> {
    val focal = capabilities?.focalLengthsMm?.minOrNull() ?: return emptyList()
    val sensorWidth = capabilities.sensorWidthMm ?: return emptyList()
    // Sensor width is reported for the native orientation (landscape), matching the still's long edge.
    val stillLongEdge =
        listOfNotNull(capabilities.maxStillWidth, capabilities.maxStillHeight).maxOrNull() ?: return emptyList()
    val optics = CameraOptics(focal, sensorWidth, stillLongEdge)
    return ESTIMATE_DISTANCES_M.map { d ->
        PlateWidthEstimate(
            d,
            optics.expectedPixelWidth(CameraOptics.TURKISH_PLATE_WIDTH_M, d.toDouble(), zoomRatio).toInt(),
        )
    }
}
