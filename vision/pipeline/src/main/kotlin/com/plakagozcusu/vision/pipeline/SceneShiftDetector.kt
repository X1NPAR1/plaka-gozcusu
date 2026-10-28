package com.plakagozcusu.vision.pipeline

import com.plakagozcusu.vision.api.ImageBuffer
import kotlin.math.abs

/**
 * Detects a sudden whole-frame change (phone bumped / moved) by comparing a coarse luminance grid
 * of consecutive frames. Vehicles change a few cells; a camera jolt changes most of them. When
 * triggered, the pipeline pauses event generation instead of producing false observations.
 */
class SceneShiftDetector(
    private val cellDiffThreshold: Int = DEFAULT_CELL_DIFF,
    private val changedFractionThreshold: Float = DEFAULT_CHANGED_FRACTION,
) {
    private var previous: IntArray? = null

    fun isShift(frame: ImageBuffer): Boolean {
        val grid = grid(frame)
        val prev = previous
        previous = grid
        if (prev == null) return false
        val changed = grid.indices.count { abs(grid[it] - prev[it]) > cellDiffThreshold }
        return changed.toFloat() / grid.size >= changedFractionThreshold
    }

    fun reset() {
        previous = null
    }

    private fun grid(f: ImageBuffer): IntArray {
        val out = IntArray(COLS * ROWS)
        for (gy in 0 until ROWS) {
            for (gx in 0 until COLS) {
                val x = (gx * HALF_STEPS + 1) * f.width / (COLS * HALF_STEPS)
                val y = (gy * HALF_STEPS + 1) * f.height / (ROWS * HALF_STEPS)
                val p = f.argb[y * f.width + x]
                out[gy * COLS + gx] =
                    ((p shr RED_SHIFT and BYTE) + (p shr GREEN_SHIFT and BYTE) + (p and BYTE)) / CHANNELS
            }
        }
        return out
    }

    private companion object {
        const val COLS = 16
        const val ROWS = 12
        const val BYTE = 0xFF
        const val RED_SHIFT = 16
        const val GREEN_SHIFT = 8

        /** Sample each cell at its centre: (2k+1)/2N. */
        const val HALF_STEPS = 2
        const val CHANNELS = 3
        const val DEFAULT_CELL_DIFF = 40
        const val DEFAULT_CHANGED_FRACTION = 0.6f
    }
}
