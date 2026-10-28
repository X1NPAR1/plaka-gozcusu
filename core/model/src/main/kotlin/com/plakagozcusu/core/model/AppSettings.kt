package com.plakagozcusu.core.model

/** Analysis load profile; concrete frame rates are tuned from on-device measurements. */
enum class AnalysisLoad { LOW, BALANCED, HIGH }

enum class ThemeMode { DARK, LIGHT, SYSTEM }

/**
 * User settings with validated ranges. Defaults are conservative starting points, documented in
 * docs/PERFORMANCE.md and revisited after the real-device pilot.
 */
data class AppSettings(
    val onboardingDone: Boolean = false,
    val analysisLoad: AnalysisLoad = AnalysisLoad.BALANCED,
    val minDetectionConfidence: Float = DEFAULT_DETECTION_CONFIDENCE,
    val minQualityForHighConfidence: Float = DEFAULT_HIGH_CONFIDENCE_QUALITY,
    val motionThresholdPxPerSec: Float = DEFAULT_MOTION_THRESHOLD,
    val duplicateWindowSec: Int = DEFAULT_DUPLICATE_WINDOW_SEC,
    val maxStillsPerTrack: Int = DEFAULT_MAX_STILLS_PER_TRACK,
    val saveContextFrames: Boolean = true,
    val retentionDays: Int = DEFAULT_RETENTION_DAYS,
    val storageCapMb: Int = DEFAULT_STORAGE_CAP_MB,
    val showOverlays: Boolean = true,
    val themeMode: ThemeMode = ThemeMode.DARK,
    val calibration: Calibration = Calibration(),
) {
    fun validated(): AppSettings =
        copy(
            minDetectionConfidence = minDetectionConfidence.coerceIn(MIN_CONF, MAX_CONF),
            minQualityForHighConfidence = minQualityForHighConfidence.coerceIn(MIN_CONF, MAX_CONF),
            motionThresholdPxPerSec = motionThresholdPxPerSec.coerceIn(1f, MAX_MOTION),
            duplicateWindowSec = duplicateWindowSec.coerceIn(0, MAX_DUP_WINDOW),
            maxStillsPerTrack = maxStillsPerTrack.coerceIn(1, MAX_STILLS),
            retentionDays = retentionDays.coerceIn(1, MAX_RETENTION_DAYS),
            storageCapMb = storageCapMb.coerceIn(MIN_CAP_MB, MAX_CAP_MB),
        )

    companion object {
        const val DEFAULT_DETECTION_CONFIDENCE = 0.5f
        const val DEFAULT_HIGH_CONFIDENCE_QUALITY = 0.7f
        const val DEFAULT_MOTION_THRESHOLD = 15f
        const val DEFAULT_DUPLICATE_WINDOW_SEC = 60
        const val DEFAULT_MAX_STILLS_PER_TRACK = 2
        const val DEFAULT_RETENTION_DAYS = 30
        const val DEFAULT_STORAGE_CAP_MB = 4_096
        private const val MIN_CONF = 0.05f
        private const val MAX_CONF = 0.99f
        private const val MAX_MOTION = 500f
        private const val MAX_DUP_WINDOW = 3_600
        private const val MAX_STILLS = 5
        private const val MAX_RETENTION_DAYS = 365
        private const val MIN_CAP_MB = 256
        private const val MAX_CAP_MB = 65_536
    }
}

/** Mounting calibration; ROI points are normalized upright scene coordinates as "x,y;x,y;...". */
data class Calibration(
    val zoomRatio: Float = 1f,
    val roiPoints: List<Pair<Float, Float>> = emptyList(),
    val focusLocked: Boolean = false,
    val focusX: Float = CENTER,
    val focusY: Float = CENTER,
    val exposureIndex: Int = 0,
) {
    companion object {
        private const val CENTER = 0.5f

        fun encodeRoi(points: List<Pair<Float, Float>>): String =
            points.joinToString(";") { "${it.first},${it.second}" }

        fun decodeRoi(text: String?): List<Pair<Float, Float>> =
            text
                ?.split(';')
                ?.mapNotNull { part ->
                    val xy = part.split(',')
                    val x = xy.getOrNull(0)?.toFloatOrNull()
                    val y = xy.getOrNull(1)?.toFloatOrNull()
                    (if (x != null && y != null) x to y else null)
                        ?.takeIf { (px, py) -> px in 0f..1f && py in 0f..1f }
                }.orEmpty()
    }
}
