package com.plakagozcusu.feature.monitor.session

import com.plakagozcusu.core.model.AnalysisLoad

/**
 * Decides whether an incoming analysis frame is processed, so detector load stays stable and
 * adapts to heat. Rates are starting points to be replaced by measured defaults (docs/PERFORMANCE.md).
 */
class FrameScheduler(
    private val load: AnalysisLoad,
) {
    private var lastAcceptedMs: Long? = null
    var thermal: ThermalLevel = ThermalLevel.NORMAL

    val targetFps: Float
        get() = baseFps(load) * thermalFactor(thermal)

    fun accept(nowMs: Long): Boolean {
        val fps = targetFps
        if (fps <= 0f) return false
        val last = lastAcceptedMs
        val interval = (MS_PER_S / fps).toLong()
        if (last != null && nowMs - last < interval) return false
        lastAcceptedMs = nowMs
        return true
    }

    companion object {
        private const val MS_PER_S = 1_000f
        private const val LOW_FPS = 2f
        private const val BALANCED_FPS = 4f
        private const val HIGH_FPS = 8f
        private const val MODERATE_FACTOR = 0.75f
        private const val SEVERE_FACTOR = 0.5f

        fun baseFps(load: AnalysisLoad): Float =
            when (load) {
                AnalysisLoad.LOW -> LOW_FPS
                AnalysisLoad.BALANCED -> BALANCED_FPS
                AnalysisLoad.HIGH -> HIGH_FPS
            }

        fun thermalFactor(level: ThermalLevel): Float =
            when (level) {
                ThermalLevel.NORMAL -> 1f
                ThermalLevel.MODERATE -> MODERATE_FACTOR
                ThermalLevel.SEVERE -> SEVERE_FACTOR
                ThermalLevel.CRITICAL -> 0f
            }
    }
}

/**
 * Detects a stalled pipeline: no processed frame for [timeoutMs] while running → the service
 * rebinds the camera. Pure so it can be tested with an injected clock.
 */
class StallWatchdog(
    private val timeoutMs: Long,
) {
    private var lastProgressMs: Long? = null

    fun progress(nowMs: Long) {
        lastProgressMs = nowMs
    }

    fun reset() {
        lastProgressMs = null
    }

    fun isStalled(nowMs: Long): Boolean = lastProgressMs?.let { nowMs - it > timeoutMs } ?: false
}
