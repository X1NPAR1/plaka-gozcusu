package com.plakagozcusu.feature.onboarding

import androidx.annotation.StringRes

/** Informational onboarding steps; permission and calibration steps are appended in Phase 3. */
internal enum class OnboardingPage(
    @param:StringRes val title: Int,
    @param:StringRes val body: Int,
) {
    PURPOSE(R.string.onboarding_purpose_title, R.string.onboarding_purpose_body),
    PRIVACY(R.string.onboarding_privacy_title, R.string.onboarding_privacy_body),
    ACCURACY(R.string.onboarding_accuracy_title, R.string.onboarding_accuracy_body),
    MOUNTING(R.string.onboarding_mounting_title, R.string.onboarding_mounting_body),
}

/** Pure navigation state so step logic is unit-testable without Compose. */
internal data class OnboardingState(
    val index: Int = 0,
) {
    val page: OnboardingPage get() = OnboardingPage.entries[index]
    val isFirst: Boolean get() = index == 0
    val isLast: Boolean get() = index == OnboardingPage.entries.lastIndex
    val stepNumber: Int get() = index + 1
    val stepCount: Int get() = OnboardingPage.entries.size

    fun next(): OnboardingState = if (isLast) this else copy(index = index + 1)

    fun previous(): OnboardingState = if (isFirst) this else copy(index = index - 1)
}
