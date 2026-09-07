package com.borzini.pos.ui.pos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.borzini.pos.core.Money

@Composable
fun CartPanel(
    modifier: Modifier,
    cart: List<CartUiLine>,
    cartTotal: Money,
    onIncrement: (CartUiLine) -> Unit,
    onDecrement: (CartUiLine) -> Unit,
    onRemove: (CartUiLine) -> Unit,
    onClear: () -> Unit,
    onCheckout: () -> Unit,
) {
    var confirmClear by remember { mutableStateOf(false) }

    Column(modifier.padding(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Текущий заказ", style = MaterialTheme.typography.titleMedium)
            if (cart.isNotEmpty()) {
                TextButton(onClick = { confirmClear = true }) { Text("Очистить") }
            }
        }
        HorizontalDivider()
        if (cart.isEmpty()) {
            Text(
                "Добавьте товары из меню",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(vertical = 24.dp),
            )
        } else {
            LazyColumn(Modifier.weight(1f)) {
                items(cart, key = { it.lineId }) { line ->
                    CartLineRow(line, onIncrement, onDecrement, onRemove)
                    HorizontalDivider()
                }
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Итого", style = MaterialTheme.typography.titleMedium)
                Text(cartTotal.format(), style = MaterialTheme.typography.titleLarge)
            }
            Button(onClick = onCheckout, modifier = Modifier.fillMaxWidth()) {
                Text("Перейти к оплате")
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Очистить заказ?") },
            text = { Text("Все добавленные товары будут удалены из текущего заказа.") },
            confirmButton = {
                TextButton(onClick = { onClear(); confirmClear = false }) { Text("Очистить") }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text("Отмена") }
            },
        )
    }
}

@Composable
private fun CartLineRow(
    line: CartUiLine,
    onIncrement: (CartUiLine) -> Unit,
    onDecrement: (CartUiLine) -> Unit,
    onRemove: (CartUiLine) -> Unit,
) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(line.productName + (line.variantName?.let { " ($it)" } ?: ""), style = MaterialTheme.typography.bodyLarge)
            Text(line.unitPrice.format(), style = MaterialTheme.typography.bodySmall)
        }
        IconButton(onClick = { onDecrement(line) }) { Icon(Icons.Filled.Remove, contentDescription = "Уменьшить") }
        Text("${line.quantity}", style = MaterialTheme.typography.titleMedium)
        IconButton(onClick = { onIncrement(line) }) { Icon(Icons.Filled.Add, contentDescription = "Увеличить") }
        IconButton(onClick = { onRemove(line) }) { Icon(Icons.Filled.Delete, contentDescription = "Удалить") }
    }
}
