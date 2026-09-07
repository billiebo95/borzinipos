package com.borzini.pos.ui.warehouse

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
import androidx.compose.material3.ExposedDropdownMenu
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.compose.runtime.collectAsState
import com.borzini.pos.LocalAppContainer
import com.borzini.pos.core.BaseUnit
import com.borzini.pos.ui.common.SimpleViewModelFactory
import com.borzini.pos.ui.common.unitLabel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventoryItemEditorScreen(navController: NavController, itemId: String?) {
    val container = LocalAppContainer.current
    val viewModel: InventoryItemEditorViewModel = viewModel(factory = SimpleViewModelFactory { InventoryItemEditorViewModel(container, itemId) })
    val state by viewModel.state.collectAsState()

    LaunchedEffect(state.saved) { if (state.saved) navController.popBackStack() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (itemId == null) "Новая позиция склада" else "Складская позиция") },
                navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.Filled.ArrowBack, null) } },
                actions = {
                    if (itemId != null) {
                        val scope = androidx.compose.runtime.rememberCoroutineScope()
                        TextButton(onClick = {
                            scope.launch { container.inventoryRepository.setArchived(itemId, true, java.time.Instant.now().toEpochMilli()) }
                            navController.popBackStack()
                        }) { Text("В архив") }
                    }
                    TextButton(onClick = viewModel::save) { Text("Сохранить") }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(value = state.name, onValueChange = viewModel::setName, label = { Text("Название") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = state.category, onValueChange = viewModel::setCategory, label = { Text("Категория (например, Упаковка)") }, modifier = Modifier.fillMaxWidth())

            var expanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                OutlinedTextField(
                    value = unitLabel(state.baseUnit.name), onValueChange = {}, readOnly = true, label = { Text("Единица измерения") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(),
                )
                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    BaseUnit.values().forEach { unit ->
                        DropdownMenuItem(text = { Text(unitLabel(unit.name)) }, onClick = { viewModel.setBaseUnit(unit); expanded = false })
                    }
                }
            }

            OutlinedTextField(value = state.minAllowedText, onValueChange = viewModel::setMinAllowed, label = { Text("Минимально допустимый остаток") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = state.supplier, onValueChange = viewModel::setSupplier, label = { Text("Поставщик (необязательно)") }, modifier = Modifier.fillMaxWidth())

            if (itemId != null) {
                Text("Текущий остаток: ${state.onHand.stripTrailingZeros().toPlainString()} ${unitLabel(state.baseUnit.name)}", style = MaterialTheme.typography.bodyMedium)
                Text("Средняя себестоимость: ${state.avgCost.stripTrailingZeros().toPlainString()} ₽/ед.", style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { navController.navigate("stock_movements/$itemId") }) { Text("История движений") }
                    TextButton(onClick = { navController.navigate("inventory_count/$itemId") }) { Text("Инвентаризация") }
                }
            }

            state.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
}
