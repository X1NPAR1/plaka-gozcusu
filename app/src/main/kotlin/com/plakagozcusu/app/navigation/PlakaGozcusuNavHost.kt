package com.plakagozcusu.app.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.plakagozcusu.app.home.HomeScreen
import com.plakagozcusu.core.designsystem.theme.Motion
import com.plakagozcusu.feature.onboarding.OnboardingScreen
import com.plakagozcusu.feature.onboarding.calibration.CalibrationScreen
import kotlinx.serialization.Serializable

@Serializable
data object OnboardingRoute

@Serializable
data object CalibrationRoute

@Serializable
data object HomeRoute

/**
 * Top-level navigation. The start destination is always onboarding until the "onboarding done"
 * flag is persisted in DataStore (Phase 4); monitor/records/incident/detail/settings routes are
 * added by their phases.
 */
@Composable
fun PlakaGozcusuNavHost(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = OnboardingRoute,
        modifier = modifier,
        enterTransition = { fadeIn(tween(Motion.MEDIUM_MS)) },
        exitTransition = { fadeOut(tween(Motion.SHORT_MS)) },
    ) {
        composable<OnboardingRoute> {
            OnboardingScreen(
                onFinished = {
                    navController.navigate(CalibrationRoute) {
                        popUpTo(OnboardingRoute) { inclusive = true }
                    }
                },
            )
        }
        composable<CalibrationRoute> {
            CalibrationScreen(
                onDone = {
                    navController.navigate(HomeRoute) {
                        popUpTo(CalibrationRoute) { inclusive = true }
                    }
                },
            )
        }
        composable<HomeRoute> {
            HomeScreen(
                onRestartOnboarding = {
                    navController.navigate(OnboardingRoute) {
                        popUpTo(HomeRoute) { inclusive = true }
                    }
                },
            )
        }
    }
}
