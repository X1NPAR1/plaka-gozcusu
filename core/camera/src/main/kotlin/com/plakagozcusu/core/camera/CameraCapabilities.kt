package com.plakagozcusu.core.camera

/**
 * What the bound back camera can actually do, read from Camera2 characteristics at bind time.
 * Recorded in the session's device info and shown in calibration so decisions are based on the
 * real phone, not assumptions (e.g. whether an optical telephoto exists).
 */
data class CameraCapabilities(
    val cameraId: String,
    val focalLengthsMm: List<Float>,
    val sensorWidthMm: Float?,
    val sensorHeightMm: Float?,
    val minZoomRatio: Float,
    val maxZoomRatio: Float,
    val hasAutoFocus: Boolean,
    /** Diopters; 0 means fixed focus. Null when the device does not report it. */
    val minFocusDistanceDiopters: Float?,
    val exposureIndexMin: Int,
    val exposureIndexMax: Int,
    /** EV per exposure index step. */
    val exposureStep: Float,
    val maxStillWidth: Int?,
    val maxStillHeight: Int?,
    val analysisWidth: Int,
    val analysisHeight: Int,
    /**
     * Physical camera ids behind this logical camera (multi-camera devices switch lenses by zoom
     * ratio). Empty on single-sensor phones; a telephoto, if any, shows up here.
     */
    val physicalCameraIds: List<String>,
)
