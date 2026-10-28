package com.plakagozcusu.vision.pipeline.plate

import com.google.common.truth.Truth.assertThat
import org.junit.Test

// Synthetic plates only.
class CorrectionLearnerTest {
    @Test
    fun `a repeated O to D fix becomes a suggestion for new readings`() {
        val learner = CorrectionLearner(listOf("34 PO 123" to "34 PD 123", "06 OA 456" to "06 DA 456"))

        assertThat(learner.suggest("35 PO 777")).isEqualTo("35 PD 777")
        assertThat(learner.learnedRules).isEqualTo(1)
    }

    @Test
    fun `a single correction is not enough evidence`() {
        val learner = CorrectionLearner(listOf("34 TET 123" to "34 TFT 123"))

        assertThat(learner.suggest("34 TET 456")).isNull()
    }

    @Test
    fun `letter-group fixes are not applied to digits`() {
        val learner = CorrectionLearner(listOf("34 PO 123" to "34 PD 123", "34 RO 123" to "34 RD 123"))

        // "0" in the number group is a digit, not the learned letter O.
        assertThat(learner.suggest("34 AB 100")).isNull()
    }

    @Test
    fun `suggestions must still be valid plates`() {
        val learner = CorrectionLearner(listOf("34 PO 123" to "34 PD 123", "34 PO 456" to "34 PD 456"))

        assertThat(learner.suggest("garbage")).isNull()
    }
}
