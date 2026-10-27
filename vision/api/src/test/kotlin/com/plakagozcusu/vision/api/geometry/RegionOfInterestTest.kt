package com.plakagozcusu.vision.api.geometry

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class RegionOfInterestTest {
    // Trapezoid road area (perspective: narrower far away at the top).
    private val road =
        RegionOfInterest(
            listOf(PointF(0.3f, 0.4f), PointF(0.7f, 0.4f), PointF(1f, 0.9f), PointF(0f, 0.9f)),
        )

    @Test
    fun `points inside and outside the trapezoid`() {
        assertThat(road.contains(PointF(0.5f, 0.6f))).isTrue()
        assertThat(road.contains(PointF(0.1f, 0.45f))).isFalse()
        assertThat(road.contains(PointF(0.5f, 0.95f))).isFalse()
    }

    @Test
    fun `vehicle is in ROI when its ground point is, even if the roof is outside`() {
        val tallVehicle = RectF(0.4f, 0.2f, 0.6f, 0.7f)

        assertThat(road.containsGroundPointOf(tallVehicle)).isTrue()
    }

    @Test
    fun `vehicle parked outside the road area is ignored`() {
        val parkedOnDriveway = RectF(0.02f, 0.3f, 0.2f, 0.42f)

        assertThat(road.containsGroundPointOf(parkedOnDriveway)).isFalse()
    }

    @Test
    fun `full frame contains interior points`() {
        assertThat(RegionOfInterest.FULL_FRAME.contains(PointF(0.5f, 0.5f))).isTrue()
    }

    @Test(expected = IllegalArgumentException::class)
    fun `denormalized points are rejected`() {
        RegionOfInterest(listOf(PointF(0f, 0f), PointF(2f, 0f), PointF(0f, 1f)))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `fewer than three points is rejected`() {
        RegionOfInterest(listOf(PointF(0f, 0f), PointF(1f, 1f)))
    }
}
