package com.plakagozcusu.vision.api.capture

/**
 * Decides whether a high-resolution still may be taken now. Stills are expensive (shutter lag,
 * memory, heat, storage), so the policy enforces a global cooldown, a per-track cap and a rolling
 * per-minute budget. Time is passed in (monotonic millis) to keep it deterministic in tests.
 */
class CaptureRateLimiter(
    private val policy: Policy,
) {
    data class Policy(
        val globalCooldownMs: Long,
        val maxCapturesPerTrack: Int,
        val maxCapturesPerMinute: Int,
    ) {
        init {
            require(globalCooldownMs >= 0 && maxCapturesPerTrack > 0 && maxCapturesPerMinute > 0)
        }
    }

    enum class Decision { ALLOWED, COOLDOWN, TRACK_LIMIT, MINUTE_BUDGET }

    private val perTrack = HashMap<Long, Int>()
    private val recent = ArrayDeque<Long>()
    private var lastCaptureAt: Long? = null

    fun check(
        trackId: Long,
        nowMs: Long,
    ): Decision {
        evictOlderThanAMinute(nowMs)
        val last = lastCaptureAt
        return when {
            last != null && nowMs - last < policy.globalCooldownMs -> Decision.COOLDOWN
            (perTrack[trackId] ?: 0) >= policy.maxCapturesPerTrack -> Decision.TRACK_LIMIT
            recent.size >= policy.maxCapturesPerMinute -> Decision.MINUTE_BUDGET
            else -> Decision.ALLOWED
        }
    }

    /** Call only after a capture was actually requested. */
    fun record(
        trackId: Long,
        nowMs: Long,
    ) {
        lastCaptureAt = nowMs
        perTrack[trackId] = (perTrack[trackId] ?: 0) + 1
        recent.addLast(nowMs)
    }

    /** Free per-track bookkeeping once a track is finalized, keeping memory bounded. */
    fun forgetTrack(trackId: Long) {
        perTrack.remove(trackId)
    }

    private fun evictOlderThanAMinute(nowMs: Long) {
        while (recent.isNotEmpty() && nowMs - recent.first() >= MINUTE_MS) recent.removeFirst()
    }

    private companion object {
        const val MINUTE_MS = 60_000L
    }
}
