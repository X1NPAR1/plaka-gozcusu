package com.plakagozcusu.core.designsystem.component

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.plakagozcusu.core.designsystem.theme.Motion
import com.plakagozcusu.core.designsystem.theme.PgTheme

private const val PULSE_MIN_ALPHA = 0.35f
private const val PILL_BACKGROUND_ALPHA = 0.14f

/**
 * Status chip: colored dot + text label. Meaning is always carried by [label] (never color-only).
 * [pulse] animates the dot for the "live" state; the system "remove animations" setting scales it away.
 */
@Composable
fun PgStatusPill(
    label: String,
    tone: PillTone,
    modifier: Modifier = Modifier,
    pulse: Boolean = false,
) {
    val color = tone.color()
    val dotAlpha =
        if (pulse) {
            val transition = rememberInfiniteTransition(label = "pill-pulse")
            val alpha by transition.animateFloat(
                initialValue = 1f,
                targetValue = PULSE_MIN_ALPHA,
                animationSpec = infiniteRepeatable(tween(Motion.PULSE_MS / 2), RepeatMode.Reverse),
                label = "pill-pulse-alpha",
            )
            alpha
        } else {
            1f
        }
    Row(
        modifier =
            modifier
                .clip(MaterialTheme.shapes.small)
                .background(color.copy(alpha = PILL_BACKGROUND_ALPHA))
                .padding(horizontal = PgTheme.spacing.md, vertical = PgTheme.spacing.xs)
                .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PgTheme.spacing.sm),
    ) {
        Box(
            Modifier
                .size(8.dp)
                .alpha(dotAlpha)
                .clip(CircleShape)
                .background(color),
        )
        Text(label, style = MaterialTheme.typography.labelLarge, color = color)
    }
}

@Composable
private fun PillTone.color(): Color =
    when (this) {
        PillTone.LIVE -> PgTheme.status.live
        PillTone.SUCCESS -> PgTheme.status.success
        PillTone.WARNING -> PgTheme.status.warning
        PillTone.DANGER -> PgTheme.status.danger
        PillTone.NEUTRAL -> PgTheme.status.neutral
    }
