package com.plakagozcusu.app.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.plakagozcusu.app.R
import com.plakagozcusu.core.designsystem.theme.Motion
import com.plakagozcusu.feature.monitor.ui.DashboardScreen
import com.plakagozcusu.feature.onboarding.OnboardingScreen
import com.plakagozcusu.feature.onboarding.calibration.CalibrationScreen
import com.plakagozcusu.feature.records.ui.DetailScreen
import com.plakagozcusu.feature.records.ui.IncidentScreen
import com.plakagozcusu.feature.records.ui.RecordsScreen
import com.plakagozcusu.feature.settings.SettingsScreen
import kotlinx.serialization.Serializable

@Serializable data object OnboardingRoute

@Serializable data object CalibrationRoute

@Serializable data object DashboardRoute

@Serializable data object RecordsRoute

@Serializable data object IncidentRoute

@Serializable data object SettingsRoute

@Serializable data class DetailRoute(
    val id: String,
)

private data class Tab(
    val route: Any,
    val label: Int,
    val glyph: String,
)

// Text glyphs keep the app free of an icon dependency; each tab also has a visible label.
private val TABS =
    listOf(
        Tab(DashboardRoute, R.string.tab_home, "◉"),
        Tab(RecordsRoute, R.string.tab_records, "≡"),
        Tab(IncidentRoute, R.string.tab_incident, "⌕"),
        Tab(SettingsRoute, R.string.tab_settings, "⚙"),
    )

const val MODEL_VERSIONS_LABEL = "EfficientDet-Lite0 int8 · ML Kit Text 16.0.1"

@Composable
fun PlakaGozcusuNavHost(
    onboardingDone: Boolean,
    appVersion: String,
    modifier: Modifier = Modifier,
) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val showTabs = TABS.any { tab -> backStack?.destination?.hasRoute(tab.route::class) == true }
    Scaffold(
        modifier = modifier,
        bottomBar = {
            if (showTabs) {
                NavigationBar {
                    TABS.forEach { tab ->
                        NavigationBarItem(
                            selected = backStack?.destination?.hasRoute(tab.route::class) == true,
                            onClick = {
                                nav.navigate(tab.route) {
                                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Text(tab.glyph, style = MaterialTheme.typography.titleLarge) },
                            label = { Text(stringResource(tab.label)) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            AppNavGraph(nav, onboardingDone, appVersion)
        }
    }
}

@Composable
private fun AppNavGraph(
    nav: NavHostController,
    onboardingDone: Boolean,
    appVersion: String,
    navVm: AppNavViewModel = hiltViewModel(),
) {
    NavHost(
        navController = nav,
        startDestination = if (onboardingDone) DashboardRoute else OnboardingRoute,
        enterTransition = { fadeIn(tween(Motion.MEDIUM_MS)) },
        exitTransition = { fadeOut(tween(Motion.SHORT_MS)) },
    ) {
        composable<OnboardingRoute> {
            OnboardingScreen(onFinished = {
                navVm.releaseCameraForCalibration()
                nav.navigate(CalibrationRoute) {
                    popUpTo(OnboardingRoute) {
                        inclusive =
                            true
                    }
                }
            })
        }
        composable<CalibrationRoute> {
            CalibrationScreen(onDone = {
                if (!nav.popBackStack()) nav.navigate(DashboardRoute)
            })
        }
        composable<DashboardRoute> {
            DashboardScreen(
                onOpenRecord = { nav.navigate(DetailRoute(it)) },
                onCalibrate = {
                    navVm.releaseCameraForCalibration()
                    nav.navigate(CalibrationRoute)
                },
            )
        }
        composable<RecordsRoute> {
            RecordsScreen(
                onOpen = { nav.navigate(DetailRoute(it)) },
                appVersion = appVersion,
            )
        }
        composable<IncidentRoute> {
            IncidentScreen(
                onOpen = { nav.navigate(DetailRoute(it)) },
                appVersion = appVersion,
            )
        }
        composable<SettingsRoute> {
            SettingsScreen(onRecalibrate = {
                navVm.releaseCameraForCalibration()
                nav.navigate(CalibrationRoute)
            }, appVersion = appVersion, modelVersions = MODEL_VERSIONS_LABEL)
        }
        composable<DetailRoute> { DetailScreen(onDeleted = { nav.popBackStack() }, appVersion = appVersion) }
    }
}
