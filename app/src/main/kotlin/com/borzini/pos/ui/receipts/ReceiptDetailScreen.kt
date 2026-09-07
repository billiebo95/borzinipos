@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.borzini.pos.ui.receipts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.compose.runtime.collectAsState
import com.borzini.pos.LocalAppContainer
import com.borzini.pos.core.Money
import com.borzini.pos.ui.common.SimpleViewModelFactory
import androidx.compose.ui.unit.dp

@Composable
fun ReceiptDetailScreen(navController: NavController, saleId: String) {
    val container = LocalAppContainer.current
    val viewModel: ReceiptDetailViewModel = viewModel(factory = SimpleViewModelFactory { ReceiptDetailViewModel(container, saleId) })
    val state by viewModel.state.collectAsState()
    var showReturnDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Чек №${state.sale?.receiptNumber ?: ""}") },
                navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.Filled.ArrowBack, null) } },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            val sale = state.sale
            if (sale != null) {
                Text(if (sale.paymentMethod == "CASH") "Наличные" else "Безналичные", style = MaterialTheme.typography.bodyMedium)
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                state.items.forEach { item ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text("${item.productNameSnapshot}${item.variantNameSnapshot?.let { " ($it)" } ?: ""} × ${item.quantity}")
                            if (item.returnedQuantity > 0) Text("Возвращено: ${item.returnedQuantity}", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        }
                        Text(Money.ofKopecks(item.lineTotalKopecks).format())
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Себестоимость")
                    Text(Money.ofKopecks(sale.cogsKopecks).format())
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Итого", style = MaterialTheme.typography.titleMedium)
                    Text(Money.ofKopecks(sale.totalKopecks).format(), style = MaterialTheme.typography.titleLarge)
                }
                androidx.compose.foundation.layout.Spacer(Modifier.padding(top = 16.dp))
                Button(onClick = { showReturnDialog = true }) { Text("Оформить возврат") }
            } else {
                Text("Загрузка…")
            }
            state.returnMessage?.let {
                Text(it, modifier = Modifier.padding(top = 8.dp))
            }
        }
    }

    if (showReturnDialog) {
        ReturnDialog(
            items = state.items,
            onDismiss = { showReturnDialog = false },
            onConfirm = { quantities, reason, restock ->
                viewModel.submitReturn(quantities, reason, restock)
                showReturnDialog = false
            },
        )
    }
}

@Composable
private fun ReturnDialog(
    items: List<com.borzini.pos.data.db.entities.SaleItemEntity>,
    onDismiss: () -> Unit,
    onConfirm: (Map<String, Int>, String, Boolean) -> Unit,
) {
    val quantities = remember { androidx.compose.runtime.mutableStateMapOf<String, Int>().apply { items.forEach { put(it.id, 0) } } }
    var reason by remember { mutableStateOf("") }
    var restock by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Возврат") },
        text = {
            Column {
                items.forEach { item ->
                    val maxReturnable = item.quantity - item.returnedQuantity
                    if (maxReturnable > 0) {
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Text(item.productNameSnapshot, modifier = Modifier.weight(1f))
                            IconButton(onClick = { quantities[item.id] = (quantities[item.id] ?: 0).let { if (it > 0) it - 1 else 0 } }) {
                                Icon(Icons.Filled.Remove, contentDescription = null)
                            }
                            Text("${quantities[item.id] ?: 0}")
                            IconButton(onClick = { quantities[item.id] = ((quantities[item.id] ?: 0) + 1).coerceAtMost(maxReturnable) }) {
                                Icon(Icons.Filled.Add, contentDescription = null)
                            }
                        }
                    }
                }
                OutlinedTextField(value = reason, onValueChange = { reason = it }, label = { Text("Причина возврата") })
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text("Вернуть ингредиенты на склад")
                    Switch(checked = restock, onCheckedChange = { restock = it })
                }
                Text(
                    "По умолчанию ингредиенты приготовленного напитка на склад не возвращаются.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(quantities.toMap(), reason.ifBlank { "Не указана" }, restock) }) { Text("Оформить возврат") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}
