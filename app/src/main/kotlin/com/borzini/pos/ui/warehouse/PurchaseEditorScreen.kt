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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.compose.runtime.collectAsState
import com.borzini.pos.LocalAppContainer
import com.borzini.pos.ui.common.SimpleViewModelFactory
import com.borzini.pos.ui.common.unitLabel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PurchaseEditorScreen(navController: NavController, purchaseId: String?) {
    val container = LocalAppContainer.current
    val viewModel: PurchaseEditorViewModel = viewModel(factory = SimpleViewModelFactory { PurchaseEditorViewModel(container, purchaseId) })
    val state by viewModel.state.collectAsState()

    LaunchedEffect(state.saved) { if (state.saved) navController.popBackStack() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (purchaseId == null) "Новая закупка" else "Закупка") },
                navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.Filled.ArrowBack, null) } },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            OutlinedTextField(value = state.supplier, onValueChange = viewModel::setSupplier, label = { Text("Поставщик") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = state.invoiceNumber, onValueChange = viewModel::setInvoiceNumber, label = { Text("Номер накладной (необязательно)") }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
            OutlinedTextField(value = state.comment, onValueChange = viewModel::setComment, label = { Text("Комментарий") }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))

            Text("Позиции", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp))
            LazyColumn(Modifier.weight(1f)) {
                items(state.lines, key = { it.key }) { line ->
                    PurchaseLineCard(line, state.inventoryItems, viewModel)
                }
                item { TextButton(onClick = viewModel::addLine) { Text("+ Строка закупки") } }
            }

            Text("Итого: ${state.total.format()}", style = MaterialTheme.typography.titleLarge)
            if (state.isPosted) {
                Text("Закупка уже проведена — остатки и себестоимость изменены, редактирование недоступно.", style = MaterialTheme.typography.bodySmall)
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                    Button(onClick = viewModel::saveDraft) { Text("Сохранить черновик") }
                    Button(onClick = viewModel::saveAndPost) { Text("Провести закупку") }
                }
            }
            state.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PurchaseLineCard(line: PurchaseLineEdit, items: List<com.borzini.pos.data.db.entities.InventoryItemEntity>, viewModel: PurchaseEditorViewModel) {
    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(Modifier.padding(12.dp)) {
            var expanded by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
            val selected = items.find { it.id == line.inventoryItemId }
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = Modifier.weight(1f)) {
                    OutlinedTextField(
                        value = selected?.name ?: "Выберите позицию", onValueChange = {}, readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(),
                    )
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        items.forEach { item ->
                            DropdownMenuItem(text = { Text(item.name) }, onClick = { viewModel.updateLine(line.key, itemId = item.id); expanded = false })
                        }
                    }
                }
                IconButton(onClick = { viewModel.removeLine(line.key) }) { Icon(Icons.Filled.Delete, contentDescription = "Удалить строку") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = line.packageCountText, onValueChange = { viewModel.updateLine(line.key, packageCount = it) },
                    label = { Text("Кол-во упаковок") }, modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = line.unitsPerPackageText, onValueChange = { viewModel.updateLine(line.key, unitsPerPackage = it) },
                    label = { Text("Ед. в упаковке (${selected?.let { unitLabel(it.baseUnit) } ?: "ед."})") }, modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = line.packageCostText, onValueChange = { viewModel.updateLine(line.key, packageCost = it) },
                    label = { Text("Цена упаковки, ₽") }, modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
