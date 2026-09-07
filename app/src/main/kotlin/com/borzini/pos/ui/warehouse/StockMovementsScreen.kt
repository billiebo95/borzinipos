@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.borzini.pos.ui.warehouse

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavController
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.produceState
import com.borzini.pos.LocalAppContainer
import com.borzini.pos.core.StockMovementReason
import com.borzini.pos.ui.common.formatDateTime
import java.time.ZoneId
import androidx.compose.ui.unit.dp

private fun reasonLabel(reason: String): String = when (reason) {
    StockMovementReason.PURCHASE_RECEIPT.name -> "Поступление (закупка)"
    StockMovementReason.SALE_DEDUCTION.name -> "Списание по продаже"
    StockMovementReason.MANUAL_WRITE_OFF.name -> "Ручное списание"
    StockMovementReason.INVENTORY_ADJUSTMENT.name -> "Инвентаризация"
    StockMovementReason.RETURN_RESTOCK.name -> "Возврат на склад"
    StockMovementReason.CORRECTION.name -> "Корректировка"
    else -> reason
}

@Composable
fun StockMovementsScreen(navController: NavController, itemId: String) {
    val container = LocalAppContainer.current
    val movements by container.inventoryRepository.observeMovementsForItem(itemId).collectAsState(initial = emptyList())
    val settings by container.settingsDataStore.settingsFlow.collectAsState(initial = com.borzini.pos.data.prefs.AppSettings())
    val zone = ZoneId.of(settings.timezoneId)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("История движений") },
                navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.Filled.ArrowBack, null) } },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            items(movements, key = { it.id }) { movement ->
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text(reasonLabel(movement.reason), style = MaterialTheme.typography.bodyLarge)
                        Text(formatDateTime(movement.createdAt, zone), style = MaterialTheme.typography.bodySmall)
                        movement.note?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    }
                    val delta = movement.quantityDelta
                    val sign = if (delta.signum() >= 0) "+" else ""
                    Text(
                        "$sign${delta.stripTrailingZeros().toPlainString()}",
                        color = if (delta.signum() >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                HorizontalDivider()
            }
        }
    }
}
