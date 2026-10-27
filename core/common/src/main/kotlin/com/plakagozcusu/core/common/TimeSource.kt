package com.plakagozcusu.core.common

import java.time.Instant
import java.time.ZoneId

/**
 * Wall-clock + monotonic time behind one interface so the pipeline and replay tests can inject a
 * deterministic clock. Wall time is always stored as UTC epoch millis plus the zone id at capture.
 */
interface TimeSource {
    fun now(): Instant

    fun zone(): ZoneId

    /** Monotonic nanoseconds for durations/timeouts; unaffected by user clock or time-zone changes. */
    fun monotonicNanos(): Long
}

object SystemTimeSource : TimeSource {
    override fun now(): Instant = Instant.now()

    override fun zone(): ZoneId = ZoneId.systemDefault()

    override fun monotonicNanos(): Long = System.nanoTime()
}
