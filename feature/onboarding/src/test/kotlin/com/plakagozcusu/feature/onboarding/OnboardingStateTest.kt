package com.plakagozcusu.feature.onboarding

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class OnboardingStateTest {
    @Test
    fun `next walks through every page and stops at the last`() {
        var state = OnboardingState()
        val visited = mutableListOf(state.page)
        repeat(OnboardingPage.entries.size + 2) {
            state = state.next()
            visited += state.page
        }

        assertThat(visited.distinct()).containsExactlyElementsIn(OnboardingPage.entries).inOrder()
        assertThat(state.isLast).isTrue()
    }

    @Test
    fun `previous never goes below the first page`() {
        val state = OnboardingState().previous()

        assertThat(state.isFirst).isTrue()
        assertThat(state.stepNumber).isEqualTo(1)
    }
}
