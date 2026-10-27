package com.plakagozcusu.vision.api.geometry

/**
 * Maps coordinates between a raw camera buffer ("buffer space", as delivered by ImageAnalysis or
 * ImageCapture, before rotation) and the upright, normalized [0,1] "scene space" every stage of the
 * pipeline and the UI share.
 *
 * Scene space is resolution-independent and upright, so a box found in a 640x480 analysis frame
 * can be located in a 4000x3000 still of the same moment (same field of view assumed: both use cases
 * are bound with the same aspect ratio and the stream's crop rect is applied first).
 *
 * @param bufferSize full buffer size as delivered.
 * @param cropRect the valid region inside the buffer (CameraX `ImageProxy.cropRect`), buffer pixels.
 * @param rotationDegrees clockwise rotation needed to make the buffer upright (`ImageInfo.rotationDegrees`).
 */
class FrameTransform(
    val bufferSize: Size,
    val cropRect: RectF = RectF(0f, 0f, bufferSize.width.toFloat(), bufferSize.height.toFloat()),
    rotationDegrees: Int,
) {
    val rotation: Int = normalizeRotation(rotationDegrees)

    /** Size of the cropped, upright image in pixels. */
    val uprightSize: Size = Size(cropRect.width.toInt(), cropRect.height.toInt()).rotated(rotation)

    init {
        require(cropRect.width > 0f && cropRect.height > 0f) { "Empty crop rect: $cropRect" }
    }

    /** Buffer pixel -> normalized upright scene coordinate. */
    fun bufferToScene(p: PointF): PointF {
        val u = (p.x - cropRect.left) / cropRect.width
        val v = (p.y - cropRect.top) / cropRect.height
        return when (rotation) {
            0 -> PointF(u, v)
            QUARTER_TURN -> PointF(1f - v, u)
            HALF_TURN -> PointF(1f - u, 1f - v)
            else -> PointF(v, 1f - u)
        }
    }

    /** Normalized upright scene coordinate -> buffer pixel (inverse of [bufferToScene]). */
    fun sceneToBuffer(p: PointF): PointF {
        val (u, v) =
            when (rotation) {
                0 -> p.x to p.y
                QUARTER_TURN -> p.y to 1f - p.x
                HALF_TURN -> 1f - p.x to 1f - p.y
                else -> 1f - p.y to p.x
            }
        return PointF(cropRect.left + u * cropRect.width, cropRect.top + v * cropRect.height)
    }

    fun bufferToScene(r: RectF): RectF =
        boundsOf(
            bufferToScene(PointF(r.left, r.top)),
            bufferToScene(PointF(r.right, r.bottom)),
        )

    fun sceneToBuffer(r: RectF): RectF =
        boundsOf(
            sceneToBuffer(PointF(r.left, r.top)),
            sceneToBuffer(PointF(r.right, r.bottom)),
        )

    /** Normalized scene rect -> pixel rect in the upright image (e.g. for cropping a rotated still). */
    fun sceneToUprightPixels(r: RectF): RectF =
        RectF(
            r.left * uprightSize.width,
            r.top * uprightSize.height,
            r.right * uprightSize.width,
            r.bottom * uprightSize.height,
        )

    private fun boundsOf(
        a: PointF,
        b: PointF,
    ) = RectF(
        minOf(a.x, b.x),
        minOf(a.y, b.y),
        maxOf(a.x, b.x),
        maxOf(a.y, b.y),
    )
}
