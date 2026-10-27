package com.plakagozcusu.core.camera

import androidx.camera.core.ImageProxy
import com.google.common.truth.Truth.assertThat
import com.plakagozcusu.core.common.AppLogger
import io.mockk.mockk
import io.mockk.verify
import org.junit.Test

class ClosingAnalyzerTest {
    private val logger = mockk<AppLogger>(relaxed = true)

    @Test
    fun `frame is closed after successful analysis`() {
        val frame = mockk<ImageProxy>(relaxed = true)

        ClosingAnalyzer({ }, logger).analyze(frame)

        verify(exactly = 1) { frame.close() }
    }

    @Test
    fun `frame is closed and error logged when the delegate throws`() {
        val frame = mockk<ImageProxy>(relaxed = true)

        ClosingAnalyzer({ error("boom") }, logger).analyze(frame)

        verify(exactly = 1) { frame.close() }
        verify { logger.error("analysis", "frame_analyzer_failed", any()) }
    }

    @Test
    fun `stream keeps flowing after a failing frame`() {
        var analyzed = 0
        val analyzer =
            ClosingAnalyzer({
                analyzed++
                if (analyzed == 1) error("first frame bad")
            }, logger)

        repeat(3) { analyzer.analyze(mockk(relaxed = true)) }

        assertThat(analyzed).isEqualTo(3)
    }
}
