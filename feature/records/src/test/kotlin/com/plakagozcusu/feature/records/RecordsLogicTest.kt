package com.plakagozcusu.feature.records

import com.google.common.truth.Truth.assertThat
import com.plakagozcusu.core.model.RecognitionStatus
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class RecordsLogicTest {
    private val istanbul = ZoneId.of("Europe/Istanbul")

    @Test
    fun `incident window converts local time to a half-open UTC range`() {
        val w = IncidentWindow(LocalDate.of(2026, 10, 27), LocalTime.of(14, 0), LocalTime.of(14, 30))

        val (from, to) = w.toMillis(istanbul)

        // Istanbul is UTC+3 all year.
        assertThat(Instant.ofEpochMilli(from).toString()).isEqualTo("2026-10-27T11:00:00Z")
        assertThat(to - from).isEqualTo(30 * 60 * 1000L)
    }

    @Test
    fun `a window crossing midnight ends on the next day`() {
        val w = IncidentWindow(LocalDate.of(2026, 10, 27), LocalTime.of(23, 30), LocalTime.of(0, 30))

        val (from, to) = w.toMillis(istanbul)

        assertThat(to - from).isEqualTo(60 * 60 * 1000L)
    }

    @Test
    fun `last hours preset ends now`() {
        val now = Instant.parse("2026-10-28T09:15:00Z")

        val w = IncidentWindow.lastHours(1, now, istanbul)

        assertThat(w.from).isEqualTo(LocalTime.of(11, 15))
        assertThat(w.to).isEqualTo(LocalTime.of(12, 15))
    }

    @Test
    fun `blank search is not sent as a plate filter`() {
        assertThat(RecordsFilter(plate = "   ").toQuery().plateFragment).isNull()
        assertThat(RecordsFilter(plate = "34 AB").toQuery().plateFragment).isEqualTo("34 AB")
        assertThat(RecordsFilter(statuses = setOf(RecognitionStatus.UNREADABLE)).toQuery().statuses)
            .containsExactly(RecognitionStatus.UNREADABLE)
    }
}
