package com.plakagozcusu.vision.pipeline

import com.google.common.truth.Truth.assertThat
import com.plakagozcusu.core.model.RecognitionStatus
import com.plakagozcusu.core.model.TravelDirection
import com.plakagozcusu.core.model.VehicleClass
import com.plakagozcusu.vision.api.ImageBuffer
import com.plakagozcusu.vision.api.PlateCandidate
import com.plakagozcusu.vision.api.PlateReader
import com.plakagozcusu.vision.api.VehicleDetection
import com.plakagozcusu.vision.api.VehicleDetector
import com.plakagozcusu.vision.api.capture.CaptureRateLimiter
import com.plakagozcusu.vision.api.geometry.PointF
import com.plakagozcusu.vision.api.geometry.RectF
import com.plakagozcusu.vision.api.geometry.RegionOfInterest
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * Replay harness: scripted detector/reader outputs over a synthetic frame sequence with an injected
 * clock. Exercises tracking, motion gating, ROI, OCR consensus, best-frame choice, finalization and
 * scene-shift pausing end to end, deterministically and without a camera.
 */
class PipelineReplayTest {
    private val w = 640
    private val h = 480
    private val frameMs = 100L

    /** Textured frame (mid-grey checkerboard) so plate crops have real sharpness and no clipping. */
    private fun frame(shade: Int = 0): ImageBuffer =
        ImageBuffer(
            w,
            h,
            IntArray(w * h) { i ->
                val v = (if (((i % w) / 2 + (i / w) / 2) % 2 == 0) 70 else 180) + shade
                (0xFF shl 24) or (v shl 16) or (v shl 8) or v
            },
        )

    private class ScriptedDetector(
        val script: (Long) -> List<VehicleDetection>,
    ) : VehicleDetector {
        var now = 0L
        override val modelVersion = "fake"

        override fun detect(image: ImageBuffer) = script(now)

        override fun close() = Unit
    }

    private class ScriptedReader(
        val text: (Int) -> List<Pair<String, Float>>,
    ) : PlateReader {
        var calls = 0
        override val modelVersion = "fake"

        override suspend fun read(vehicleCrop: ImageBuffer): List<PlateCandidate> =
            text(calls++).map { (t, c) -> PlateCandidate(t, RectF(0.2f, 0.6f, 0.8f, 0.8f), c, null) }

        override fun close() = Unit
    }

    /** A car moving left→right across the lower half of the frame. */
    private fun movingCar(
        t: Long,
        start: Long = 0,
        y: Float = 0.5f,
    ): VehicleDetection? {
        val k = (t - start) / 1000f
        if (k < 0f || k > 2f) return null
        val left = 0.05f + 0.3f * k
        return VehicleDetection(RectF(left, y, left + 0.4f, y + 0.4f), VehicleClass.CAR, 0.9f)
    }

    private fun config(roi: RegionOfInterest = RegionOfInterest.FULL_FRAME) =
        PipelineConfig(
            roi = roi,
            minMotionPxPerSec = 15f,
            duplicateWindowMs = 60_000,
            capturePolicy = CaptureRateLimiter.Policy(500, 2, 20),
        )

    private suspend fun run(
        detector: ScriptedDetector,
        reader: PlateReader,
        cfg: PipelineConfig = config(),
        durationMs: Long = 3_500,
        frameAt: (Long) -> ImageBuffer = { frame() },
    ): Pair<List<TrackOutcome>, List<FrameResult>> {
        val engine = PipelineEngine(detector, reader, { byteArrayOf(1) }, cfg)
        val outcomes = mutableListOf<TrackOutcome>()
        val results = mutableListOf<FrameResult>()
        var t = 0L
        while (t <= durationMs) {
            detector.now = t
            val r = engine.process(AnalysisFrame(t, frameAt(t)))
            results += r
            outcomes += r.finalized
            t += frameMs
        }
        outcomes += engine.flush(w, h)
        return outcomes to results
    }

    @Test
    fun `one passing car produces exactly one confident observation`() =
        runTest {
            val (outcomes, results) =
                run(ScriptedDetector { listOfNotNull(movingCar(it)) }, ScriptedReader { listOf("34 AB 123" to 0.95f) })

            val o = outcomes.single()
            assertThat(o.recognition.status).isEqualTo(RecognitionStatus.HIGH_CONFIDENCE)
            assertThat(o.recognition.plateText).isEqualTo("34 AB 123")
            assertThat(o.summary.direction).isEqualTo(TravelDirection.LEFT_TO_RIGHT)
            assertThat(o.evidence.plateCropJpeg).isNotNull()
            assertThat(results.count { it.stillRequestForTrack != null }).isAtMost(2)
            assertThat(o.stillRequested).isTrue()
        }

    @Test
    fun `parked car never produces an observation`() =
        runTest {
            val parked = VehicleDetection(RectF(0.1f, 0.5f, 0.4f, 0.8f), VehicleClass.CAR, 0.9f)
            val reader = ScriptedReader { listOf("34 AB 123" to 0.95f) }

            val (outcomes, _) = run(ScriptedDetector { listOf(parked) }, reader)

            assertThat(outcomes).isEmpty()
            assertThat(reader.calls).isEqualTo(0)
        }

    @Test
    fun `two cars passing at different heights stay two observations`() =
        runTest {
            val (outcomes, _) =
                run(
                    ScriptedDetector { t ->
                        listOfNotNull(movingCar(t, y = 0.05f), movingCar(t, start = 200, y = 0.55f))
                    },
                    ScriptedReader { listOf("34 AB 123" to 0.95f) },
                    cfg = config().copy(maxOcrPerFrame = 2),
                )

            assertThat(outcomes).hasSize(2)
            assertThat(outcomes.map { it.summary.trackId }.toSet()).hasSize(2)
        }

    @Test
    fun `cars whose ground point is outside the road area are ignored`() =
        runTest {
            val topStrip = RegionOfInterest(listOf(PointF(0f, 0f), PointF(1f, 0f), PointF(1f, 0.3f), PointF(0f, 0.3f)))

            val (outcomes, _) =
                run(
                    ScriptedDetector {
                        listOfNotNull(movingCar(it))
                    },
                    ScriptedReader { listOf("34 AB 123" to 0.95f) },
                    config(topStrip),
                )

            assertThat(outcomes).isEmpty()
        }

    @Test
    fun `garbage OCR yields an honest unreadable record, never an invented plate`() =
        runTest {
            val (outcomes, _) =
                run(
                    ScriptedDetector { listOfNotNull(movingCar(it)) },
                    ScriptedReader {
                        listOf("TR" to 0.9f)
                    },
                )

            val o = outcomes.single()
            assertThat(o.recognition.status).isEqualTo(RecognitionStatus.UNREADABLE)
            assertThat(o.recognition.plateText).isNull()
            assertThat(o.recognition.rawOcrText).isEqualTo("TR")
        }

    @Test
    fun `no text at all is recorded as no plate visible`() =
        runTest {
            val (outcomes, _) = run(ScriptedDetector { listOfNotNull(movingCar(it)) }, ScriptedReader { emptyList() })

            assertThat(outcomes.single().recognition.status).isEqualTo(RecognitionStatus.NO_PLATE_VISIBLE)
        }

    @Test
    fun `disagreeing readings are flagged for review`() =
        runTest {
            val texts = listOf("34 AB 123", "34 AB 128", "34 AB 723")
            val (outcomes, _) =
                run(
                    ScriptedDetector { listOfNotNull(movingCar(it)) },
                    ScriptedReader { i ->
                        listOf(texts[i % 3] to 0.9f)
                    },
                )

            assertThat(outcomes.single().recognition.status).isEqualTo(RecognitionStatus.NEEDS_REVIEW)
        }

    @Test
    fun `a camera jolt pauses the pipeline instead of creating events`() =
        runTest {
            val (outcomes, results) =
                run(
                    ScriptedDetector { listOfNotNull(movingCar(it)) },
                    ScriptedReader { listOf("34 AB 123" to 0.95f) },
                    durationMs = 1_500,
                    frameAt = { t -> if (t >= 600) frame(shade = 70) else frame() },
                )

            assertThat(results.any { it.sceneShift }).isTrue()
            assertThat(outcomes).isEmpty()
        }
}
