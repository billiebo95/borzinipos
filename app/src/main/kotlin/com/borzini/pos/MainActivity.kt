package com.borzini.pos

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import com.borzini.pos.di.AppContainer
import com.borzini.pos.data.prefs.AppSettings
import com.borzini.pos.ui.BorziniNavHost
import com.borzini.pos.ui.theme.BorziniTheme

val LocalAppContainer = compositionLocalOf<AppContainer> { error("AppContainer not provided") }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as BorziniApplication).container
        setContent {
            val settings by container.settingsDataStore.settingsFlow.collectAsState(initial = AppSettings())
            CompositionLocalProvider(LocalAppContainer provides container) {
                BorziniTheme(
                    themeMode = settings.themeMode,
                    accentColorHex = settings.accentColorHex,
                    textScale = settings.textScale,
                ) {
                    BorziniNavHost()
                }
            }
        }
    }
}
