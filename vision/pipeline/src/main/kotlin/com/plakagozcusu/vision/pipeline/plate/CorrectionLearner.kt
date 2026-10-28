package com.plakagozcusu.vision.pipeline.plate

/**
 * Learns this installation's typical OCR mistakes from the user's own corrections (e.g. O→D in the
 * letter group seen 3 times) and proposes a corrected reading for new uncertain plates.
 *
 * It only ever produces a *suggestion*: the caller shows it and the user decides. Suggestions must
 * be valid civilian plates and differ by exactly one learned substitution, so nothing is invented.
 */
class CorrectionLearner(
    corrections: List<Pair<String, String>>,
    private val minEvidence: Int = DEFAULT_MIN_EVIDENCE,
) {
    /** (from, to, inLetterGroup) → how often the user made this fix. */
    private val counts: Map<Triple<Char, Char, Boolean>, Int> =
        corrections
            .mapNotNull { (auto, fixed) -> pair(auto, fixed) }
            .flatMap { (a, f) ->
                val letters = letterMask(f)
                a.indices.filter { a[it] != f[it] }.map { Triple(a[it], f[it], letters[it]) }
            }.groupingBy { it }
            .eachCount()

    val learnedRules: Int get() = counts.count { it.value >= minEvidence }

    /** Best single-substitution suggestion for [reading], or null. */
    fun suggest(reading: String): String? {
        val parsed = TurkishPlateParser.parse(reading)
        val text = parsed.normalized ?: return null
        val letters = letterMask(text)
        return text.indices
            .flatMap { i ->
                counts
                    .filter { (k, n) -> k.first == text[i] && k.third == letters[i] && n >= minEvidence }
                    .map { (k, n) -> Triple(i, k.second, n) }
            }.sortedByDescending { it.third }
            .firstNotNullOfOrNull { (i, to, _) ->
                val candidate = text.substring(0, i) + to + text.substring(i + 1)
                TurkishPlateParser.parse(candidate).takeIf { it.isValidCivilian }?.formatted
            }
    }

    private fun pair(
        auto: String,
        fixed: String,
    ): Pair<String, String>? {
        val a = TurkishPlateParser.parse(auto).normalized ?: auto.filter { it.isLetterOrDigit() }.uppercase()
        val f = TurkishPlateParser.parse(fixed).normalized ?: return null
        return if (a.length == f.length && a != f) a to f else null
    }

    /** true where the (valid) plate has its letter group. */
    private fun letterMask(normalized: String): BooleanArray = BooleanArray(normalized.length) { normalized[it].isLetter() }

    private companion object {
        const val DEFAULT_MIN_EVIDENCE = 2
    }
}
