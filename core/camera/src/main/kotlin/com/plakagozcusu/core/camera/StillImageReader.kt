package com.plakagozcusu.core.camera

import android.graphics.ImageFormat
import androidx.camera.core.ImageProxy
import com.plakagozcusu.core.common.AppError
import com.plakagozcusu.core.common.AppResult

/** Copies the encoded JPEG out of a captured [ImageProxy] untouched (originals are never re-encoded). */
internal object StillImageReader {
    fun read(image: ImageProxy): AppResult<StillImage> {
        if (image.format != ImageFormat.JPEG || image.planes.isEmpty()) {
            return AppResult.Failure(
                AppError.Unexpected(IllegalStateException("Unexpected still format ${image.format}")),
            )
        }
        val buffer = image.planes[0].buffer
        buffer.rewind()
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        return AppResult.Success(
            StillImage(
                jpeg = bytes,
                width = image.width,
                height = image.height,
                rotationDegrees = image.imageInfo.rotationDegrees,
                sensorTimestampNanos = image.imageInfo.timestamp,
            ),
        )
    }
}
