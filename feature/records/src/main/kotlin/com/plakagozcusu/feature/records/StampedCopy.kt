package com.plakagozcusu.feature.records

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Derivative export copy with the capture date/time burned in at the bottom-right. The original
 * evidence file (whose SHA-256 is recorded) is never modified; this copy is labelled as derived.
 */
internal object StampedCopy {
    private const val TEXT_SIZE_RATIO = 0.032f
    private const val PAD_RATIO = 0.35f
    private const val BG_ALPHA = 150
    private const val JPEG_QUALITY = 92

    fun render(
        file: File,
        text: String,
    ): ByteArray? {
        val src = BitmapFactory.decodeFile(file.path) ?: return null
        val bmp = src.copy(Bitmap.Config.ARGB_8888, true)
        src.recycle()
        val canvas = Canvas(bmp)
        val size = bmp.width * TEXT_SIZE_RATIO
        val paint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = size
                typeface = Typeface.MONOSPACE
            }
        val pad = size * PAD_RATIO
        val w = paint.measureText(text)
        val right = bmp.width - pad
        val bottom = bmp.height - pad
        val box = RectF(right - w - 2 * pad, bottom - size - 2 * pad, right, bottom)
        canvas.drawRect(box, Paint().apply { color = Color.argb(BG_ALPHA, 0, 0, 0) })
        canvas.drawText(text, box.left + pad, box.bottom - pad - paint.descent(), paint)
        return ByteArrayOutputStream().use { out ->
            bmp.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            bmp.recycle()
            out.toByteArray()
        }
    }
}
