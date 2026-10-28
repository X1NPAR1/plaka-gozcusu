package com.plakagozcusu.vision.pipeline

import com.google.common.truth.Truth.assertThat
import com.plakagozcusu.core.model.MotionStatus
import com.plakagozcusu.core.model.RecognitionStatus
import com.plakagozcusu.core.model.VehicleClass
import com.plakagozcusu.vision.api.ImageBuffer
import com.plakagozcusu.vision.api.VehicleDetection
import com.plakagozcusu.vision.api.geometry.RectF
import com.plakagozcusu.vision.pipeline.plate.PlateConsensus
import com.plakagozcusu.vision.pipeline.plate.PlateObservation
import com.plakagozcusu.vision.pipeline.plate.TurkishPlateParser
import com.plakagozcusu.vision.pipeline.quality.ImageQuality
import com.plakagozcusu.vision.pipeline.quality.QualityScorer
import com.plakagozcusu.vision.pipeline.tracking.MotionClassifier
import com.plakagozcusu.vision.pipeline.tracking.TrackState
import com.plakagozcusu.vision.pipeline.tracking.Tracker
import com.plakagozcusu.vision.pipeline.tracking.TrackerConfig
import org.junit.Test

class PipelineUnitsTest {
    private fun det(
        l: Float,
        t: Float = 0.4f,
    ) = VehicleDetection(RectF(l, t, l + 0.2f, t + 0.2f), VehicleClass.CAR, 0.9f)

    private fun obs(
        text: String,
        conf: Float = 0.9f,
        q: Float = 0.8f,
    ) = PlateObservation(TurkishPlateParser.parse(text), conf, q)

    @Test
    fun `consensus picks the weighted majority and reports per-char agreement`() {
        val c = PlateConsensus.decide(listOf(obs("34 AB 123"), obs("34 AB 123"), obs("34 AB 128", conf = 0.5f)))

        assertThat(c.text).isEqualTo("34AB123")
        assertThat(c.supportingFrames).isEqualTo(2)
        assertThat(c.agreement).isGreaterThan(0.7f)
        assertThat(c.perCharConfidence!!.last()).isLessThan(1f)
        assertThat(c.perCharConfidence.first()).isEqualTo(1f)
    }

    @Test
    fun `consensus without valid readings returns no text`() {
        assertThat(PlateConsensus.decide(listOf(obs("TR"), obs("??"))).text).isNull()
    }

    @Test
    fun `tracker keeps identity through a short dropout and expires after the timeout`() {
        val tracker = Tracker(TrackerConfig(confirmHits = 2, maxLostMs = 300))
        tracker.update(0, listOf(det(0.1f)))
        tracker.update(100, listOf(det(0.12f)))
        val id = tracker.active.single().id
        assertThat(tracker.active.single().state).isEqualTo(TrackState.CONFIRMED)

        tracker.update(200, emptyList())
        tracker.update(300, listOf(det(0.16f)))
        assertThat(tracker.active.single().id).isEqualTo(id)

        tracker.update(400, emptyList())
        tracker.update(500, emptyList())
        val expired = tracker.update(800, emptyList())
        assertThat(expired.map { it.id }).containsExactly(id)
        assertThat(tracker.active).isEmpty()
    }

    @Test
    fun `tentative one-frame blips are dropped silently`() {
        val tracker = Tracker()
        tracker.update(0, listOf(det(0.5f)))
        tracker.update(100, emptyList())

        assertThat(tracker.active).isEmpty()
    }

    @Test
    fun `two overlapping-path vehicles keep separate ids`() {
        val tracker = Tracker(TrackerConfig(confirmHits = 1))
        tracker.update(0, listOf(det(0.1f, 0.1f), det(0.1f, 0.6f)))
        tracker.update(100, listOf(det(0.12f, 0.1f), det(0.12f, 0.6f)))

        assertThat(tracker.active.map { it.id }.toSet()).hasSize(2)
    }

    @Test
    fun `motion classifier separates parked from moving`() {
        val tracker = Tracker(TrackerConfig(confirmHits = 1))
        (0..5).forEach { i -> tracker.update(i * 100L, listOf(det(0.1f + i * 0.05f, 0.1f), det(0.5f, 0.6f))) }
        val mc = MotionClassifier(640, 480, minSpeedPxPerSec = 15f)
        val (moving, parked) = tracker.active.sortedBy { it.lastBox.top }

        assertThat(mc.motion(moving)).isEqualTo(MotionStatus.MOVING)
        assertThat(mc.motion(parked)).isEqualTo(MotionStatus.STATIONARY)
    }

    @Test
    fun `quality prefers large sharp unclipped crops`() {
        fun img(
            w: Int,
            checker: Boolean,
            hi: Int,
        ) = ImageBuffer(w, 30, IntArray(w * 30) { i -> if (!checker || ((i % w) + i / w) % 2 == 0) hi else 60 })
        val sharp = QualityScorer.score(ImageQuality.measure(img(150, true, 0xFFB4B4B4.toInt() and 0xFFFFFF), false))
        val blurry = QualityScorer.score(ImageQuality.measure(img(150, false, 120), false))
        val tiny = QualityScorer.score(ImageQuality.measure(img(40, true, 180), false))

        assertThat(sharp).isGreaterThan(blurry)
        assertThat(tiny).isEqualTo(0f)
    }

    @Test
    fun `decider rules`() {
        val strong = PlateConsensus.decide(listOf(obs("34 AB 123"), obs("34 AB 123")))
        val t = DecisionThresholds()

        assertThat(RecognitionDecider.decide(strong, true, 0.8f, 0.9f, t)).isEqualTo(RecognitionStatus.HIGH_CONFIDENCE)
        assertThat(RecognitionDecider.decide(strong, true, 0.3f, 0.9f, t)).isEqualTo(RecognitionStatus.NEEDS_REVIEW)
        val special = PlateConsensus.decide(listOf(obs("06 CD 123"), obs("06 CD 123")))
        assertThat(RecognitionDecider.decide(special, true, 0.9f, 0.9f, t)).isEqualTo(RecognitionStatus.NEEDS_REVIEW)
    }

    @Test
    fun `duplicate linker links same confident plate inside the window only`() {
        val linker = DuplicateLinker(windowMs = 60_000)

        assertThat(linker.link("34AB123", RecognitionStatus.HIGH_CONFIDENCE, 0, "a")).isNull()
        assertThat(linker.link("34AB123", RecognitionStatus.HIGH_CONFIDENCE, 30_000, "b")).isEqualTo("a")
        assertThat(linker.link("34AB123", RecognitionStatus.NEEDS_REVIEW, 31_000, "c")).isNull()
        assertThat(linker.link("34AB123", RecognitionStatus.HIGH_CONFIDENCE, 200_000, "d")).isNull()
    }

    @Test
    fun `scene shift fires on a whole-frame change but not on a moving object`() {
        val d = SceneShiftDetector()
        val base = ImageBuffer(160, 120, IntArray(160 * 120) { 0xFF404040.toInt() })
        val withCar = ImageBuffer(160, 120, base.argb.copyOf().also { a -> for (i in 0 until 160 * 30) a[i] = -1 })
        val bright = ImageBuffer(160, 120, IntArray(160 * 120) { 0xFFC0C0C0.toInt() })

        assertThat(d.isShift(base)).isFalse()
        assertThat(d.isShift(withCar)).isFalse()
        assertThat(d.isShift(bright)).isTrue()
    }
}
