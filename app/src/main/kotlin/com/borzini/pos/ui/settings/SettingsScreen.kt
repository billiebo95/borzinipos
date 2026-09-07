@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.borzini.pos.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import androidx.compose.runtime.collectAsState
import com.borzini.pos.LocalAppContainer
import com.borzini.pos.data.prefs.AppSettings
import com.borzini.pos.ui.Routes
import kotlinx.coroutines.launch

private data class SettingsRow(val title: String, val subtitle: String, val route: String)

private val rows = listOf(
    SettingsRow("Товары", "Меню, цены, рецептуры", "product_editor"),
    SettingsRow("Категории", "Порядок, добавление, архив", Routes.CATEGORY_MANAGER),
    SettingsRow("Архив", "Скрытые товары и складские позиции", Routes.ARCHIVE),
    SettingsRow("Цели", "Целевая выручка на день и месяц", Routes.GOALS),
    SettingsRow("Расходы", "Аренда, зарплата, налоги и другое", Routes.EXPENSES),
    SettingsRow("Оформление", "Тема, акцентный цвет, размер текста", Routes.APPEARANCE),
    SettingsRow("Параметры кофейни и правила остатков", "Часовой пояс, отрицательные остатки", Routes.COFFEE_SHOP_SETTINGS),
    SettingsRow("Google-аккаунт и синхронизация", "Вход, папка Drive, таблица Sheets", Routes.GOOGLE_SYNC),
    SettingsRow("Резервное копирование и восстановление", "Экспорт данных, CSV", Routes.BACKUP),
)

@Composable
fun SettingsScreen(navController: NavController) {
    val container = LocalAppContainer.current
    val settings by container.settingsDataStore.settingsFlow.collectAsState(initial = AppSettings())
    val scope = rememberCoroutineScope()

    Scaffold(topBar = { TopAppBar(title = { Text("Настройки") }) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            items(rows) { row ->
                ListItem(
                    headlineContent = { Text(row.title) },
                    supportingContent = { Text(row.subtitle) },
                    trailingContent = { Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth().clickable { navController.navigate(row.route) },
                )
                HorizontalDivider()
            }
            item {
                ListItem(
                    headlineContent = { Text("Демо-режим: ${if (settings.demoModeEnabled) "включён" else "выключен"}") },
                    supportingContent = { Text("Демонстрационные товары и продажи не попадают в реальные отчёты") },
                    trailingContent = {
                        Switch(
                            checked = settings.demoModeEnabled,
                            onCheckedChange = { checked ->
                                scope.launch {
                                    container.settingsDataStore.setDemoModeEnabled(checked)
                                    if (checked) container.demoDataSeeder.seedIfNeeded()
                                }
                            },
                        )
                    },
                )
            }
        }
    }
}
