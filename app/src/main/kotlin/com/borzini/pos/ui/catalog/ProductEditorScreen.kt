package com.borzini.pos.ui.catalog

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
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.material3.Switch
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
import com.borzini.pos.ui.common.SimpleViewModelFactory
import com.borzini.pos.ui.common.unitLabel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductEditorScreen(navController: NavController, productId: String?) {
    val container = LocalAppContainer.current
    val viewModel: ProductEditorViewModel = viewModel(factory = SimpleViewModelFactory { ProductEditorViewModel(container, productId) })
    val state by viewModel.state.collectAsState()

    LaunchedEffect(state.saved) {
        if (state.saved) navController.popBackStack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (productId == null) "Новый товар" else "Редактирование товара") },
                navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.Filled.ArrowBack, null) } },
                actions = {
                    if (productId != null) {
                        val scope = androidx.compose.runtime.rememberCoroutineScope()
                        TextButton(onClick = {
                            scope.launch { container.catalogRepository.setProductArchived(productId, true, java.time.Instant.now().toEpochMilli()) }
                            navController.popBackStack()
                        }) { Text("В архив") }
                    }
                    TextButton(onClick = viewModel::save) { Text("Сохранить") }
                },
            )
        },
    ) { padding ->
        if (!state.loaded) {
            Column(Modifier.fillMaxSize().padding(padding), verticalArrangement = Arrangement.Center) {
                CircularProgressIndicator(Modifier.padding(32.dp))
            }
            return@Scaffold
        }

        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                OutlinedTextField(value = state.name, onValueChange = viewModel::setName, label = { Text("Название") }, modifier = Modifier.fillMaxWidth())
            }
            item {
                OutlinedTextField(value = state.description, onValueChange = viewModel::setDescription, label = { Text("Состав/описание") }, modifier = Modifier.fillMaxWidth())
            }
            item {
                var expanded by remember { mutableStateOf(false) }
                val selectedName = state.categories.find { it.id == state.categoryId }?.name ?: "Выберите категорию"
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                    OutlinedTextField(
                        value = selectedName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Категория") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(),
                    )
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        state.categories.forEach { category ->
                            DropdownMenuItem(text = { Text(category.name) }, onClick = { viewModel.setCategoryId(category.id); expanded = false })
                        }
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Простой товар без рецептуры (например, бутылка воды)")
                    Switch(checked = state.isSimpleProduct, onCheckedChange = viewModel::setSimpleProduct)
                }
            }
            if (state.isSimpleProduct) {
                item {
                    var expanded by remember { mutableStateOf(false) }
                    val selectedName = state.inventoryItems.find { it.id == state.simpleInventoryItemId }?.name ?: "Выберите позицию склада"
                    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                        OutlinedTextField(
                            value = selectedName, onValueChange = {}, readOnly = true, label = { Text("Складская позиция") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                            modifier = Modifier.fillMaxWidth().menuAnchor(),
                        )
                        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            state.inventoryItems.forEach { item ->
                                DropdownMenuItem(text = { Text(item.name) }, onClick = { viewModel.setSimpleInventoryItemId(item.id); expanded = false })
                            }
                        }
                    }
                }
            } else {
                item { Text("Варианты (размеры) и рецептура", style = MaterialTheme.typography.titleMedium) }
                items(state.variants, key = { it.key }) { variant ->
                    VariantEditorCard(
                        variant = variant,
                        inventoryItems = state.inventoryItems,
                        cost = state.costFor(variant),
                        canRemove = state.variants.size > 1,
                        onNameChange = { viewModel.updateVariant(variant.key, name = it) },
                        onPriceChange = { viewModel.updateVariant(variant.key, price = it) },
                        onRemoveVariant = { viewModel.removeVariant(variant.key) },
                        onAddRecipeLine = { viewModel.addRecipeLine(variant.key) },
                        onRemoveRecipeLine = { lineKey -> viewModel.removeRecipeLine(variant.key, lineKey) },
                        onRecipeItemChange = { lineKey, itemId -> viewModel.updateRecipeLine(variant.key, lineKey, inventoryItemId = itemId) },
                        onRecipeQuantityChange = { lineKey, qty -> viewModel.updateRecipeLine(variant.key, lineKey, quantity = qty) },
                    )
                }
                item {
                    TextButton(onClick = viewModel::addVariant) { Text("+ Добавить размер/вариант") }
                }
            }
            state.errorMessage?.let { message ->
                item { Text(message, color = MaterialTheme.colorScheme.error) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VariantEditorCard(
    variant: VariantEdit,
    inventoryItems: List<com.borzini.pos.data.db.entities.InventoryItemEntity>,
    cost: com.borzini.pos.core.Money,
    canRemove: Boolean,
    onNameChange: (String) -> Unit,
    onPriceChange: (String) -> Unit,
    onRemoveVariant: () -> Unit,
    onAddRecipeLine: () -> Unit,
    onRemoveRecipeLine: (String) -> Unit,
    onRecipeItemChange: (String, String) -> Unit,
    onRecipeQuantityChange: (String, String) -> Unit,
) {
    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                OutlinedTextField(value = variant.name, onValueChange = onNameChange, label = { Text("Размер (например, 400 мл)") }, modifier = Modifier.weight(1f))
                if (canRemove) {
                    IconButton(onClick = onRemoveVariant) { Icon(Icons.Filled.Delete, contentDescription = "Удалить вариант") }
                }
            }
            OutlinedTextField(value = variant.priceText, onValueChange = onPriceChange, label = { Text("Цена продажи, ₽") }, modifier = Modifier.fillMaxWidth())

            variant.recipeLines.forEach { line ->
                RecipeLineRow(line, inventoryItems, onRecipeItemChange, onRecipeQuantityChange, onRemoveRecipeLine)
            }
            TextButton(onClick = onAddRecipeLine) { Text("+ Ингредиент") }

            Text("Себестоимость порции: ${cost.format()}", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecipeLineRow(
    line: RecipeLineEdit,
    inventoryItems: List<com.borzini.pos.data.db.entities.InventoryItemEntity>,
    onItemChange: (String, String) -> Unit,
    onQuantityChange: (String, String) -> Unit,
    onRemove: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = inventoryItems.find { it.id == line.inventoryItemId }
    Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = Modifier.weight(2f)) {
            OutlinedTextField(
                value = selected?.name ?: "Выберите ингредиент",
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                modifier = Modifier.fillMaxWidth().menuAnchor(),
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                inventoryItems.forEach { item ->
                    DropdownMenuItem(text = { Text("${item.name} (${unitLabel(item.baseUnit)})") }, onClick = { onItemChange(line.key, item.id); expanded = false })
                }
            }
        }
        OutlinedTextField(
            value = line.quantityText,
            onValueChange = { onQuantityChange(line.key, it) },
            label = { Text(selected?.let { unitLabel(it.baseUnit) } ?: "кол-во") },
            modifier = Modifier.weight(1f).padding(start = 8.dp),
        )
        IconButton(onClick = { onRemove(line.key) }) { Icon(Icons.Filled.Delete, contentDescription = "Удалить ингредиент") }
    }
}
