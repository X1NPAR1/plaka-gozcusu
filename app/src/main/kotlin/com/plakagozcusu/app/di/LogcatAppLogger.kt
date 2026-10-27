package com.plakagozcusu.app.di

import android.util.Log
import com.plakagozcusu.core.common.AppLogger
import com.plakagozcusu.core.common.AppLogger.Companion.TAG
import javax.inject.Inject

/**
 * Logcat sink for the stage/code logging facade. Only stable codes are logged (never plate text);
 * persistent diagnostic events are added in Phase 7.
 */
class LogcatAppLogger
    @Inject
    constructor() : AppLogger {
        override fun info(
            stage: String,
            code: String,
            durationMs: Long?,
        ) {
            Log.i(TAG, format(stage, code, durationMs))
        }

        override fun warn(
            stage: String,
            code: String,
            throwable: Throwable?,
        ) {
            Log.w(TAG, format(stage, code, null), throwable)
        }

        override fun error(
            stage: String,
            code: String,
            throwable: Throwable?,
        ) {
            Log.e(TAG, format(stage, code, null), throwable)
        }

        private fun format(
            stage: String,
            code: String,
            durationMs: Long?,
        ) = if (durationMs == null) "[$stage] $code" else "[$stage] $code ${durationMs}ms"
    }
