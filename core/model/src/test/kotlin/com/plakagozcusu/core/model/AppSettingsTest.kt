package com.plakagozcusu.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AppSettingsTest {
    @Test
    fun `out of range values are clamped`() {
        val s =
            AppSettings(
                retentionDays = 0,
                storageCapMb = 1,
                minDetectionConfidence = 3f,
                maxStillsPerTrack = 99,
            ).validated()

        assertThat(s.retentionDays).isEqualTo(1)
        assertThat(s.storageCapMb).isEqualTo(256)
        assertThat(s.minDetectionConfidence).isEqualTo(0.99f)
        assertThat(s.maxStillsPerTrack).isEqualTo(5)
    }

    @Test
    fun `roi encoding round-trips and drops corrupt or out-of-range parts`() {
        val points = listOf(0.1f to 0.2f, 0.9f to 0.8f)

        assertThat(Calibration.decodeRoi(Calibration.encodeRoi(points))).isEqualTo(points)
        assertThat(Calibration.decodeRoi("0.1,0.2;garbage;2,0.5;0.3")).containsExactly(0.1f to 0.2f)
        assertThat(Calibration.decodeRoi(null)).isEmpty()
    }
}
