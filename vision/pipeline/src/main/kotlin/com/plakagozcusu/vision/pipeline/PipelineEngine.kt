package com.plakagozcusu.vision.pipeline

import com.plakagozcusu.core.model.MotionStatus
import com.plakagozcusu.core.model.RecognitionStatus
import com.plakagozcusu.core.model.TravelDirection
import com.plakagozcusu.core.model.VehicleClass
import com.plakagozcusu.vision.api.ImageBuffer
import com.plakagozcusu.vision.api.PlateReader
import com.plakagozcusu.vision.api.VehicleDetection
import com.plakagozcusu.vision.api.VehicleDetector
import com.plakagozcusu.vision.api.capture.CaptureRateLimiter
import com.plakagozcusu.vision.api.geometry.RectF
import com.plakagozcusu.vision.api.geometry.RegionOfInterest
import com.plakagozcusu.vision.api.geometry.Size
import com.plakagozcusu.vision.pipeline.plate.PlateConsensus
import com.plakagozcusu.vision.pipeline.plate.PlateObservation
import com.plakagozcusu.vision.pipeline.plate.TurkishPlateParser
import com.plakagozcusu.vision.pipeline.quality.ImageQuality
import com.plakagozcusu.vision.pipeline.quality.QualityScorer
import com.plakagozcusu.vision.pipeline.tracking.MotionClassifier
import com.plakagozcusu.vision.pipeline.tracking.Track
import com.plakagozcusu.vision.pipeline.tracking.TrackState
import com.plakagozcusu.vision.pipeline.tracking.Tracker
import com.plakagozcusu.vision.pipeline.tracking.TrackerConfig

/** Upright analysis frame with its monotonic timestamp (ms). */
class AnalysisFrame(
    val timeMs: Long,
    val image: ImageBuffer,
)

/** Encodes evidence images (JPEG on Android, a fake in tests). */
fun interface ImageEncoder {
    fun encode(image: ImageBuffer): ByteArray
}

data class PipelineConfig(
    val roi: RegionOfInterest = RegionOfInterest.FULL_FRAME,
    val minMotionPxPerSec: Float,
    val decision: DecisionThresholds = DecisionThresholds(),
    val duplicateWindowMs: Long,
    val capturePolicy: CaptureRateLimiter.Policy,
    /** OCR runs on at most this many tracks per frame (thermal/latency budget). */
    val maxOcrPerFrame: Int = 1,
    /** Stop OCR on a track once consensus is this strong with enough frames. */
    val strongAgreement: Float = 0.85f,
    val strongFrames: Int = 3,
    val sceneShiftCooldownMs: Long = 3_000,
    val tracker: TrackerConfig = TrackerConfig(),
)

/** Identity, motion and timing of a finalized track. */
data class TrackSummary(
    val trackId: Long,
    val vehicleClass: VehicleClass,
    val motion: MotionStatus,
    val direction: TravelDirection,
    val timing: TrackTiming,
    val detectionConfidence: Float,
)

data class TrackTiming(
    val firstSeenMs: Long,
    val lastSeenMs: Long,
    val bestFrameMs: Long,
)

data class RecognitionSummary(
    val status: RecognitionStatus,
    val plateText: String?,
    val rawOcrText: String?,
    val perCharConfidence: List<Float>?,
    val plateConfidence: Float,
    val qualityScore: Float,
)

/** Encoded evidence; the scene is replaced by a matching high-res still when one was captured. */
class EvidenceImages(
    val sceneJpeg: ByteArray,
    val vehicleCropJpeg: ByteArray,
    val plateCropJpeg: ByteArray?,
)

/** What a finalized track produced; persistence (Phase 7) turns it into an Observation. */
class TrackOutcome(
    val summary: TrackSummary,
    val recognition: RecognitionSummary,
    val evidence: EvidenceImages,
    val stillRequested: Boolean,
)

data class FrameResult(
    val tracks: List<TrackView>,
    val finalized: List<TrackOutcome>,
    /** Track id for which a high-resolution still should be captured now, if any. */
    val stillRequestForTrack: Long?,
    val sceneShift: Boolean,
)

/** Lightweight overlay data for the live view (no images). */
data class TrackView(
    val id: Long,
    val box: RectF,
    val confirmed: Boolean,
    val moving: Boolean,
    val inRoi: Boolean,
)

/**
 * Orchestrates detection → tracking → motion → plate reading → consensus → best-frame → decisions.
 * Single-threaded and deterministic for a given frame sequence (driven by the replay harness in
 * tests). Only CONFIRMED + MOVING tracks whose ground point is inside the ROI get OCR; parked
 * vehicles therefore never generate observations.
 */
class PipelineEngine(
    private val detector: VehicleDetector,
    private val reader: PlateReader,
    private val encoder: ImageEncoder,
    private val config: PipelineConfig,
) {
    private val tracker = Tracker(config.tracker)
    private val shift = SceneShiftDetector()
    private val limiter = CaptureRateLimiter(config.capturePolicy)
    private val state = HashMap<Long, TrackEvidence>()
    private var pausedUntilMs = Long.MIN_VALUE

    private class TrackEvidence {
        val readings = mutableListOf<PlateObservation>()
        var anyText = false
        var bestScore = -1f
        var bestFrameMs = 0L
        var bestScene: ByteArray? = null
        var bestVehicle: ByteArray? = null
        var bestPlate: ByteArray? = null
        var bestRaw: String? = null
        var bestOcrConfidence = 0f
        var bestQuality = 0f
        var maxDetectionScore = 0f
        var largestHeight = 0f
        var stillRequested = false
        var everMovingInRoi = false
    }

    suspend fun process(frame: AnalysisFrame): FrameResult {
        if (shift.isShift(frame.image)) {
            pausedUntilMs = frame.timeMs + config.sceneShiftCooldownMs
            // Tracks across a camera jolt are meaningless; drop their in-progress evidence.
            tracker.flush().forEach { state.remove(it.id) }
        }
        if (frame.timeMs < pausedUntilMs) return FrameResult(emptyList(), emptyList(), null, sceneShift = true)

        val detections = detector.detect(frame.image)
        val expired = tracker.update(frame.timeMs, detections)
        val motion = MotionClassifier(frame.image.width, frame.image.height, config.minMotionPxPerSec)
        val views = mutableListOf<TrackView>()
        var ocrBudget = config.maxOcrPerFrame
        var stillFor: Long? = null
        val detById = tracker.active.associateWith { t -> detections.firstOrNull { it.box == t.lastBox } }

        for (track in tracker.active) {
            val ev = state.getOrPut(track.id) { TrackEvidence() }
            val inRoi = config.roi.containsGroundPointOf(track.lastBox)
            val moving = motion.motion(track) == MotionStatus.MOVING
            val confirmed = track.state == TrackState.CONFIRMED
            views += TrackView(track.id, track.lastBox, confirmed, moving, inRoi)
            val eligible = confirmed && moving && inRoi
            val det = detById[track]?.takeIf { eligible && track.lastSeenMs == frame.timeMs } ?: continue
            ev.everMovingInRoi = true
            ev.maxDetectionScore = maxOf(ev.maxDetectionScore, det.score)
            if (ocrBudget > 0 && !hasStrongConsensus(ev)) {
                ocrBudget--
                readPlate(frame, det, ev)
            }
            if (stillFor == null && claimStill(track, ev, frame.timeMs)) stillFor = track.id
        }
        val outcomes = expired.mapNotNull { finalize(it, motion) }
        return FrameResult(views, outcomes, stillFor, sceneShift = false)
    }

    /** Ends the session: finalizes every live track. */
    fun flush(
        frameWidth: Int,
        frameHeight: Int,
    ): List<TrackOutcome> {
        val motion = MotionClassifier(frameWidth, frameHeight, config.minMotionPxPerSec)
        return tracker.flush().mapNotNull { finalize(it, motion) }
    }

    private fun hasStrongConsensus(ev: TrackEvidence): Boolean {
        val c = PlateConsensus.decide(ev.readings)
        return c.text != null && c.agreement >= config.strongAgreement && c.supportingFrames >= config.strongFrames
    }

    private suspend fun readPlate(
        frame: AnalysisFrame,
        det: VehicleDetection,
        ev: TrackEvidence,
    ) {
        val img = frame.image
        val pxBox =
            RectF(
                det.box.left * img.width,
                det.box.top * img.height,
                det.box.right * img.width,
                det.box.bottom * img.height,
            )
        val vehicle = img.crop(pxBox)
        val candidates = reader.read(vehicle)
        if (candidates.isNotEmpty()) ev.anyText = true
        val frameSize = Size(img.width, img.height)
        val vehicleEdge = pxBox.touchesEdgeOf(frameSize, EDGE_MARGIN_PX)
        for (c in candidates) {
            val plateBox =
                RectF(
                    c.box.left * vehicle.width,
                    c.box.top * vehicle.height,
                    c.box.right * vehicle.width,
                    c.box.bottom * vehicle.height,
                )
            val plate = vehicle.crop(plateBox)
            val quality = QualityScorer.score(ImageQuality.measure(plate, vehicleEdge))
            val parse = TurkishPlateParser.parse(c.rawText)
            ev.readings += PlateObservation(parse, c.confidence, quality)
            if (parse.normalized != null && quality * c.confidence > ev.bestScore) {
                ev.bestScore = quality * c.confidence
                ev.bestFrameMs = frame.timeMs
                ev.bestScene = encoder.encode(img)
                ev.bestVehicle = encoder.encode(vehicle)
                ev.bestPlate = encoder.encode(plate)
                ev.bestRaw = c.rawText
                ev.bestOcrConfidence = c.confidence
                ev.bestQuality = quality
            }
        }
        if (ev.bestScene == null) {
            // No usable plate yet: keep the vehicle's best (largest) view as evidence anyway.
            ev.bestFrameMs = frame.timeMs
            ev.bestScene = encoder.encode(img)
            ev.bestVehicle = encoder.encode(vehicle)
            ev.bestRaw = candidates.maxByOrNull { it.confidence }?.rawText
        }
    }

    private fun claimStill(
        track: Track,
        ev: TrackEvidence,
        nowMs: Long,
    ): Boolean {
        if (!wantsStill(track, ev, nowMs)) return false
        ev.stillRequested = true
        limiter.record(track.id, nowMs)
        return true
    }

    /** Request a still when the vehicle is the largest seen so far (approaching) and policy allows. */
    private fun wantsStill(
        track: Track,
        ev: TrackEvidence,
        nowMs: Long,
    ): Boolean {
        val h = track.lastBox.height
        val growing = h > ev.largestHeight * STILL_GROWTH
        if (h > ev.largestHeight) ev.largestHeight = h
        return growing && limiter.check(track.id, nowMs) == CaptureRateLimiter.Decision.ALLOWED
    }

    private fun finalize(
        track: Track,
        motion: MotionClassifier,
    ): TrackOutcome? {
        val ev = state.remove(track.id) ?: return null
        limiter.forgetTrack(track.id)
        val scene = ev.bestScene
        val vehicle = ev.bestVehicle
        if (!ev.everMovingInRoi || scene == null || vehicle == null) return null
        val consensus = PlateConsensus.decide(ev.readings)
        val status =
            RecognitionDecider.decide(
                consensus,
                ev.anyText,
                ev.bestQuality,
                ev.bestOcrConfidence,
                config.decision,
            )
        return TrackOutcome(
            summary =
                TrackSummary(
                    trackId = track.id,
                    vehicleClass = track.vehicleClass,
                    motion = motion.motion(track),
                    direction = motion.direction(track),
                    timing = TrackTiming(track.firstSeenMs, track.lastSeenMs, ev.bestFrameMs),
                    detectionConfidence = ev.maxDetectionScore,
                ),
            recognition =
                RecognitionSummary(
                    status = status,
                    plateText = consensus.formatted,
                    rawOcrText = ev.bestRaw,
                    perCharConfidence = consensus.perCharConfidence,
                    plateConfidence = consensus.agreement * ev.bestOcrConfidence,
                    qualityScore = ev.bestQuality,
                ),
            evidence = EvidenceImages(scene, vehicle, ev.bestPlate),
            stillRequested = ev.stillRequested,
        )
    }

    private companion object {
        const val EDGE_MARGIN_PX = 2f
        const val STILL_GROWTH = 1.05f
    }
}
