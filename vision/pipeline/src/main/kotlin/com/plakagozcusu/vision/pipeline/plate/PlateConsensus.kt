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
    /** Mean per-character agreement of the winning shape (0..1). */
    val agreement: Float,
    /** Weakest single character agreement (0..1): one disputed letter keeps the record in review. */
    val minCharAgreement: Float,
    val supportingFrames: Int,
    val perCharConfidence: List<Float>?,
    val anySpecial: Boolean,
) {
    /** Positions where the frames did not (nearly) agree. */
    val disputedChars: Int get() = perCharConfidence?.count { it < UNDISPUTED } ?: 0

    private companion object {
        const val UNDISPUTED = 0.9f
    }
}

/**
 * Multi-frame consensus over the readings of one track.
 *
 * Readings are grouped by plate *shape* (province + letter count + digit count). Within the heaviest
 * shape, every character position is decided by a confidence×quality weighted vote, so "34 TFT 123",
 * "34 TFT 123", "34 TET 123" yields "34 TFT 123" with one weaker letter instead of splitting the whole
 * plate vote. Each chosen character was actually read at that position in at least one frame —
 * nothing is invented — and the composed text must still be a valid civilian plate.
 */
object PlateConsensus {
    fun decide(observations: List<PlateObservation>): ConsensusResult {
        val anySpecial = observations.any { it.parse.possiblySpecial }
        val valid = observations.filter { it.parse.isValidCivilian && it.weight > 0f }
        if (valid.isEmpty()) return ConsensusResult(null, null, 0f, 0f, 0, null, anySpecial)

        val byShape = valid.groupBy { shapeOf(it.parse.formatted!!) }
        val (_, votes) = byShape.maxBy { (_, v) -> v.sumOf { it.weight.toDouble() } }
        val length =
            votes
                .first()
                .parse.normalized!!
                .length
        val total = votes.sumOf { it.weight.toDouble() }
        val chars = CharArray(length)
        val perChar =
            (0 until length).map { i ->
                val (c, w) =
                    votes
                        .groupBy { it.parse.normalized!![i] }
                        .mapValues { (_, v) -> v.sumOf { it.weight.toDouble() } }
                        .maxBy { it.value }
                chars[i] = c
                (w / total).toFloat()
            }
        val composed = TurkishPlateParser.parse(String(chars))
        if (!composed.isValidCivilian) {
            // Defensive: fall back to the heaviest observed whole string of this shape.
            val best = votes.groupBy { it.parse.normalized!! }.maxBy { (_, v) -> v.sumOf { it.weight.toDouble() } }
            val share = (best.value.sumOf { it.weight.toDouble() } / total).toFloat()
            return ConsensusResult(
                best.key,
                best.value
                    .first()
                    .parse.formatted,
                share,
                share,
                votes.size,
                perChar,
                anySpecial,
            )
        }
        val shapeShare = (total / valid.sumOf { it.weight.toDouble() }).toFloat()
        return ConsensusResult(
            text = composed.normalized,
            formatted = composed.formatted,
            agreement = perChar.average().toFloat() * shapeShare,
            minCharAgreement = perChar.min(),
            supportingFrames = votes.size,
            perCharConfidence = perChar,
            anySpecial = anySpecial,
        )
    }

    /** "34 ABC 123" → "2-3-3": same shape means positions are comparable letter-for-letter. */
    private fun shapeOf(formatted: String): String = formatted.split(' ').joinToString("-") { it.length.toString() }
}
