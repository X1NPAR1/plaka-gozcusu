package com.plakagozcusu.vision.models

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import androidx.core.graphics.createBitmap

/**
 * Prepares a tight plate crop for OCR: greyscale + contrast stretch around mid-grey. Turkish plates
 * are black on white, so this separates thin strokes (D vs O, F vs E) from glare and dirt.
 */
internal object PlateEnhancer {
    private const val CONTRAST = 1.8f
    private const val MID = 128f

    fun enhance(src: Bitmap): Bitmap {
        val out = createBitmap(src.width, src.height)
        val grey = ColorMatrix().apply { setSaturation(0f) }
        val offset = MID * (1 - CONTRAST)
        val contrast =
            ColorMatrix(
                floatArrayOf(
                    CONTRAST,
                    0f,
                    0f,
                    0f,
                    offset,
                    0f,
                    CONTRAST,
                    0f,
                    0f,
                    offset,
                    0f,
                    0f,
                    CONTRAST,
                    0f,
                    offset,
                    0f,
                    0f,
                    0f,
                    1f,
                    0f,
                ),
            )
        grey.postConcat(contrast)
        Canvas(out).drawBitmap(src, 0f, 0f, Paint().apply { colorFilter = ColorMatrixColorFilter(grey) })
        return out
    }
}
