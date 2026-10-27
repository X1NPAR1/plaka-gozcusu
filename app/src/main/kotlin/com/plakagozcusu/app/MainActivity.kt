package com.plakagozcusu.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.plakagozcusu.app.navigation.PlakaGozcusuNavHost
import com.plakagozcusu.core.designsystem.theme.PlakaGozcusuTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PlakaGozcusuTheme {
                PlakaGozcusuNavHost()
            }
        }
    }
}
