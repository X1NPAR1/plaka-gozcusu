package com.plakagozcusu.core.designsystem.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

private val DarkColors =
    darkColorScheme(
        primary = Palette.Teal300,
        onPrimary = Palette.Teal900,
        primaryContainer = Palette.Teal700,
        onPrimaryContainer = Palette.Slate50,
        secondary = Palette.Slate300,
        onSecondary = Palette.Slate900,
        tertiary = Palette.Amber300,
        background = Palette.Slate950,
        onBackground = Palette.Slate100,
        surface = Palette.Slate900,
        onSurface = Palette.Slate100,
        surfaceVariant = Palette.Slate800,
        onSurfaceVariant = Palette.Slate300,
        surfaceContainerLowest = Palette.Slate950,
        surfaceContainerLow = Palette.Slate900,
        surfaceContainer = Palette.Slate850,
        surfaceContainerHigh = Palette.Slate800,
        surfaceContainerHighest = Palette.Slate700,
        outline = Palette.Slate500,
        outlineVariant = Palette.Slate700,
        error = Palette.Red300,
    )

private val LightColors =
    lightColorScheme(
        primary = Palette.Teal700,
        onPrimary = Palette.Slate50,
        primaryContainer = Palette.Teal300,
        onPrimaryContainer = Palette.Teal900,
        secondary = Palette.Slate500,
        tertiary = Palette.Amber700,
        background = Palette.Slate50,
        onBackground = Palette.Slate950,
        surface = Palette.Slate50,
        onSurface = Palette.Slate950,
        surfaceVariant = Palette.Slate100,
        onSurfaceVariant = Palette.Slate700,
        outline = Palette.Slate500,
        error = Palette.Red700,
    )

val LocalSpacing = staticCompositionLocalOf { Spacing() }
val LocalPgColors = staticCompositionLocalOf { DarkStatusColors }

/** App theme. Dark is the default because the app is mostly used for night-time monitoring. */
@Composable
fun PlakaGozcusuTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalSpacing provides Spacing(),
        LocalPgColors provides if (darkTheme) DarkStatusColors else LightStatusColors,
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = PgTypography,
            shapes = PgShapes,
            content = content,
        )
    }
}

/** Accessors: `PgTheme.spacing.lg`, `PgTheme.status.warning`. */
object PgTheme {
    val spacing: Spacing
        @Composable @ReadOnlyComposable
        get() = LocalSpacing.current

    val status: StatusColors
        @Composable @ReadOnlyComposable
        get() = LocalPgColors.current
}
