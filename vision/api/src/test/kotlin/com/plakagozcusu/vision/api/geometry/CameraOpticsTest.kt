package com.plakagozcusu.vision.api.geometry

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CameraOpticsTest {
    // Representative mid-range main camera: 4.7 mm lens, 6.4 mm wide sensor, 4000 px wide still.
    private val optics = CameraOptics(focalLengthMm = 4.7f, sensorWidthMm = 6.4f, imageWidthPx = 4000)

    @Test
    fun `field of view follows the pinhole model`() {
        // 2 * atan(6.4 / 9.4) = 68.5 degrees
        assertThat(optics.horizontalFovDegrees).isWithin(0.1).of(68.5)
    }

    @Test
    fun `plate pixel width halves when distance doubles`() {
        val at10 = optics.expectedPixelWidth(CameraOptics.TURKISH_PLATE_WIDTH_M, 10.0)
        val at20 = optics.expectedPixelWidth(CameraOptics.TURKISH_PLATE_WIDTH_M, 20.0)

        // 4.7 * 0.52 / 10 = 0.2444 mm on sensor * (4000 / 6.4) px/mm = 152.75 px
        assertThat(at10).isWithin(0.01).of(152.75)
        assertThat(at20).isWithin(0.01).of(at10 / 2)
    }

    @Test
    fun `zoom scales the estimate linearly`() {
        val base = optics.expectedPixelWidth(0.52, 30.0)

        assertThat(optics.expectedPixelWidth(0.52, 30.0, zoomRatio = 2f)).isWithin(1e-6).of(base * 2)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `zero distance is rejected`() {
        optics.expectedPixelWidth(0.52, 0.0)
    }
}
