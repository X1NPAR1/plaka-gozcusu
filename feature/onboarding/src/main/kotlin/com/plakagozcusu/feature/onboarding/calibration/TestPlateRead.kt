package com.plakagozcusu.feature.onboarding.calibration

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import com.plakagozcusu.vision.api.ImageBuffer
import com.plakagozcusu.vision.api.PlateCandidate
import com.plakagozcusu.vision.pipeline.plate.TurkishPlateParser

/** What the test capture read: real plate width measured in the still, never an estimate. */
data class TestReadResult(
    val plate: String?,
    val rawTexts: List<String>,
    /** Width of the best plate-like text box in ORIGINAL still pixels. */
    val plateWidthPx: Int?,
)

internal object TestPlateRead {
    /** Long-edge cap for OCR on the still: keeps memory sane while preserving plate detail. */
    private const val MAX_EDGE = 2400

    fun decodeUpright(
        jpeg: ByteArray,
        rotationDegrees: Int,
    ): Pair<ImageBuffer, Float> {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_EDGE) sample *= 2
        val raw = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, BitmapFactory.Options().apply { inSampleSize = sample })
        val upright =
            if (rotationDegrees == 0) {
                raw
            } else {
                Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, Matrix().apply { postRotate(rotationDegrees.toFloat()) }, true)
            }
        val px = IntArray(upright.width * upright.height)
        upright.getPixels(px, 0, upright.width, 0, 0, upright.width, upright.height)
        val buffer = ImageBuffer(upright.width, upright.height, px)
        if (upright !== raw) upright.recycle()
        raw.recycle()
        return buffer to sample.toFloat()
    }

    fun summarize(
        candidates: List<PlateCandidate>,
        image: ImageBuffer,
        sampleScale: Float,
    ): TestReadResult {
        val parsed = candidates.map { it to TurkishPlateParser.parse(it.rawText) }
        val best = parsed.filter { it.second.isValidCivilian }.maxByOrNull { it.first.confidence }
        val widthOf = { c: PlateCandidate -> (c.box.width * image.width * sampleScale).toInt() }
        return TestReadResult(
            plate = best?.second?.formatted,
            rawTexts = candidates.map { it.rawText },
            plateWidthPx = (best?.first ?: candidates.maxByOrNull { it.box.width })?.let(widthOf),
        )
    }
}
