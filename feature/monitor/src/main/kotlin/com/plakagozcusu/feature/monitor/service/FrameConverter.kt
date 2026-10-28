package com.plakagozcusu.feature.monitor.service

import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.camera.core.ImageProxy
import com.plakagozcusu.vision.api.ImageBuffer
import com.plakagozcusu.vision.pipeline.ImageEncoder
import java.io.ByteArrayOutputStream

/**
 * YUV analysis frame → upright ARGB [ImageBuffer]. Uses CameraX's built-in YUV→RGB conversion and
 * rotates by the frame's rotation so the pipeline always works in upright scene coordinates.
 * Allocates per frame; buffer reuse is a measured Phase 10 optimization.
 */
internal object FrameConverter {
    fun toUpright(image: ImageProxy): ImageBuffer {
        val raw = image.toBitmap()
        val rotation = image.imageInfo.rotationDegrees
        val upright =
            if (rotation == 0) {
                raw
            } else {
                Bitmap.createBitmap(
                    raw,
                    0,
                    0,
                    raw.width,
                    raw.height,
                    Matrix().apply { postRotate(rotation.toFloat()) },
                    false,
                )
            }
        val pixels = IntArray(upright.width * upright.height)
        upright.getPixels(pixels, 0, upright.width, 0, 0, upright.width, upright.height)
        val buffer = ImageBuffer(upright.width, upright.height, pixels)
        if (upright !== raw) upright.recycle()
        raw.recycle()
        return buffer
    }
}

/** JPEG encoder for derived evidence (crops, analysis-frame fallbacks). Originals stay untouched. */
internal class JpegEncoder(
    private val quality: Int = DEFAULT_QUALITY,
) : ImageEncoder {
    override fun encode(image: ImageBuffer): ByteArray {
        val bmp = Bitmap.createBitmap(image.argb, image.width, image.height, Bitmap.Config.ARGB_8888)
        return try {
            ByteArrayOutputStream().use { out ->
                bmp.compress(Bitmap.CompressFormat.JPEG, quality, out)
                out.toByteArray()
            }
        } finally {
            bmp.recycle()
        }
    }

    private companion object {
        const val DEFAULT_QUALITY = 92
    }
}
