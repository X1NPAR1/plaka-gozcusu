package com.plakagozcusu.vision.pipeline.tracking

import com.plakagozcusu.core.model.MotionStatus
import com.plakagozcusu.core.model.TravelDirection
import kotlin.math.abs
import kotlin.math.hypot

/**
 * Image-space motion of a track. Speeds are in analysis pixels per second — **not** physical
 * speed. A track is MOVING when its ground point travelled at least [minDisplacementPx] at an
 * average of at least [minSpeedPxPerSec] over at least [minAgeMs].
 */
class MotionClassifier(
    private val frameWidthPx: Int,
    private val frameHeightPx: Int,
    private val minSpeedPxPerSec: Float,
    private val minDisplacementPx: Float = DEFAULT_MIN_DISPLACEMENT,
    private val minAgeMs: Long = DEFAULT_MIN_AGE_MS,
) {
    fun motion(track: Track): MotionStatus {
        val s = track.samples
        val ageMs = s.last().timeMs - s.first().timeMs
        if (ageMs < minAgeMs) return MotionStatus.UNKNOWN
        val (dx, dy) = groundDelta(track)
        val dist = hypot(dx, dy)
        val speed = dist / (ageMs / MS_PER_S)
        return if (dist >= minDisplacementPx &&
            speed >= minSpeedPxPerSec
        ) {
            MotionStatus.MOVING
        } else {
            MotionStatus.STATIONARY
        }
    }

    fun direction(track: Track): TravelDirection {
        val (dx, dy) = groundDelta(track)
        val first = track.samples.first().box
        val last = track.samples.last().box
        val growth = last.height / first.height.coerceAtLeast(MIN_SIZE)
        return when {
            hypot(dx, dy) < minDisplacementPx && abs(growth - 1f) < GROWTH_EPS -> TravelDirection.UNKNOWN
            abs(dx) >= abs(dy) -> if (dx > 0) TravelDirection.LEFT_TO_RIGHT else TravelDirection.RIGHT_TO_LEFT
            growth > 1f -> TravelDirection.TOWARD_CAMERA
            else -> TravelDirection.AWAY_FROM_CAMERA
        }
    }

    /** Displacement of the bottom-centre (ground contact point) between first and last sample. */
    private fun groundDelta(track: Track): Pair<Float, Float> {
        val a = track.samples.first().box
        val b = track.samples.last().box
        val dx = (b.center.x - a.center.x) * frameWidthPx
        val dy = (b.bottom - a.bottom) * frameHeightPx
        return dx to dy
    }

    private companion object {
        const val DEFAULT_MIN_DISPLACEMENT = 25f
        const val DEFAULT_MIN_AGE_MS = 400L
        const val MS_PER_S = 1_000f
        const val MIN_SIZE = 1e-4f
        const val GROWTH_EPS = 0.15f
    }
}
