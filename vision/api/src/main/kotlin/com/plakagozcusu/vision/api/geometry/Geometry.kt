package com.plakagozcusu.vision.api.geometry

/** Integer size of an image buffer in pixels. */
data class Size(
    val width: Int,
    val height: Int,
) {
    init {
        require(width > 0 && height > 0) { "Size must be positive: ${width}x$height" }
    }

    fun rotated(rotationDegrees: Int): Size = if (normalizeRotation(rotationDegrees) % HALF_TURN == 0) this else Size(height, width)
}

data class PointF(
    val x: Float,
    val y: Float,
)

/** Axis-aligned rectangle in pixel (or any linear) units; left/top inclusive, right/bottom exclusive. */
data class RectF(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val area: Float get() = if (width <= 0f || height <= 0f) 0f else width * height
    val center: PointF get() = PointF((left + right) / 2f, (top + bottom) / 2f)

    fun intersect(other: RectF): RectF =
        RectF(
            maxOf(left, other.left),
            maxOf(top, other.top),
            minOf(right, other.right),
            minOf(bottom, other.bottom),
        )

    fun iou(other: RectF): Float {
        val inter = intersect(other).area
        val union = area + other.area - inter
        return if (union <= 0f) 0f else inter / union
    }

    fun clampTo(size: Size): RectF =
        RectF(
            left.coerceIn(0f, size.width.toFloat()),
            top.coerceIn(0f, size.height.toFloat()),
            right.coerceIn(0f, size.width.toFloat()),
            bottom.coerceIn(0f, size.height.toFloat()),
        )

    /** True when the rect touches an image edge, i.e. the object may be cut off. */
    fun touchesEdgeOf(
        size: Size,
        marginPx: Float,
    ): Boolean = left <= marginPx || top <= marginPx || right >= size.width - marginPx || bottom >= size.height - marginPx
}

internal const val FULL_TURN = 360
internal const val QUARTER_TURN = 90
internal const val HALF_TURN = 180
internal const val THREE_QUARTER_TURN = 270

fun normalizeRotation(degrees: Int): Int {
    require(degrees % QUARTER_TURN == 0) { "Rotation must be a multiple of 90: $degrees" }
    return ((degrees % FULL_TURN) + FULL_TURN) % FULL_TURN
}
