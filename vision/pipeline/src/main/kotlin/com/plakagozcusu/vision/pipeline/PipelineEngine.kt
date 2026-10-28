package com.plakagozcusu.vision.pipeline

import com.plakagozcusu.core.model.MotionStatus
import com.plakagozcusu.core.model.RecognitionStatus
import com.plakagozcusu.core.model.TravelDirection
import com.plakagozcusu.core.model.VehicleClass
import com.plakagozcusu.vision.api.ImageBuffer
import com.plakagozcusu.vision.api.PlateCandidate
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
    /** Hard cap of OCR calls per track (thermal budget; long-lived tracks). */
    val maxOcrPerTrack: Int = 40,
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

    // Raw (not yet encoded) evidence images; JPEG encoding happens once, at finalize.
    private class RawImages(
        val scene: ImageBuffer,
        val vehicle: ImageBuffer,
        val plate: ImageBuffer?,
    )

    /** Best evidence seen for one reading (per normalized plate text, or the no-plate fallback). */
    private class Evidence(
        val score: Float,
        val frameMs: Long,
        val images: RawImages,
        val raw: String?,
        val ocrConfidence: Float,
        val quality: Float,
    )

    private class TrackEvidence {
        val readings = ArrayDeque<PlateObservation>()
        var ocrCalls = 0
        var anyText = false

        /** Best frame per distinct valid plate text, so stored crops always match the stored text. */
        val byText = LinkedHashMap<String, Evidence>()

        /** Largest view of the vehicle, used when no valid plate was ever read. */
        var fallback: Evidence? = null
        var fallbackArea = 0f
        var maxDetectionScore = 0f
        var largestHeight = 0f
        var stillRequested = false
        var everMovingInRoi = false
    }

    suspend fun process(frame: AnalysisFrame): FrameResult {
        val jolted = mutableListOf<TrackOutcome>()
        if (shift.isShift(frame.image)) {
            pausedUntilMs = frame.timeMs + config.sceneShiftCooldownMs
            // Boxes are meaningless across a camera jolt: end every live track now, keeping
            // whatever evidence was already gathered rather than silently dropping it.
            val motion = MotionClassifier(frame.image.width, frame.image.height, config.minMotionPxPerSec)
            jolted += tracker.flush().mapNotNull { finalize(it, motion) }
        }
        if (frame.timeMs < pausedUntilMs) return FrameResult(emptyList(), jolted, null, sceneShift = true)

        val detections = detector.detect(frame.image)
        val expired = tracker.update(frame.timeMs, detections)
        val motion = MotionClassifier(frame.image.width, frame.image.height, config.minMotionPxPerSec)
        val views = mutableListOf<TrackView>()
        val eligible = mutableListOf<Pair<VehicleDetection, TrackEvidence>>()
        var stillFor: Long? = null
        val detById = tracker.active.associateWith { t -> detections.firstOrNull { it.box == t.lastBox } }

        for (track in tracker.active) {
            val ev = state.getOrPut(track.id) { TrackEvidence() }
            val inRoi = config.roi.containsGroundPointOf(track.lastBox)
            val moving = motion.motion(track) == MotionStatus.MOVING
            val confirmed = track.state == TrackState.CONFIRMED
            views += TrackView(track.id, track.lastBox, confirmed, moving, inRoi)
            val ok = confirmed && moving && inRoi
            val det = detById[track]?.takeIf { ok && track.lastSeenMs == frame.timeMs } ?: continue
            ev.everMovingInRoi = true
            ev.maxDetectionScore = maxOf(ev.maxDetectionScore, det.score)
            if (ev.ocrCalls < config.maxOcrPerTrack && !hasStrongConsensus(ev)) eligible += det to ev
            if (stillFor == null && claimStill(track, ev, frame.timeMs)) stillFor = track.id
        }
        // Fair OCR budget: the least-read tracks go first, so a long-lived hard case cannot starve
        // a second vehicle passing at the same time.
        for ((det, ev) in eligible.sortedBy { it.second.ocrCalls }.take(config.maxOcrPerFrame)) {
            readPlate(frame, det, ev)
        }
        val outcomes = jolted + expired.mapNotNull { finalize(it, motion) }
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
        val c = PlateConsensus.decide(ev.readings.toList())
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
        ev.ocrCalls++
        val candidates = reader.read(vehicle)
        if (candidates.isNotEmpty()) ev.anyText = true
        val vehicleEdge = pxBox.touchesEdgeOf(Size(img.width, img.height), EDGE_MARGIN_PX)
        for (c in candidates) recordCandidate(frame, vehicle, c, vehicleEdge, ev)
        val area = det.box.area
        if (area > ev.fallbackArea) {
            ev.fallbackArea = area
            val raw = candidates.maxByOrNull { it.confidence }
            val images = RawImages(img, vehicle, null)
            ev.fallback = Evidence(0f, frame.timeMs, images, raw?.rawText, raw?.confidence ?: 0f, 0f)
        }
    }

    private fun recordCandidate(
        frame: AnalysisFrame,
        vehicle: ImageBuffer,
        c: PlateCandidate,
        vehicleEdge: Boolean,
        ev: TrackEvidence,
    ) {
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
        ev.readings.addLast(PlateObservation(parse, c.confidence, quality))
        if (ev.readings.size > MAX_READINGS) ev.readings.removeFirst()
        val text = parse.normalized?.takeIf { parse.isValidCivilian } ?: return
        val score = quality * c.confidence
        if (shouldReplace(ev.byText[text], score, ev.byText.size)) {
            val images = RawImages(frame.image, vehicle, plate)
            ev.byText[text] = Evidence(score, frame.timeMs, images, c.rawText, c.confidence, quality)
        }
    }

    private fun shouldReplace(
        current: Evidence?,
        score: Float,
        knownTexts: Int,
    ): Boolean = if (current == null) knownTexts < MAX_TEXTS else score > current.score

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
        limiter.forgetTrack(track.id)
        val ev = state.remove(track.id)?.takeIf { it.everMovingInRoi } ?: return null
        val consensus = PlateConsensus.decide(ev.readings.toList())
        // Evidence, raw OCR, quality and confidence all come from a reading of the consensus text.
        val chosen = consensus.text?.let { ev.byText[it] } ?: ev.fallback ?: return null
        val status =
            RecognitionDecider.decide(
                consensus,
                ev.anyText,
                chosen.quality,
                chosen.ocrConfidence,
                config.decision,
            )
        return TrackOutcome(
            summary =
                TrackSummary(
                    trackId = track.id,
                    vehicleClass = track.vehicleClass,
                    motion = motion.motion(track),
                    direction = motion.direction(track),
                    timing = TrackTiming(track.firstSeenMs, track.lastSeenMs, chosen.frameMs),
                    detectionConfidence = ev.maxDetectionScore,
                ),
            recognition =
                RecognitionSummary(
                    status = status,
                    plateText = consensus.formatted,
                    rawOcrText = chosen.raw,
                    perCharConfidence = consensus.perCharConfidence,
                    plateConfidence = consensus.agreement * chosen.ocrConfidence,
                    qualityScore = chosen.quality,
                ),
            evidence = encode(chosen.images),
            stillRequested = ev.stillRequested,
        )
    }

    private fun encode(raw: RawImages) =
        EvidenceImages(
            sceneJpeg = encoder.encode(raw.scene),
            vehicleCropJpeg = encoder.encode(raw.vehicle),
            plateCropJpeg = raw.plate?.let(encoder::encode),
        )

    private companion object {
        const val EDGE_MARGIN_PX = 2f
        const val STILL_GROWTH = 1.05f

        /** Bounds per-track memory for long-lived tracks (traffic jams, creeping buses). */
        const val MAX_READINGS = 30
        const val MAX_TEXTS = 3
    }
}
