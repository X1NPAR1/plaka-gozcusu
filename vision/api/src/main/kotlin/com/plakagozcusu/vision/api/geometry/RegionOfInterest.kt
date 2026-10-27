package com.plakagozcusu.vision.api.geometry

/**
 * User-drawn road area as a polygon in normalized upright scene coordinates. Detections whose
 * reference point (bottom-centre of the box: where the vehicle touches the road) lies outside are
 * ignored, which saves compute and keeps neighbours' private areas out of the pipeline.
 */
data class RegionOfInterest(
    val points: List<PointF>,
) {
    init {
        require(points.size >= MIN_POINTS) { "ROI needs at least $MIN_POINTS points, got ${points.size}" }
        require(points.all { it.x in 0f..1f && it.y in 0f..1f }) { "ROI points must be normalized" }
    }

    val bounds: RectF
        get() = RectF(points.minOf { it.x }, points.minOf { it.y }, points.maxOf { it.x }, points.maxOf { it.y })

    /** Ray-casting point-in-polygon test; points exactly on an edge may fall either way. */
    fun contains(p: PointF): Boolean {
        var inside = false
        var j = points.lastIndex
        for (i in points.indices) {
            val a = points[i]
            val b = points[j]
            val crosses = (a.y > p.y) != (b.y > p.y) && p.x < (b.x - a.x) * (p.y - a.y) / (b.y - a.y) + a.x
            if (crosses) inside = !inside
            j = i
        }
        return inside
    }

    fun containsGroundPointOf(box: RectF): Boolean = contains(PointF(box.center.x, box.bottom))

    companion object {
        const val MIN_POINTS = 3

        /** Whole frame; used until the user calibrates. */
        val FULL_FRAME = RegionOfInterest(listOf(PointF(0f, 0f), PointF(1f, 0f), PointF(1f, 1f), PointF(0f, 1f)))
    }
}
