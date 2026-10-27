package com.plakagozcusu.core.designsystem.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** 4dp-based spacing scale. */
@Immutable
data class Spacing(
    val xxs: Dp = 2.dp,
    val xs: Dp = 4.dp,
    val sm: Dp = 8.dp,
    val md: Dp = 12.dp,
    val lg: Dp = 16.dp,
    val xl: Dp = 24.dp,
    val xxl: Dp = 32.dp,
    val xxxl: Dp = 48.dp,
)

/** Minimum touch target per Material accessibility guidance. */
val MinTouchTarget: Dp = 48.dp

internal val PgShapes =
    Shapes(
        extraSmall = RoundedCornerShape(6.dp),
        small = RoundedCornerShape(10.dp),
        medium = RoundedCornerShape(16.dp),
        large = RoundedCornerShape(24.dp),
        extraLarge = RoundedCornerShape(32.dp),
    )

/** Motion tokens (durations in ms). UI animation must never run on the analysis thread. */
object Motion {
    const val SHORT_MS = 150
    const val MEDIUM_MS = 280
    const val LONG_MS = 450
    const val PULSE_MS = 1_600

    val EmphasizedEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val StandardEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
}
