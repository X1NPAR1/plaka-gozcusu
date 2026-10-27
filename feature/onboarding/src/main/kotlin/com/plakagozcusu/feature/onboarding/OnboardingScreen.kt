package com.plakagozcusu.feature.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import com.plakagozcusu.core.designsystem.component.PgPrimaryButton
import com.plakagozcusu.core.designsystem.component.PgTextButton
import com.plakagozcusu.core.designsystem.theme.Motion
import com.plakagozcusu.core.designsystem.theme.PgTheme
import com.plakagozcusu.core.designsystem.theme.PlakaGozcusuTheme

/** Entering pages slide in from a quarter of the width; a full-width slide feels sluggish here. */
private const val SLIDE_DIVISOR = 4

@Composable
fun OnboardingScreen(
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var index by rememberSaveable { mutableIntStateOf(0) }
    // Clamp: a saved index may outlive a change to the page list (process-death restore).
    val state = OnboardingState(index.coerceIn(0, OnboardingPage.entries.lastIndex))
    BackHandler(enabled = !state.isFirst) { index = state.previous().index }

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier
                .safeDrawingPadding()
                .padding(PgTheme.spacing.xl),
        ) {
            Text(
                stringResource(R.string.onboarding_step_counter, state.stepNumber, state.stepCount),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(PgTheme.spacing.sm))
            LinearProgressIndicator(
                progress = { state.stepNumber.toFloat() / state.stepCount },
                modifier = Modifier.fillMaxWidth(),
            )
            AnimatedContent(
                targetState = state.page,
                modifier = Modifier.weight(1f),
                transitionSpec = {
                    val forward = targetState.ordinal > initialState.ordinal
                    val spec = tween<IntOffset>(Motion.MEDIUM_MS, easing = Motion.EmphasizedEasing)
                    val enter =
                        slideInHorizontally(spec) { width ->
                            if (forward) width / SLIDE_DIVISOR else -width / SLIDE_DIVISOR
                        }
                    (enter + fadeIn()).togetherWith(fadeOut())
                },
                label = "onboarding-page",
            ) { page -> OnboardingPageContent(page) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                if (state.isFirst) {
                    Spacer(Modifier)
                } else {
                    PgTextButton(stringResource(R.string.onboarding_back), onClick = { index = state.previous().index })
                }
                if (state.isLast) {
                    PgPrimaryButton(stringResource(R.string.onboarding_finish), onClick = onFinished)
                } else {
                    PgPrimaryButton(stringResource(R.string.onboarding_next), onClick = { index = state.next().index })
                }
            }
        }
    }
}

@Composable
private fun OnboardingPageContent(
    page: OnboardingPage,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(vertical = PgTheme.spacing.xxl),
        verticalArrangement = Arrangement.spacedBy(PgTheme.spacing.lg),
    ) {
        Text(
            stringResource(page.title),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            stringResource(page.body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Preview
@Composable
private fun OnboardingScreenPreview() {
    PlakaGozcusuTheme { OnboardingScreen(onFinished = {}) }
}
