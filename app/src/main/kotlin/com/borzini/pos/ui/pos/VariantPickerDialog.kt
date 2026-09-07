package com.borzini.pos.ui.pos

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.borzini.pos.core.Money
import com.borzini.pos.data.db.entities.ProductVariantEntity

@Composable
fun VariantPickerDialog(card: ProductCardUi, onDismiss: () -> Unit, onPick: (ProductVariantEntity) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Выберите размер: ${card.product.name}") },
        text = {
            androidx.compose.foundation.layout.Column {
                card.variants.forEach { variant ->
                    Button(
                        onClick = { onPick(variant) },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    ) {
                        Text("${variant.name ?: "Стандарт"} — ${Money.ofKopecks(variant.priceKopecks).format()}")
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}
