package com.plakagozcusu.vision.pipeline.plate

/** One plate reading of a track at one frame. Weight = OCR confidence × image quality. */
data class PlateObservation(
    val parse: PlateParse,
    val ocrConfidence: Float,
    val quality: Float,
) {
    val weight: Float get() = (ocrConfidence * quality).coerceAtLeast(0f)
}

data class ConsensusResult(
    val text: String?,
    val formatted: String?,
    /** Share of total weight agreeing with [text] (0..1). */
    val agreement: Float,
    val supportingFrames: Int,
    val perCharConfidence: List<Float>?,
    val anySpecial: Boolean,
)

/**
 * Multi-frame consensus over the readings of one track. Only valid civilian parses vote on the
 * text; per-character agreement is computed over same-length votes. Characters are never invented:
 * the result is always one of the observed normalized strings.
 */
object PlateConsensus {
    fun decide(observations: List<PlateObservation>): ConsensusResult {
        val valid = observations.filter { it.parse.isValidCivilian && it.weight > 0f }
        if (valid.isEmpty()) {
            return ConsensusResult(null, null, 0f, 0, null, observations.any { it.parse.possiblySpecial })
        }
        val total = valid.sumOf { it.weight.toDouble() }.toFloat()
        val byText = valid.groupBy { it.parse.normalized!! }
        val (text, votes) = byText.maxBy { (_, v) -> v.sumOf { it.weight.toDouble() } }
        val support = votes.sumOf { it.weight.toDouble() }.toFloat()
        val sameLength = valid.filter { it.parse.normalized!!.length == text.length }
        val perChar =
            text.indices.map { i ->
                val agree = sameLength.filter { it.parse.normalized!![i] == text[i] }.sumOf { it.weight.toDouble() }
                (agree / sameLength.sumOf { it.weight.toDouble() }).toFloat()
            }
        return ConsensusResult(
            text = text,
            formatted = votes.first().parse.formatted,
            agreement = support / total,
            supportingFrames = votes.size,
            perCharConfidence = perChar,
            anySpecial = observations.any { it.parse.possiblySpecial },
        )
    }
}
