package com.plakagozcusu.core.common

/**
 * Logging facade. Callers pass a [stage] and a stable [code] instead of free text so that plate
 * text can never reach logcat by accident (privacy requirement, see docs/SECURITY.md).
 */
interface AppLogger {
    fun info(
        stage: String,
        code: String,
        durationMs: Long? = null,
    )

    fun warn(
        stage: String,
        code: String,
        throwable: Throwable? = null,
    )

    fun error(
        stage: String,
        code: String,
        throwable: Throwable? = null,
    )

    companion object {
        const val TAG = "PlakaGozcusu"
    }
}
