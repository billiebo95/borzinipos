package com.borzini.pos.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.compose.runtime.collectAsState
import com.borzini.pos.LocalAppContainer
import com.borzini.pos.data.prefs.AppSettings
import com.borzini.pos.data.prefs.ThemeMode
import com.borzini.pos.ui.theme.parseAccentColor
import kotlinx.coroutines.launch

private val accentOptions = listOf(
    "FF6F4E37" to "Кофейный",
    "FF8D6E63" to "Капучино",
    "FF4E342E" to "Эспрессо",
    "FF2E7D32" to "Мятный",
    "FFB71C1C" to "Гранатовый",
    "FF1565C0" to "Синий",
)

@Composable
fun AppearanceScreen(navController: NavController) {
    val container = LocalAppContainer.current
    val settings by container.settingsDataStore.settingsFlow.collectAsState(initial = AppSettings())
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Оформление") },
                navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.Filled.ArrowBack, null) } },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Тема", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = settings.themeMode == ThemeMode.LIGHT, onClick = { scope.launch { container.settingsDataStore.setThemeMode(ThemeMode.LIGHT) } }, label = { Text("Светлая") })
                FilterChip(selected = settings.themeMode == ThemeMode.DARK, onClick = { scope.launch { container.settingsDataStore.setThemeMode(ThemeMode.DARK) } }, label = { Text("Тёмная") })
                FilterChip(selected = settings.themeMode == ThemeMode.SYSTEM, onClick = { scope.launch { container.settingsDataStore.setThemeMode(ThemeMode.SYSTEM) } }, label = { Text("Системная") })
            }

            Text("Акцентный цвет", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                accentOptions.forEach { (hex, name) ->
                    val color = parseAccentColor(hex)
                    val selected = settings.accentColorHex == hex
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(color)
                                .clickable { scope.launch { container.settingsDataStore.setAccentColorHex(hex) } },
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            if (selected) {
                                Icon(Icons.Filled.Check, contentDescription = name, tint = Color.White, modifier = Modifier.align(Alignment.CenterVertically).size(20.dp))
                            }
                        }
                    }
                }
            }

            Text("Размер текста: ${(settings.textScale * 100).toInt()}%", style = MaterialTheme.typography.titleMedium)
            Slider(
                value = settings.textScale,
                onValueChange = { value -> scope.launch { container.settingsDataStore.setTextScale(value) } },
                valueRange = 0.85f..1.3f,
            )
        }
    }
}
