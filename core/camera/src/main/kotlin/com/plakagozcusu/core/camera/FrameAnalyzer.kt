package com.plakagozcusu.core.camera

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.plakagozcusu.core.common.AppLogger

/** Consumer of analysis frames. Implementations must not keep a reference to [frame] after returning. */
fun interface FrameAnalyzer {
    fun analyze(frame: ImageProxy)
}

/**
 * Adapter that guarantees every [ImageProxy] is closed on every path, including exceptions.
 * An unclosed ImageProxy stalls CameraX analysis permanently (KEEP_ONLY_LATEST stops delivering),
 * which would silently stop monitoring, so this is enforced in one place and unit-tested.
 */
class ClosingAnalyzer(
    private val delegate: FrameAnalyzer,
    private val logger: AppLogger,
) : ImageAnalysis.Analyzer {
    override fun analyze(image: ImageProxy) {
        try {
            delegate.analyze(image)
        } catch (
            @Suppress("TooGenericExceptionCaught") e: RuntimeException,
        ) {
            // One bad frame must not kill the analysis stream; it is logged, not swallowed.
            logger.error(stage = "analysis", code = "frame_analyzer_failed", throwable = e)
        } finally {
            image.close()
        }
    }
}
