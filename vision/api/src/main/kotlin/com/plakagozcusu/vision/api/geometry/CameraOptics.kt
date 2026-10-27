package com.plakagozcusu.vision.api.geometry

import kotlin.math.atan

/**
 * Pinhole-camera estimates used during calibration to explain *why* a mounting may or may not work.
 * These are theoretical upper bounds (perfect focus, no blur, no compression): the real GO/NO-GO
 * decision is made from measured plate pixel widths in actual captures (Phase 9), never from this.
 *
 * @param focalLengthMm physical focal length (Camera2 LENS_INFO_AVAILABLE_FOCAL_LENGTHS).
 * @param sensorWidthMm physical sensor width along the image width (SENSOR_INFO_PHYSICAL_SIZE).
 * @param imageWidthPx width of the image the estimate is for (e.g. the still resolution).
 */
data class CameraOptics(
    val focalLengthMm: Float,
    val sensorWidthMm: Float,
    val imageWidthPx: Int,
) {
    init {
        require(focalLengthMm > 0f && sensorWidthMm > 0f && imageWidthPx > 0)
    }

    /** Horizontal field of view in degrees at zoom 1x. */
    val horizontalFovDegrees: Double
        get() = Math.toDegrees(2.0 * atan(sensorWidthMm / (2.0 * focalLengthMm)))

    /**
     * Expected width in pixels of an object [objectWidthM] wide at [distanceM], with digital/optical
     * [zoomRatio] applied (digital zoom crops then upscales: it does not add real detail, so the
     * caller should treat zoomed estimates above the native ratio with care).
     */
    fun expectedPixelWidth(
        objectWidthM: Double,
        distanceM: Double,
        zoomRatio: Float = 1f,
    ): Double {
        require(objectWidthM > 0 && distanceM > 0 && zoomRatio > 0f)
        val pixelsPerMmOnSensor = imageWidthPx / sensorWidthMm.toDouble()
        val imageWidthOnSensorMm = focalLengthMm * zoomRatio * objectWidthM / distanceM
        return imageWidthOnSensorMm * pixelsPerMmOnSensor
    }

    companion object {
        /**
         * Standard single-line Turkish plate width (520 mm x 110 mm, same as the common EU format).
         * Two-line (square) plates are narrower; see docs/TECHNICAL_DECISIONS.md.
         */
        const val TURKISH_PLATE_WIDTH_M = 0.52
    }
}
