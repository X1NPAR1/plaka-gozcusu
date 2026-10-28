package com.plakagozcusu.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.plakagozcusu.app.navigation.PlakaGozcusuNavHost
import com.plakagozcusu.core.datastore.SettingsRepository
import com.plakagozcusu.core.designsystem.theme.PlakaGozcusuTheme
import com.plakagozcusu.core.model.AppSettings
import com.plakagozcusu.core.model.ThemeMode
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var settingsRepository: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val version = packageManager.getPackageInfo(packageName, 0).versionName ?: "?"
        setContent {
            val settings: AppSettings? by settingsRepository.settings.collectAsStateWithLifecycle(initialValue = null)
            // Keep the splash until settings are read so the start screen is decided once.
            splash.setKeepOnScreenCondition { settings == null }
            val s = settings ?: return@setContent
            val dark =
                when (s.themeMode) {
                    ThemeMode.DARK -> true
                    ThemeMode.LIGHT -> false
                    ThemeMode.SYSTEM -> isSystemInDarkTheme()
                }
            PlakaGozcusuTheme(darkTheme = dark) {
                PlakaGozcusuNavHost(onboardingDone = s.onboardingDone, appVersion = version)
            }
        }
    }
}
