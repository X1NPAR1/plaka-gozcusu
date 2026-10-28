package com.plakagozcusu.vision.models

import android.graphics.Bitmap
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
        val bitmap =
            Bitmap.createBitmap(
                vehicleCrop.argb,
                vehicleCrop.width,
                vehicleCrop.height,
                Bitmap.Config.ARGB_8888,
            )
        val text =
            try {
                suspendCancellableCoroutine { cont ->
                    recognizer
                        .process(InputImage.fromBitmap(bitmap, 0))
                        .addOnSuccessListener { cont.resume(it) }
                        .addOnFailureListener { cont.resumeWithException(it) }
                }
            } finally {
                bitmap.recycle()
            }
        return text.textBlocks.flatMap { it.lines }.mapNotNull { it.toCandidate(vehicleCrop) }
    }

    private fun Text.Line.toCandidate(crop: ImageBuffer): PlateCandidate? {
        val r = boundingBox ?: return null
        val symbols = elements.flatMap { it.symbols }
        return PlateCandidate(
            rawText = text,
            box =
                RectF(
                    r.left.toFloat() / crop.width,
                    r.top.toFloat() / crop.height,
                    r.right.toFloat() / crop.width,
                    r.bottom.toFloat() / crop.height,
                ),
            confidence = confidence,
            perCharConfidence = symbols.map { it.confidence }.takeIf { it.isNotEmpty() },
        )
    }

    override fun close() = recognizer.close()
}
