package com.plakagozcusu.app.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.ManageSearch
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.plakagozcusu.app.R
import com.plakagozcusu.core.designsystem.component.PgScreen
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
    val icon: ImageVector,
)

private val TABS =
    listOf(
        Tab(DashboardRoute, R.string.tab_home, Icons.Filled.Home),
        Tab(RecordsRoute, R.string.tab_records, Icons.AutoMirrored.Filled.List),
        Tab(IncidentRoute, R.string.tab_incident, Icons.AutoMirrored.Filled.ManageSearch),
        Tab(SettingsRoute, R.string.tab_settings, Icons.Filled.Settings),
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
                            icon = { Icon(tab.icon, contentDescription = null) },
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
        tabScreens(nav, appVersion, navVm)
        composable<DetailRoute> {
            PgScreen(
                title = stringResource(R.string.detail_title),
                onBack = { nav.popBackStack() },
                backDescription = stringResource(R.string.back),
            ) { inner ->
                DetailScreen(modifier = inner, onDeleted = { nav.popBackStack() }, appVersion = appVersion)
            }
        }
    }
}

private fun NavGraphBuilder.tabScreens(
    nav: NavHostController,
    appVersion: String,
    navVm: AppNavViewModel,
) {
    composable<RecordsRoute> {
        PgScreen(
            title = stringResource(R.string.tab_records),
        ) { inner ->
            RecordsScreen(
                modifier = inner,
                onOpen = { nav.navigate(DetailRoute(it)) },
                appVersion = appVersion,
            )
        }
    }
    composable<IncidentRoute> {
        PgScreen(
            title = stringResource(R.string.tab_incident),
        ) { inner ->
            IncidentScreen(
                modifier = inner,
                onOpen = { nav.navigate(DetailRoute(it)) },
                appVersion = appVersion,
            )
        }
    }
    composable<SettingsRoute> {
        PgScreen(
            title = stringResource(R.string.tab_settings),
        ) { inner ->
            SettingsScreen(modifier = inner, onRecalibrate = {
                navVm.releaseCameraForCalibration()
                nav.navigate(CalibrationRoute)
            }, appVersion = appVersion, modelVersions = MODEL_VERSIONS_LABEL)
        }
    }
}
