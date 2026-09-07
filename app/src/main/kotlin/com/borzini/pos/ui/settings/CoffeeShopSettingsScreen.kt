@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.borzini.pos.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import androidx.compose.runtime.collectAsState
import com.borzini.pos.LocalAppContainer
import com.borzini.pos.data.prefs.AppSettings
import kotlinx.coroutines.launch
import androidx.compose.ui.unit.dp

private val commonTimezones = listOf(
    "Europe/Kaliningrad", "Europe/Moscow", "Europe/Samara", "Asia/Yekaterinburg", "Asia/Omsk",
    "Asia/Novosibirsk", "Asia/Krasnoyarsk", "Asia/Irkutsk", "Asia/Yakutsk", "Asia/Vladivostok",
    "Asia/Magadan", "Asia/Kamchatka",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoffeeShopSettingsScreen(navController: NavController) {
    val container = LocalAppContainer.current
    val settings by container.settingsDataStore.settingsFlow.collectAsState(initial = AppSettings())
    val scope = rememberCoroutineScope()
    var shopName by remember(settings.coffeeShopName) { mutableStateOf(settings.coffeeShopName) }
    var feePercentText by remember(settings.autoAcquiringFeePercent) { mutableStateOf(settings.autoAcquiringFeePercent.toString()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Параметры кофейни") },
                navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.Filled.ArrowBack, null) } },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            OutlinedTextField(
                value = shopName,
                onValueChange = { shopName = it },
                label = { Text("Название кофейни") },
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = {},
            )
            androidx.compose.material3.Button(onClick = { scope.launch { container.settingsDataStore.setCoffeeShopName(shopName) } }) { Text("Сохранить название") }

            Text("Часовой пояс", style = MaterialTheme.typography.titleMedium)
            var expanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                OutlinedTextField(
                    value = settings.timezoneId, onValueChange = {}, readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(),
                )
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    commonTimezones.forEach { tz ->
                        DropdownMenuItem(text = { Text(tz) }, onClick = { scope.launch { container.settingsDataStore.setTimezoneId(tz) }; expanded = false })
                    }
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text("Разрешить отрицательные остатки", style = MaterialTheme.typography.titleMedium)
                    Text("Если выключено, продажа блокируется при нехватке ингредиентов", style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = settings.negativeStockAllowed, onCheckedChange = { scope.launch { container.settingsDataStore.setNegativeStockAllowed(it) } })
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Синхронизировать в мобильном интернете (не только Wi-Fi)")
                Switch(checked = settings.syncOverMeteredAllowed, onCheckedChange = { scope.launch { container.settingsDataStore.setSyncOverMeteredAllowed(it) } })
            }

            Text("Комиссия эквайринга", style = MaterialTheme.typography.titleMedium)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Считать автоматически от безналичной выручки")
                Switch(
                    checked = settings.autoAcquiringFeeEnabled,
                    onCheckedChange = { checked -> scope.launch { container.settingsDataStore.setAutoAcquiringFee(checked, settings.autoAcquiringFeePercent) } },
                )
            }
            if (settings.autoAcquiringFeeEnabled) {
                OutlinedTextField(
                    value = feePercentText,
                    onValueChange = { feePercentText = it },
                    label = { Text("Процент комиссии") },
                    modifier = Modifier.fillMaxWidth(),
                )
                androidx.compose.material3.Button(onClick = {
                    val percent = feePercentText.replace(",", ".").toFloatOrNull() ?: return@Button
                    scope.launch { container.settingsDataStore.setAutoAcquiringFee(true, percent) }
                }) { Text("Сохранить процент") }
                Text(
                    "Если комиссия считается автоматически, не вносите её ещё раз вручную в расходах — иначе она будет учтена дважды.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}
