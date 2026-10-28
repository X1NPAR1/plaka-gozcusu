package com.plakagozcusu.vision.pipeline.quality

import com.plakagozcusu.vision.api.ImageBuffer

/** Raw quality signals of a plate (or vehicle) crop; each is one signal, none is "the truth". */
data class QualitySignals(
    val widthPx: Int,
    val heightPx: Int,
    /** Variance of the Laplacian on luminance: higher = sharper. */
    val sharpness: Double,
    /** Fraction of pixels at the clipping limits (glare / crushed blacks). */
    val clippedFraction: Double,
    val meanLuminance: Double,
    /** Box touches the frame border, so the plate may be cut off. */
    val touchesEdge: Boolean,
)

object ImageQuality {
    private const val R_W = 0.299
    private const val G_W = 0.587
    private const val B_W = 0.114
    private const val CLIP_HIGH = 250
    private const val CLIP_LOW = 5
    private const val BYTE = 0xFF
    private const val RED_SHIFT = 16
    private const val GREEN_SHIFT = 8
    private const val KERNEL = 3
    private const val NEIGHBOURS = 4

    fun measure(
        crop: ImageBuffer,
        touchesEdge: Boolean,
    ): QualitySignals {
        val w = crop.width
        val h = crop.height
        val lum = IntArray(w * h) { i -> luminance(crop.argb[i]) }
        var clipped = 0
        var sum = 0L
        for (v in lum) {
            if (v >= CLIP_HIGH || v <= CLIP_LOW) clipped++
            sum += v
        }
        return QualitySignals(
            widthPx = w,
            heightPx = h,
            sharpness = laplacianVariance(lum, w, h),
            clippedFraction = clipped.toDouble() / lum.size,
            meanLuminance = sum.toDouble() / lum.size,
            touchesEdge = touchesEdge,
        )
    }

    private fun luminance(p: Int): Int =
        ((p shr RED_SHIFT and BYTE) * R_W + (p shr GREEN_SHIFT and BYTE) * G_W + (p and BYTE) * B_W).toInt()

    private fun laplacianVariance(
        lum: IntArray,
        w: Int,
        h: Int,
    ): Double {
        if (w < KERNEL || h < KERNEL) return 0.0
        var n = 0
        var mean = 0.0
        var m2 = 0.0
        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                val i = y * w + x
                val lap = (NEIGHBOURS * lum[i] - lum[i - 1] - lum[i + 1] - lum[i - w] - lum[i + w]).toDouble()
                n++
                val d = lap - mean
                mean += d / n
                m2 += d * (lap - mean)
            }
        }
        return m2 / n
    }
}

data class QualityWeights(
    val minPlateWidthPx: Int = 60,
    val goodPlateWidthPx: Int = 140,
    val goodSharpness: Double = 400.0,
    val maxClippedFraction: Double = 0.25,
)

/**
 * Folds signals into a 0..1 score. The defaults are starting points to be tuned with the replay
 * harness and the real-device pilot (docs/PERFORMANCE.md); they are not measured truths.
 */
object QualityScorer {
    private const val SIZE_W = 0.45f
    private const val SHARP_W = 0.35f
    private const val EXPOSURE_W = 0.2f
    private const val EDGE_PENALTY = 0.5f

    fun score(
        s: QualitySignals,
        w: QualityWeights = QualityWeights(),
    ): Float {
        if (s.widthPx < w.minPlateWidthPx) return 0f
        val size =
            ((s.widthPx - w.minPlateWidthPx).toFloat() / (w.goodPlateWidthPx - w.minPlateWidthPx)).coerceIn(
                0f,
                1f,
            )
        val sharp = (s.sharpness / w.goodSharpness).toFloat().coerceIn(0f, 1f)
        val exposure = (1.0 - s.clippedFraction / w.maxClippedFraction).toFloat().coerceIn(0f, 1f)
        val base = SIZE_W * size + SHARP_W * sharp + EXPOSURE_W * exposure
        return if (s.touchesEdge) base * EDGE_PENALTY else base
    }
}
