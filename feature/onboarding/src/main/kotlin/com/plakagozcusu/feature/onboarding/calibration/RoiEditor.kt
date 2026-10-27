package com.plakagozcusu.feature.onboarding.calibration

import com.plakagozcusu.vision.api.geometry.PointF
import com.plakagozcusu.vision.api.geometry.RegionOfInterest

/**
 * Editing state for the user-drawn road polygon. Taps add vertices (clamped to the frame), "undo"
 * removes the last one. The polygon becomes a valid [RegionOfInterest] at 3+ points.
 */
data class RoiEditor(
    val points: List<PointF> = emptyList(),
) {
    val canUndo: Boolean get() = points.isNotEmpty()
    val canAdd: Boolean get() = points.size < MAX_POINTS
    val isComplete: Boolean get() = points.size >= RegionOfInterest.MIN_POINTS

    fun add(p: PointF): RoiEditor =
        if (!canAdd) this else copy(points = points + PointF(p.x.coerceIn(0f, 1f), p.y.coerceIn(0f, 1f)))

    fun undo(): RoiEditor = if (canUndo) copy(points = points.dropLast(1)) else this

    fun clear(): RoiEditor = RoiEditor()

    fun toRegionOrNull(): RegionOfInterest? = if (isComplete) RegionOfInterest(points) else null

    companion object {
        /** Enough for any road shape; more points only make the polygon harder to edit on a phone. */
        const val MAX_POINTS = 8
    }
}
