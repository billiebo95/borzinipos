@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.borzini.pos.ui.settings

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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.borzini.pos.core.ExpenseCategory
import com.borzini.pos.core.Money
import com.borzini.pos.core.StatsPeriodPreset
import com.borzini.pos.data.prefs.AppSettings
import com.borzini.pos.ui.common.formatDate
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneId
import androidx.compose.ui.unit.dp

private fun categoryLabel(category: ExpenseCategory): String = when (category) {
    ExpenseCategory.RENT -> "Аренда"
    ExpenseCategory.SALARY -> "Зарплата"
    ExpenseCategory.UTILITIES -> "Коммунальные услуги"
    ExpenseCategory.ACQUIRING_FEE -> "Комиссия эквайринга"
    ExpenseCategory.TAXES -> "Налоги"
    ExpenseCategory.OTHER -> "Прочее"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpensesScreen(navController: NavController) {
    val container = LocalAppContainer.current
    val settings by container.settingsDataStore.settingsFlow.collectAsState(initial = AppSettings())
    val zone = ZoneId.of(settings.timezoneId)
    val range = BusinessCalendar.resolve(StatsPeriodPreset.THIS_MONTH, zone, Instant.now())
    val expenses by container.expenseRepository.observeInRange(range.startInclusive.toEpochMilli(), range.endExclusive.toEpochMilli(), settings.demoModeEnabled)
        .collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var showAdd by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Расходы (текущий месяц)") },
                navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.Filled.ArrowBack, null) } },
            )
        },
        floatingActionButton = { FloatingActionButton(onClick = { showAdd = true }) { Icon(Icons.Filled.Add, contentDescription = "Добавить расход") } },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            items(expenses, key = { it.id }) { expense ->
                Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text(categoryLabel(runCatching { ExpenseCategory.valueOf(expense.category) }.getOrDefault(ExpenseCategory.OTHER)), style = MaterialTheme.typography.titleMedium)
                            Text(formatDate(expense.date, zone), style = MaterialTheme.typography.bodySmall)
                            expense.comment?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                        }
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Text(Money.ofKopecks(expense.amountKopecks).format(), style = MaterialTheme.typography.titleMedium)
                            IconButton(onClick = { scope.launch { container.expenseRepository.delete(expense.id, Instant.now().toEpochMilli()) } }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Удалить")
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAdd) {
        AddExpenseDialog(
            onDismiss = { showAdd = false },
            onConfirm = { category, amount, comment ->
                scope.launch {
                    container.expenseRepository.save(null, Instant.now().toEpochMilli(), category, amount, comment, Instant.now().toEpochMilli())
                }
                showAdd = false
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddExpenseDialog(onDismiss: () -> Unit, onConfirm: (ExpenseCategory, Money, String?) -> Unit) {
    var category by remember { mutableStateOf(ExpenseCategory.RENT) }
    var amountText by remember { mutableStateOf("") }
    var comment by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Новый расход") },
        text = {
            Column {
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                    OutlinedTextField(
                        value = categoryLabel(category), onValueChange = {}, readOnly = true, label = { Text("Категория") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(),
                    )
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        ExpenseCategory.values().forEach { c ->
                            DropdownMenuItem(text = { Text(categoryLabel(c)) }, onClick = { category = c; expanded = false })
                        }
                    }
                }
                OutlinedTextField(value = amountText, onValueChange = { amountText = it }, label = { Text("Сумма, ₽") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = comment, onValueChange = { comment = it }, label = { Text("Комментарий") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val amount = runCatching { BigDecimal(amountText.replace(",", ".")) }.getOrNull() ?: return@TextButton
                onConfirm(category, Money.fromRubles(amount), comment.ifBlank { null })
            }) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}
