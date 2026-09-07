package com.borzini.pos.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import com.borzini.pos.core.BusinessCalendar
import com.borzini.pos.core.Money
import com.borzini.pos.data.prefs.AppSettings
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneId

@Composable
fun GoalsScreen(navController: NavController) {
    val container = LocalAppContainer.current
    val settings by container.settingsDataStore.settingsFlow.collectAsState(initial = AppSettings())
    val zone = ZoneId.of(settings.timezoneId)
    val today = BusinessCalendar.today(zone)
    val dailyKey = today.toString()
    val monthlyKey = today.toString().substring(0, 7)

    val dailyGoal by container.goalRepository.observeDailyGoal(dailyKey).collectAsState(initial = null)
    val monthlyGoal by container.goalRepository.observeMonthlyGoal(monthlyKey).collectAsState(initial = null)
    val scope = rememberCoroutineScope()

    var dailyText by remember(dailyGoal) { mutableStateOf(dailyGoal?.let { Money.ofKopecks(it.targetKopecks).rubles.toPlainString() } ?: "") }
    var monthlyText by remember(monthlyGoal) { mutableStateOf(monthlyGoal?.let { Money.ofKopecks(it.targetKopecks).rubles.toPlainString() } ?: "") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Цели по выручке") },
                navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.Filled.ArrowBack, null) } },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Цель на сегодня ($dailyKey)", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(value = dailyText, onValueChange = { dailyText = it }, label = { Text("Целевая выручка, ₽") }, modifier = Modifier.fillMaxWidth())
            Button(onClick = {
                val amount = runCatching { BigDecimal(dailyText.replace(",", ".")) }.getOrNull() ?: return@Button
                scope.launch { container.goalRepository.setGoal("DAILY", dailyKey, Money.fromRubles(amount), Instant.now().toEpochMilli()) }
            }) { Text("Сохранить дневную цель") }

            Text("Цель на месяц ($monthlyKey)", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(value = monthlyText, onValueChange = { monthlyText = it }, label = { Text("Целевая выручка, ₽") }, modifier = Modifier.fillMaxWidth())
            Button(onClick = {
                val amount = runCatching { BigDecimal(monthlyText.replace(",", ".")) }.getOrNull() ?: return@Button
                scope.launch { container.goalRepository.setGoal("MONTHLY", monthlyKey, Money.fromRubles(amount), Instant.now().toEpochMilli()) }
            }) { Text("Сохранить месячную цель") }
        }
    }
}
