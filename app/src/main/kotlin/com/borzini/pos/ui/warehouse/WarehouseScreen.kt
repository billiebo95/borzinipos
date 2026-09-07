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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.compose.runtime.collectAsState
import com.borzini.pos.LocalAppContainer
import com.borzini.pos.ui.Routes
import com.borzini.pos.ui.common.SimpleViewModelFactory
import com.borzini.pos.ui.common.formatQuantity
import com.borzini.pos.ui.common.unitLabel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WarehouseScreen(navController: NavController) {
    val container = LocalAppContainer.current
    val viewModel: WarehouseViewModel = viewModel(factory = SimpleViewModelFactory { WarehouseViewModel(container) })
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = { TopAppBar(title = { Text("Склад") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { navController.navigate("inventory_item_editor") }) { Icon(Icons.Filled.Add, contentDescription = "Новая позиция") }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                androidx.compose.material3.Button(onClick = { navController.navigate(Routes.PURCHASE_LIST) }) {
                    Icon(Icons.Filled.Receipt, contentDescription = null)
                    Text(" Закупки")
                }
            }
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = viewModel::setSearchQuery,
                placeholder = { Text("Поиск по названию") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                singleLine = true,
            )
            FilterChip(selected = state.lowStockOnly, onClick = { viewModel.setLowStockOnly(!state.lowStockOnly) }, label = { Text("Только низкий остаток") })

            LazyColumn(Modifier.padding(top = 8.dp)) {
                items(state.items, key = { it.id }) { item ->
                    Card(
                        onClick = { navController.navigate("inventory_item_editor?itemId=${item.id}") },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    ) {
                        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text(item.name, style = MaterialTheme.typography.titleMedium)
                                Text(item.category, style = MaterialTheme.typography.bodySmall)
                            }
                            Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
                                val isLow = item.onHandAmount <= item.minAllowedAmount
                                Text(
                                    "${item.onHandAmount.formatQuantity()} ${unitLabel(item.baseUnit)}",
                                    color = if (isLow) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                Text("~${item.avgUnitCostRubles.formatQuantity()} ₽/ед.", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }
}
