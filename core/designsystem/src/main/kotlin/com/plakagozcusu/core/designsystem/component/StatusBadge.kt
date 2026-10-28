package com.plakagozcusu.core.designsystem.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.plakagozcusu.core.designsystem.R
import com.plakagozcusu.core.model.RecognitionStatus

/** Turkish label for a recognition status; always text, colour is secondary. */
fun RecognitionStatus.labelRes(): Int =
    when (this) {
        RecognitionStatus.PENDING -> R.string.status_pending
        RecognitionStatus.HIGH_CONFIDENCE -> R.string.status_high_confidence
        RecognitionStatus.NEEDS_REVIEW -> R.string.status_needs_review
        RecognitionStatus.UNREADABLE -> R.string.status_unreadable
        RecognitionStatus.NO_PLATE_VISIBLE -> R.string.status_no_plate
        RecognitionStatus.FAILED -> R.string.status_failed
        RecognitionStatus.USER_VERIFIED -> R.string.status_user_verified
        RecognitionStatus.USER_CORRECTED -> R.string.status_user_corrected
    }

fun RecognitionStatus.tone(): PillTone =
    when (this) {
        RecognitionStatus.HIGH_CONFIDENCE, RecognitionStatus.USER_VERIFIED, RecognitionStatus.USER_CORRECTED -> PillTone.SUCCESS
        RecognitionStatus.NEEDS_REVIEW, RecognitionStatus.PENDING -> PillTone.WARNING
        RecognitionStatus.FAILED -> PillTone.DANGER
        RecognitionStatus.UNREADABLE, RecognitionStatus.NO_PLATE_VISIBLE -> PillTone.NEUTRAL
    }

@Composable
fun StatusBadge(
    status: RecognitionStatus,
    modifier: Modifier = Modifier,
) {
    PgStatusPill(label = stringResource(status.labelRes()), tone = status.tone(), modifier = modifier)
}
