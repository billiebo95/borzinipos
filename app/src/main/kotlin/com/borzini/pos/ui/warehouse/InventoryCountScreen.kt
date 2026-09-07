package com.borzini.pos.ui.warehouse

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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.compose.runtime.collectAsState
import com.borzini.pos.LocalAppContainer
import com.borzini.pos.core.Quantity
import com.borzini.pos.data.prefs.AppSettings
import com.borzini.pos.ui.common.unitLabel
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.Instant

@Composable
fun InventoryCountScreen(navController: NavController, itemId: String) {
    val container = LocalAppContainer.current
    val item by container.inventoryRepository.observeItem(itemId).collectAsState(initial = null)
    var countedText by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Инвентаризация") },
                navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.Filled.ArrowBack, null) } },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val current = item
            if (current != null) {
                Text("${current.name}: числится ${current.onHandAmount.stripTrailingZeros().toPlainString()} ${unitLabel(current.baseUnit)}")
                OutlinedTextField(
                    value = countedText, onValueChange = { countedText = it },
                    label = { Text("Фактическое количество") }, modifier = Modifier.fillMaxWidth(),
                )
                val counted = countedText.replace(",", ".").toBigDecimalOrNull()
                if (counted != null) {
                    val diff = counted.subtract(current.onHandAmount)
                    Text("Разница: ${if (diff.signum() >= 0) "+" else ""}${diff.stripTrailingZeros().toPlainString()} ${unitLabel(current.baseUnit)}")
                }
                OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("Комментарий (необязательно)") }, modifier = Modifier.fillMaxWidth())
                Button(
                    onClick = {
                        val counted2 = countedText.replace(",", ".").toBigDecimalOrNull() ?: return@Button
                        scope.launch {
                            container.inventoryRepository.performInventoryCount(itemId, Quantity(counted2), note.ifBlank { null }, Instant.now().toEpochMilli())
                            navController.popBackStack()
                        }
                    },
                    enabled = counted != null,
                ) { Text("Сохранить инвентаризацию") }
            } else {
                Text("Загрузка…")
            }
        }
    }
}

private fun String.toBigDecimalOrNull(): BigDecimal? = runCatching { BigDecimal(this) }.getOrNull()
