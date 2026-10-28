package com.plakagozcusu.vision.api

import com.plakagozcusu.core.model.VehicleClass
import com.plakagozcusu.vision.api.geometry.RectF

/**
 * Upright RGB image in plain JVM memory (ARGB_8888 ints), so pipeline logic and tests need no
 * Android types. Producers reuse buffers where possible; consumers must not keep a reference.
 */
class ImageBuffer(
    val width: Int,
    val height: Int,
    val argb: IntArray,
) {
    init {
        require(width > 0 && height > 0 && argb.size == width * height) { "Bad image buffer ${width}x$height" }
    }

    /** Copies the [box] region (pixels, clamped) into a new buffer. */
    fun crop(box: RectF): ImageBuffer {
        val l = box.left.toInt().coerceIn(0, width - 1)
        val t = box.top.toInt().coerceIn(0, height - 1)
        val r = box.right.toInt().coerceIn(l + 1, width)
        val b = box.bottom.toInt().coerceIn(t + 1, height)
        val w = r - l
        val h = b - t
        val out = IntArray(w * h)
        for (y in 0 until h) System.arraycopy(argb, (t + y) * width + l, out, y * w, w)
        return ImageBuffer(w, h, out)
    }
}

/** A vehicle detection; [box] in normalized upright scene coordinates. */
data class VehicleDetection(
    val box: RectF,
    val vehicleClass: VehicleClass,
    val score: Float,
)

/** Raw OCR result for one candidate plate region; [box] normalized within the analysed crop. */
data class PlateCandidate(
    val rawText: String,
    val box: RectF,
    val confidence: Float,
    val perCharConfidence: List<Float>?,
)

interface VehicleDetector : AutoCloseable {
    val modelVersion: String

    fun detect(image: ImageBuffer): List<VehicleDetection>
}

/** Finds and reads plate-like text in a vehicle crop (plate detection + OCR behind one boundary). */
interface PlateReader : AutoCloseable {
    val modelVersion: String

    suspend fun read(vehicleCrop: ImageBuffer): List<PlateCandidate>
}
