package com.plakagozcusu.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// System sans keeps the app fully offline (no downloadable fonts). Plate text uses monospace so
// similar glyphs (0/O, 1/I, 8/B) are easier to tell apart during manual review.
private val Base = Typography()

internal val PgTypography =
    Typography(
        displaySmall = Base.displaySmall.copy(fontWeight = FontWeight.SemiBold),
        headlineMedium = Base.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
        headlineSmall = Base.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = Base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = Base.titleMedium.copy(fontWeight = FontWeight.Medium),
        labelLarge = Base.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    )

val PlateTextStyle =
    TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        letterSpacing = 2.sp,
    )
