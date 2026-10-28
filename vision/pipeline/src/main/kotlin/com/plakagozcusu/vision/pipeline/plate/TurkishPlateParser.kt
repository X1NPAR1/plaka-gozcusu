package com.plakagozcusu.vision.pipeline.plate

import java.util.Locale

/**
 * Result of parsing one OCR string. [raw] is always kept verbatim. [normalized] is only set when a
 * unique civilian-format reading exists; otherwise the caller must treat the text as uncertain.
 */
data class PlateParse(
    val raw: String,
    val normalized: String?,
    /** Display form "34 AB 123", or null. */
    val formatted: String?,
    /** True when a digit position needed an OCR-confusion fix (e.g. O→0 in the province code). */
    val corrected: Boolean,
    /** More than one valid civilian reading exists (e.g. letter O vs digit 0 at the boundary). */
    val ambiguous: Boolean,
    /** Syntax matches a known special series (diplomatic, police, temporary, foreign resident...). */
    val possiblySpecial: Boolean,
) {
    val isValidCivilian: Boolean get() = normalized != null && !ambiguous
}

/**
 * Turkish civilian plate rules (source: en.wikipedia.org "Vehicle registration plates of Turkey",
 * accessed 2026-10-28; the regulation text itself could not be retrieved — see ADR-008):
 * province 01–81, then 1 letter + 4–5 digits, 2 letters + 3–4 digits, or 3 letters + 2–3 digits.
 * Serial letters exclude Ç Ş İ Ö Ü Ğ and Q W X.
 *
 * Confusion fixes are applied **only to digit positions** (province code and number group), never
 * to letters: a letter that looks like a digit could be a real letter, so it is not rewritten.
 */
object TurkishPlateParser {
    private const val ALLOWED_LETTERS = "ABCDEFGHIJKLMNOPRSTUVYZ"
    private const val PROVINCE_MIN = 1
    private const val PROVINCE_MAX = 81
    private const val PROVINCE_LEN = 2
    private const val MAX_LETTERS = 3
    private val BOUNDARY_CONFUSABLE = setOf('O', 'I')

    /** Letter-count → allowed digit-group lengths. */
    private val FORMATS = mapOf(1 to 4..5, 2 to 3..4, 3 to 2..3)

    /** Most common letter-for-digit OCR misreads; kept small so real letters are not rewritten. */
    private val DIGIT_CONFUSIONS =
        mapOf('O' to '0', 'I' to '1', 'Z' to '2', 'S' to '5', 'B' to '8', 'G' to '6')

    // Series documented as special/non-private (same syntax as civilian plates).
    private val SPECIAL_TWO_LETTER = setOf("CD", "CC")
    private val SPECIAL_THREE_LETTER = setOf("GMR", "SGH")
    private val SPECIAL_ONE_LETTER = setOf("G", "T", "B")

    fun parse(raw: String): PlateParse {
        val cleaned = raw.uppercase(Locale.ROOT).filter { it.isLetterOrDigit() }
        val readings = candidateReadings(cleaned).distinctBy { it.normalized }
        val minFixes = readings.minOfOrNull { it.fixes }
        val best = readings.filter { it.fixes == minFixes }
        val chosen = best.singleOrNull()
        // Ambiguous: several equally good readings, or the chosen one ends its letter group with a
        // glyph that is also a digit look-alike (O/0, I/1) while another valid split exists.
        val ambiguous =
            best.size > 1 ||
                (chosen != null && readings.size > 1 && chosen.letters.last() in BOUNDARY_CONFUSABLE)
        val pick = chosen ?: best.firstOrNull()
        return PlateParse(
            raw = raw,
            normalized = pick?.normalized,
            formatted = pick?.formatted,
            corrected = (pick?.fixes ?: 0) > 0,
            ambiguous = ambiguous,
            possiblySpecial = pick?.let { isSpecialSeries(it.letters) } ?: false,
        )
    }

    private data class Reading(
        val province: String,
        val letters: String,
        val digits: String,
        val fixes: Int,
    ) {
        val normalized get() = province + letters + digits
        val formatted get() = "$province $letters $digits"
    }

    private fun candidateReadings(s: String): List<Reading> {
        if (s.length < PROVINCE_LEN + 1 + 2) return emptyList()
        val (province, provinceFixes) = toDigits(s.substring(0, PROVINCE_LEN)) ?: return emptyList()
        if (province.toInt() !in PROVINCE_MIN..PROVINCE_MAX) return emptyList()
        val rest = s.substring(PROVINCE_LEN)
        return (1..MAX_LETTERS).mapNotNull { n ->
            if (rest.length <= n) return@mapNotNull null
            val letters = rest.substring(0, n)
            if (!letters.all { it in ALLOWED_LETTERS }) return@mapNotNull null
            val (digits, digitFixes) = toDigits(rest.substring(n)) ?: return@mapNotNull null
            if (digits.length !in FORMATS.getValue(n)) return@mapNotNull null
            Reading(province, letters, digits, provinceFixes + digitFixes)
        }
    }

    private fun toDigits(s: String): Pair<String, Int>? {
        var fixes = 0
        val out =
            s.map { c ->
                when {
                    c.isDigit() -> c
                    c in DIGIT_CONFUSIONS -> DIGIT_CONFUSIONS.getValue(c).also { fixes++ }
                    else -> return null
                }
            }
        return out.joinToString("") to fixes
    }

    private fun isSpecialSeries(letters: String): Boolean =
        when (letters.length) {
            1 -> letters in SPECIAL_ONE_LETTER
            2 -> letters in SPECIAL_TWO_LETTER || letters.startsWith('M')
            else -> letters in SPECIAL_THREE_LETTER || letters.startsWith('J')
        }
}
