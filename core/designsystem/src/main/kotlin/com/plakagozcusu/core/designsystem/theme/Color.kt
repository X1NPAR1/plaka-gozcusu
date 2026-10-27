package com.plakagozcusu.core.designsystem.theme

import androidx.compose.ui.graphics.Color

// Brand palette: deep night slate + "signal" teal accent, amber for warnings.
// Chosen for long night-time monitoring sessions (low glare) and AA contrast on dark surfaces.
internal object Palette {
    val Slate950 = Color(0xFF0B1016)
    val Slate900 = Color(0xFF111821)
    val Slate850 = Color(0xFF16202B)
    val Slate800 = Color(0xFF1C2834)
    val Slate700 = Color(0xFF2A3947)
    val Slate500 = Color(0xFF5B6B7A)
    val Slate300 = Color(0xFFA9B6C2)
    val Slate100 = Color(0xFFE3E9EF)
    val Slate50 = Color(0xFFF5F7FA)

    val Teal300 = Color(0xFF5FE0CD)
    val Teal500 = Color(0xFF14B8A6)
    val Teal700 = Color(0xFF0F766E)
    val Teal900 = Color(0xFF053B37)

    val Amber300 = Color(0xFFFFCF70)
    val Amber700 = Color(0xFFB45309)
    val Red300 = Color(0xFFFF8A80)
    val Red700 = Color(0xFFB3261E)
    val Green300 = Color(0xFF7EE2A0)
    val Green700 = Color(0xFF1B7F3B)
}

/** Semantic status colors beyond Material roles. Always paired with text/icon, never color-only. */
data class StatusColors(
    val live: Color,
    val success: Color,
    val warning: Color,
    val danger: Color,
    val neutral: Color,
)

internal val DarkStatusColors =
    StatusColors(
        live = Palette.Teal300,
        success = Palette.Green300,
        warning = Palette.Amber300,
        danger = Palette.Red300,
        neutral = Palette.Slate300,
    )

internal val LightStatusColors =
    StatusColors(
        live = Palette.Teal700,
        success = Palette.Green700,
        warning = Palette.Amber700,
        danger = Palette.Red700,
        neutral = Palette.Slate500,
    )
