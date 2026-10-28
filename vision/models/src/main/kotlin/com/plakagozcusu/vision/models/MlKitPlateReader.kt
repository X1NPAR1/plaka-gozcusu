package com.plakagozcusu.vision.models

import android.graphics.Bitmap
import androidx.core.graphics.scale
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.plakagozcusu.vision.api.ImageBuffer
import com.plakagozcusu.vision.api.PlateCandidate
import com.plakagozcusu.vision.api.PlateReader
import com.plakagozcusu.vision.api.geometry.RectF
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Baseline plate reader: ML Kit Text Recognition (bundled Latin model, on-device) over the vehicle
 * crop. Each recognised text line is a plate *candidate*; the pipeline's Turkish plate parser and
 * multi-frame consensus decide what is plausible. No dedicated plate detector with a clear licence
 * was found (docs/MODEL_CARD.md), so the line box doubles as the plate box.
 */
class MlKitPlateReader : PlateReader {
    override val modelVersion: String = "mlkit-text-recognition-latin-16.0.1"

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    override suspend fun read(vehicleCrop: ImageBuffer): List<PlateCandidate> {
        val source = Bitmap.createBitmap(vehicleCrop.argb, vehicleCrop.width, vehicleCrop.height, Bitmap.Config.ARGB_8888)
        try {
            // Pass 1: whole vehicle crop. Distant vehicles give small crops whose plate characters are
            // only a few pixels tall; ML Kit reads markedly better after a smooth upscale.
            val factor = upscaleFactor(vehicleCrop.width)
            val first =
                recognize(
                    if (factor >
                        1
                    ) {
                        source.scale(vehicleCrop.width * factor, vehicleCrop.height * factor)
                    } else {
                        source.copy(Bitmap.Config.ARGB_8888, false)
                    },
                )
            val firstPass = first.textBlocks.flatMap { it.lines }.mapNotNull { it.toCandidate(vehicleCrop, factor) }
            // Pass 2: re-read each plate-like line from a tight, enhanced, enlarged crop. Most D/O/0, F/E,
            // T/L confusions come from characters only ~10 px tall in pass 1.
            val secondPass = firstPass.filter { it.looksLikePlate() }.take(MAX_SECOND_PASS).mapNotNull { secondPass(source, it) }
            return secondPass + firstPass
        } finally {
            source.recycle()
        }
    }

    /** Runs ML Kit on [bitmap] and recycles it when ML Kit is done (also if we were cancelled). */
    private suspend fun recognize(bitmap: Bitmap): Text =
        suspendCancellableCoroutine { cont ->
            recognizer
                .process(InputImage.fromBitmap(bitmap, 0))
                .addOnCompleteListener { bitmap.recycle() }
                .addOnSuccessListener { if (cont.isActive) cont.resume(it) }
                .addOnFailureListener { if (cont.isActive) cont.resumeWithException(it) }
        }

    private suspend fun secondPass(
        source: Bitmap,
        c: PlateCandidate,
    ): PlateCandidate? {
        val padX = c.box.width * MARGIN
        val padY = c.box.height * MARGIN
        val l = ((c.box.left - padX).coerceAtLeast(0f) * source.width).toInt()
        val t = ((c.box.top - padY).coerceAtLeast(0f) * source.height).toInt()
        val r = ((c.box.right + padX).coerceAtMost(1f) * source.width).toInt()
        val b = ((c.box.bottom + padY).coerceAtMost(1f) * source.height).toInt()
        if (r - l < MIN_SIDE || b - t < MIN_SIDE) return null
        val crop = Bitmap.createBitmap(source, l, t, r - l, b - t)
        val scale = (TARGET_HEIGHT_PX.toFloat() / crop.height).coerceIn(1f, MAX_PLATE_UPSCALE)
        val enlarged = crop.scale((crop.width * scale).toInt(), (crop.height * scale).toInt())
        if (enlarged !== crop) crop.recycle()
        val text = recognize(PlateEnhancer.enhance(enlarged).also { if (it !== enlarged) enlarged.recycle() })
        val joined =
            text.textBlocks
                .flatMap { it.lines }
                .joinToString(" ") { it.text }
                .trim()
        val confidence =
            text.textBlocks
                .flatMap { it.lines }
                .map { it.confidence }
                .average()
                .toFloat()
        val symbols =
            text.textBlocks
                .flatMap { it.lines }
                .flatMap { it.elements }
                .flatMap { it.symbols }
                .map { it.confidence }
        return if (joined.isEmpty()) {
            null
        } else {
            // Same plate box as pass 1 (the crop region), so quality scoring and evidence crops line up.
            PlateCandidate(joined, c.box, if (confidence.isNaN()) c.confidence else confidence, symbols.takeIf { it.isNotEmpty() })
        }
    }

    private fun PlateCandidate.looksLikePlate(): Boolean = rawText.count { it.isLetterOrDigit() } >= MIN_PLATE_CHARS

    private fun Text.Line.toCandidate(
        crop: ImageBuffer,
        scale: Int,
    ): PlateCandidate? {
        val r = boundingBox ?: return null
        val symbols = elements.flatMap { it.symbols }
        return PlateCandidate(
            rawText = text,
            box =
                RectF(
                    r.left.toFloat() / (crop.width * scale),
                    r.top.toFloat() / (crop.height * scale),
                    r.right.toFloat() / (crop.width * scale),
                    r.bottom.toFloat() / (crop.height * scale),
                ),
            confidence = confidence,
            perCharConfidence = symbols.map { it.confidence }.takeIf { it.isNotEmpty() },
        )
    }

    override fun close() = recognizer.close()

    private fun upscaleFactor(width: Int): Int =
        when {
            width < SMALL_CROP_PX -> SMALL_UPSCALE
            width < MEDIUM_CROP_PX -> MEDIUM_UPSCALE
            else -> 1
        }

    private companion object {
        const val SMALL_CROP_PX = 320
        const val MAX_SECOND_PASS = 2
        const val MARGIN = 0.15f
        const val MIN_SIDE = 8
        const val TARGET_HEIGHT_PX = 160
        const val MAX_PLATE_UPSCALE = 6f
        const val MIN_PLATE_CHARS = 5
        const val MEDIUM_CROP_PX = 720
        const val SMALL_UPSCALE = 3
        const val MEDIUM_UPSCALE = 2
    }
}
