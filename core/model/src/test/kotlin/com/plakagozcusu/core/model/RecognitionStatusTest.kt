package com.plakagozcusu.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class RecognitionStatusTest {
    @Test
    fun `only user states count as user confirmed`() {
        val confirmed = RecognitionStatus.entries.filter { it.isUserConfirmed }

        assertThat(confirmed).containsExactly(RecognitionStatus.USER_VERIFIED, RecognitionStatus.USER_CORRECTED)
    }

    @Test
    fun `unreadable and missing plates never expose plate text`() {
        listOf(
            RecognitionStatus.PENDING,
            RecognitionStatus.UNREADABLE,
            RecognitionStatus.NO_PLATE_VISIBLE,
            RecognitionStatus.FAILED,
        ).forEach { assertThat(it.hasPlateText).isFalse() }
    }
}
