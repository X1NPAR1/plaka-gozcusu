package com.plakagozcusu.feature.records.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.plakagozcusu.core.data.ai.AiCheckOutcome
import com.plakagozcusu.core.designsystem.component.PgCard
import com.plakagozcusu.core.designsystem.component.PgSecondaryButton
import com.plakagozcusu.core.designsystem.component.PgTextButton
import com.plakagozcusu.core.designsystem.component.StatusBadge
import com.plakagozcusu.core.designsystem.component.TimeStampOverlay
import com.plakagozcusu.core.designsystem.theme.PgTheme
import com.plakagozcusu.core.designsystem.theme.PlateTextStyle
import com.plakagozcusu.core.model.EvidenceFile
import com.plakagozcusu.core.model.Observation
import com.plakagozcusu.core.model.RecognitionStatus
import com.plakagozcusu.feature.records.AiUiState
import com.plakagozcusu.feature.records.DetailViewModel
import com.plakagozcusu.feature.records.R

@Composable
internal fun AiCard(
    o: Observation,
    state: AiUiState,
    onAsk: () -> Unit,
    onAccept: () -> Unit,
) {
    PgCard(Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.ai_section), style = MaterialTheme.typography.titleMedium)
        val ai = o.ai
        val aiPlate = ai?.plateText
        when {
            ai == null -> Text(stringResource(R.string.ai_none), color = MaterialTheme.colorScheme.onSurfaceVariant)
            aiPlate == null -> Text(stringResource(R.string.ai_unreadable))
            ai.agreesWithReading -> Text(stringResource(R.string.ai_agrees, aiPlate), color = PgTheme.status.success)
            else -> Text(stringResource(R.string.ai_differs, aiPlate), color = PgTheme.status.warning, style = PlateTextStyle)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(PgTheme.spacing.sm)) {
            if (aiPlate != null && ai.agreesWithReading.not() && !o.recognitionStatus.isUserConfirmed) {
                PgSecondaryButton(stringResource(R.string.ai_accept), onAccept)
            }
            PgTextButton(stringResource(R.string.ai_ask), onAsk)
        }
        AiStatusText(state)
    }
}

@Composable
internal fun AiStatusText(state: AiUiState) {
    val text =
        when (state) {
            AiUiState.Idle -> {
                return
            }

            AiUiState.Running -> {
                stringResource(R.string.ai_running)
            }

            is AiUiState.Finished -> {
                when (val o = state.outcome) {
                    is AiCheckOutcome.Done -> {
                        if (o.plate ==
                            null
                        ) {
                            stringResource(R.string.ai_unreadable)
                        } else {
                            stringResource(R.string.ai_done)
                        }
                    }

                    AiCheckOutcome.NoKey -> {
                        stringResource(R.string.ai_err_key_missing)
                    }

                    AiCheckOutcome.NoImage -> {
                        stringResource(R.string.ai_err_no_image)
                    }

                    AiCheckOutcome.UserConfirmed -> {
                        stringResource(R.string.ai_err_user_confirmed)
                    }

                    is AiCheckOutcome.Failed -> {
                        stringResource(aiErrorText(o.code), o.code)
                    }
                }
            }
        }
    Text(text, style = MaterialTheme.typography.bodySmall)
}

/** One-tap review for uncertain records: confirm the reading or take the learned suggestion. */
@Composable
internal fun ReviewCard(
    o: Observation,
    learned: String?,
    onConfirm: () -> Unit,
    onAcceptLearned: () -> Unit,
) {
    val needsReview = o.recognitionStatus == RecognitionStatus.NEEDS_REVIEW || o.recognitionStatus == RecognitionStatus.HIGH_CONFIDENCE
    if (!needsReview && learned == null) return
    PgCard(Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.review_title), style = MaterialTheme.typography.titleMedium)
        learned?.let {
            Text(stringResource(R.string.review_learned, it), style = PlateTextStyle, color = PgTheme.status.warning)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(PgTheme.spacing.sm)) {
            if (o.plateText != null && needsReview) PgSecondaryButton(stringResource(R.string.review_confirm), onConfirm)
            if (learned != null) PgSecondaryButton(stringResource(R.string.review_accept_learned), onAcceptLearned)
        }
    }
}

private const val HTTP_BAD_REQUEST = 400
private const val HTTP_UNAUTHORIZED = 401
private const val HTTP_FORBIDDEN = 403
private const val HTTP_NOT_FOUND = 404
private const val HTTP_TOO_MANY = 429

private fun aiErrorText(code: Int): Int =
    when (code) {
        -1 -> R.string.ai_err_offline
        HTTP_BAD_REQUEST, HTTP_UNAUTHORIZED, HTTP_FORBIDDEN -> R.string.ai_err_key
        HTTP_NOT_FOUND -> R.string.ai_err_model
        HTTP_TOO_MANY -> R.string.ai_err_quota
        else -> R.string.ai_err_other
    }
