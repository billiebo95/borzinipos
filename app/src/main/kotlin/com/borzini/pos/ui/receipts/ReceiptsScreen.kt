package com.borzini.pos.ui.receipts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.compose.runtime.collectAsState
import com.borzini.pos.LocalAppContainer
import com.borzini.pos.core.Money
import com.borzini.pos.data.prefs.AppSettings
import com.borzini.pos.ui.common.SimpleViewModelFactory
import com.borzini.pos.ui.common.formatDateTime
import java.time.ZoneId

@Composable
fun ReceiptsScreen(navController: NavController) {
    val container = LocalAppContainer.current
    val viewModel: ReceiptsViewModel = viewModel(factory = SimpleViewModelFactory { ReceiptsViewModel(container) })
    val state by viewModel.uiState.collectAsState()
    val settings by container.settingsDataStore.settingsFlow.collectAsState(initial = AppSettings())
    val zone = ZoneId.of(settings.timezoneId)

    Scaffold(topBar = { TopAppBar(title = { Text("Чеки (текущий месяц)") }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            OutlinedTextField(
                value = state.query, onValueChange = viewModel::setQuery,
                label = { Text("Поиск по номеру чека") }, modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                FilterChip(selected = state.paymentFilter == null, onClick = { viewModel.setPaymentFilter(null) }, label = { Text("Все") })
                FilterChip(selected = state.paymentFilter == "CASH", onClick = { viewModel.setPaymentFilter("CASH") }, label = { Text("Наличные") })
                FilterChip(selected = state.paymentFilter == "CASHLESS", onClick = { viewModel.setPaymentFilter("CASHLESS") }, label = { Text("Безналичные") })
            }
            LazyColumn {
                items(state.sales, key = { it.id }) { sale ->
                    Card(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        onClick = { navController.navigate("receipt_detail/${sale.id}") },
                    ) {
                        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text("Чек №${sale.receiptNumber}", style = MaterialTheme.typography.titleMedium)
                                Text(formatDateTime(sale.createdAt, zone), style = MaterialTheme.typography.bodySmall)
                            }
                            Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
                                Text(Money.ofKopecks(sale.totalKopecks).format(), style = MaterialTheme.typography.titleMedium)
                                if (sale.isFullyReturned) Text("Возврат", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                                else if (sale.isPartiallyReturned) Text("Частичный возврат", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }
}
