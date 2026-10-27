package com.plakagozcusu.app.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import com.plakagozcusu.app.R
import com.plakagozcusu.core.designsystem.component.PgCard
import com.plakagozcusu.core.designsystem.component.PgEmptyState
import com.plakagozcusu.core.designsystem.component.PgStatusPill
import com.plakagozcusu.core.designsystem.component.PgTextButton
import com.plakagozcusu.core.designsystem.component.PillTone
import com.plakagozcusu.core.designsystem.theme.PgTheme
import com.plakagozcusu.core.designsystem.theme.PlakaGozcusuTheme

/**
 * Temporary home shown until the real dashboard lands in Phase 8. It deliberately shows no
 * counts or observations: nothing is recorded yet and the UI must never display fabricated numbers.
 */
@Composable
fun HomeScreen(
    onRestartOnboarding: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier
                .safeDrawingPadding()
                .padding(PgTheme.spacing.xl),
            verticalArrangement = Arrangement.spacedBy(PgTheme.spacing.lg),
        ) {
            Text(
                stringResource(R.string.home_title),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.semantics { heading() },
            )
            PgStatusPill(label = stringResource(R.string.home_status_setup), tone = PillTone.NEUTRAL)
            PgCard(Modifier.fillMaxWidth()) {
                PgEmptyState(
                    title = stringResource(R.string.home_not_ready_title),
                    message = stringResource(R.string.home_not_ready_body),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            PgTextButton(stringResource(R.string.home_restart_onboarding), onClick = onRestartOnboarding)
        }
    }
}

@Preview
@Composable
private fun HomeScreenPreview() {
    PlakaGozcusuTheme { HomeScreen(onRestartOnboarding = {}) }
}
