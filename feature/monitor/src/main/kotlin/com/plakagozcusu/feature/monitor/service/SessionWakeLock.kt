package com.plakagozcusu.feature.monitor.service

import android.content.Context
import android.os.PowerManager

/**
 * Partial wake lock that keeps analysing with the screen off. It is taken with a timeout and
 * renewed by the session's supervise loop, so a crashed/killed session can never hold the CPU
 * awake for more than [TIMEOUT_MS].
 */
internal class SessionWakeLock(
    context: Context,
) {
    private val lock =
        context
            .getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "PlakaGozcusu:monitoring")
            .apply { setReferenceCounted(false) }

    /** Acquire or extend. */
    fun renew() = lock.acquire(TIMEOUT_MS)

    fun release() {
        if (lock.isHeld) lock.release()
    }

    private companion object {
        const val TIMEOUT_MS = 10 * 60 * 1000L
    }
}
