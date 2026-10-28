package com.plakagozcusu.vision.pipeline.plate

import com.google.common.truth.Truth.assertThat
import org.junit.Test

// All plate strings here are synthetic examples of the documented formats, not real vehicles.
class TurkishPlateParserTest {
    private fun p(s: String) = TurkishPlateParser.parse(s)

    @Test
    fun `every documented civilian format is accepted`() {
        mapOf(
            "06 A 1234" to "06 A 1234",
            "06 A 12345" to "06 A 12345",
            "34 AB 123" to "34 AB 123",
            "34 AB 1234" to "34 AB 1234",
            "35 ABC 12" to "35 ABC 12",
            "35 ABC 123" to "35 ABC 123",
        ).forEach { (input, formatted) ->
            val r = p(input)
            assertThat(r.isValidCivilian).isTrue()
            assertThat(r.formatted).isEqualTo(formatted)
            assertThat(r.corrected).isFalse()
        }
    }

    @Test
    fun `separators and case are normalized but raw is kept`() {
        val r = p("34-ab.123")

        assertThat(r.normalized).isEqualTo("34AB123")
        assertThat(r.raw).isEqualTo("34-ab.123")
    }

    @Test
    fun `province code must be 01 to 81`() {
        listOf("00 AB 123", "82 AB 123", "99 AB 123").forEach { assertThat(p(it).normalized).isNull() }
        assertThat(p("81 AB 123").isValidCivilian).isTrue()
    }

    @Test
    fun `wrong group lengths are rejected`() {
        listOf("34 A 123", "34 A 123456", "34 AB 12", "34 AB 12345", "34 ABC 1", "34 ABC 1234", "34 ABCD 12")
            .forEach { assertThat(p(it).normalized).isNull() }
    }

    @Test
    fun `excluded letters are rejected`() {
        listOf("34 QA 123", "34 AW 123", "34 XB 123", "34 ÇA 123", "34 AŞ 123").forEach {
            assertThat(p(it).normalized).isNull()
        }
    }

    @Test
    fun `confusions are fixed only in digit positions and flagged`() {
        val r = p("3O AB 1Z3")

        assertThat(r.normalized).isEqualTo("30AB123")
        assertThat(r.corrected).isTrue()
    }

    @Test
    fun `letter positions are never converted from digits`() {
        // "34 8B 123": 8 sits where the letter group must start; it must not become B.
        assertThat(p("34 8B 123").normalized).isNull()
        // "34 A8 123" is still a valid reading as "34 A 8123" (no conversion needed).
        assertThat(p("34 A8 123").formatted).isEqualTo("34 A 8123")
    }

    @Test
    fun `boundary ambiguity is reported instead of guessed`() {
        // "34 ABO 23" vs "34 AB 023": O could be a letter or a misread zero.
        val r = p("34ABO23")

        assertThat(r.ambiguous).isTrue()
        assertThat(r.isValidCivilian).isFalse()
    }

    @Test
    fun `a clean reading is not made ambiguous by letter-to-digit look-alikes`() {
        // B could be read as 8 ("34 A 81234"), but the zero-fix reading wins.
        val r = p("34AB1234")

        assertThat(r.isValidCivilian).isTrue()
        assertThat(r.formatted).isEqualTo("34 AB 1234")
    }

    @Test
    fun `special series are flagged for review`() {
        listOf(
            "06 CD 123",
            "34 MA 123",
            "34 G 1234",
            "06 JAA 123",
        ).forEach { assertThat(p(it).possiblySpecial).isTrue() }
        assertThat(p("34 AB 123").possiblySpecial).isFalse()
    }

    @Test
    fun `garbage and empty strings never produce a plate`() {
        listOf("", "TR", "STOP", "34", "????").forEach { assertThat(p(it).normalized).isNull() }
    }

    @Test
    fun `foreign plate syntax is not forced into Turkish format`() {
        assertThat(p("B MW 1234").normalized).isNull()
        assertThat(p("AB-123-CD").normalized).isNull()
    }
}
