package com.plakagozcusu.vision.pipeline

import com.plakagozcusu.core.model.RecognitionStatus
import com.plakagozcusu.vision.pipeline.plate.ConsensusResult

data class DecisionThresholds(
    val minAgreement: Float = 0.7f,
    /** Every single character must be at least this agreed on for HIGH_CONFIDENCE. */
    val minCharAgreement: Float = 0.6f,
    /** A single misread letter (TFT/TET) is tolerated; several disputed positions are not. */
    val maxDisputedChars: Int = 1,
    val minSupportingFrames: Int = 2,
    val minQuality: Float = 0.7f,
    val minOcrConfidence: Float = 0.7f,
)

/**
 * Maps the evidence of a finalized track to an honest status. HIGH_CONFIDENCE is a heuristic
 * requiring agreement across frames, good image quality and OCR confidence; anything weaker is
 * NEEDS_REVIEW, and without a valid reading it is UNREADABLE or NO_PLATE_VISIBLE.
 */
object RecognitionDecider {
    fun decide(
        consensus: ConsensusResult,
        anyTextSeen: Boolean,
        bestQuality: Float,
        bestOcrConfidence: Float,
        t: DecisionThresholds,
    ): RecognitionStatus =
        when {
            consensus.text == null && !anyTextSeen -> RecognitionStatus.NO_PLATE_VISIBLE

            consensus.text == null -> RecognitionStatus.UNREADABLE

            consensus.anySpecial -> RecognitionStatus.NEEDS_REVIEW

            consensus.agreement >= t.minAgreement &&
                consensus.minCharAgreement >= t.minCharAgreement &&
                consensus.disputedChars <= t.maxDisputedChars &&
                consensus.supportingFrames >= t.minSupportingFrames &&
                bestQuality >= t.minQuality &&
                bestOcrConfidence >= t.minOcrConfidence -> RecognitionStatus.HIGH_CONFIDENCE

            else -> RecognitionStatus.NEEDS_REVIEW
        }
}

/**
 * Links (never drops) an observation whose confident plate equals one finalized within the
 * duplicate window — e.g. one car split into two tracks by an occlusion. Bounded memory.
 */
class DuplicateLinker(
    private val windowMs: Long,
) {
    private val recent = ArrayDeque<Triple<String, Long, String>>()

    /** Returns the id of an earlier observation with the same plate, and records this one. */
    fun link(
        plate: String?,
        status: RecognitionStatus,
        timeMs: Long,
        observationId: String,
    ): String? {
        while (recent.isNotEmpty() && timeMs - recent.first().second > windowMs) recent.removeFirst()
        if (plate == null || status != RecognitionStatus.HIGH_CONFIDENCE) return null
        val earlier = recent.lastOrNull { it.first == plate }?.third
        recent.addLast(Triple(plate, timeMs, observationId))
        while (recent.size > MAX_RECENT) recent.removeFirst()
        return earlier
    }

    private companion object {
        const val MAX_RECENT = 256
    }
}
