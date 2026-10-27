package com.plakagozcusu.vision.api.capture

import com.google.common.truth.Truth.assertThat
import com.plakagozcusu.vision.api.capture.CaptureRateLimiter.Decision
import org.junit.Test

class CaptureRateLimiterTest {
    private val limiter =
        CaptureRateLimiter(
            CaptureRateLimiter.Policy(globalCooldownMs = 500, maxCapturesPerTrack = 2, maxCapturesPerMinute = 3),
        )

    @Test
    fun `cooldown blocks a second capture too soon`() {
        limiter.record(trackId = 1, nowMs = 0)

        assertThat(limiter.check(trackId = 2, nowMs = 499)).isEqualTo(Decision.COOLDOWN)
        assertThat(limiter.check(trackId = 2, nowMs = 500)).isEqualTo(Decision.ALLOWED)
    }

    @Test
    fun `per-track cap stops capturing the same vehicle forever`() {
        limiter.record(1, 0)
        limiter.record(1, 1_000)

        assertThat(limiter.check(1, 2_000)).isEqualTo(Decision.TRACK_LIMIT)
        assertThat(limiter.check(2, 2_000)).isEqualTo(Decision.ALLOWED)
    }

    @Test
    fun `minute budget is rolling`() {
        limiter.record(1, 0)
        limiter.record(2, 1_000)
        limiter.record(3, 2_000)

        assertThat(limiter.check(4, 3_000)).isEqualTo(Decision.MINUTE_BUDGET)
        assertThat(limiter.check(4, 60_000)).isEqualTo(Decision.ALLOWED)
    }

    @Test
    fun `forgetting a track resets its cap`() {
        limiter.record(1, 0)
        limiter.record(1, 1_000)
        limiter.forgetTrack(1)

        assertThat(limiter.check(1, 61_000)).isEqualTo(Decision.ALLOWED)
    }
}
