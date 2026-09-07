package com.borzini.pos.ui.pos

import com.borzini.pos.core.CheckoutDraft
import com.borzini.pos.core.Money
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class CartUiLine(
    val lineId: String,
    val productId: String?,
    val productName: String,
    val variantId: String?,
    val variantName: String?,
    val unitPrice: Money,
    val quantity: Int,
    val isSimpleProduct: Boolean,
    val simpleInventoryItemId: String?,
) {
    val lineTotal: Money get() = unitPrice * quantity
}

/**
 * In-memory current order, shared between the POS screen and the checkout screen. Deliberately
 * NOT persisted to disk: an in-progress, unpaid cart is allowed to be lost if the app is killed
 * (the barista just re-taps the drinks) - what must never be lost or duplicated is a sale that
 * already completed, which is why the durable idempotency key ([draft]) is created once per
 * checkout attempt and reused across retries (see core.CheckoutDraft / SaleRepository).
 */
class CartHolder {
    private val _lines = MutableStateFlow<List<CartUiLine>>(emptyList())
    val lines: StateFlow<List<CartUiLine>> = _lines

    val draft = CheckoutDraft.new()

    fun addOrIncrement(newLine: CartUiLine) {
        val current = _lines.value
        val existingIndex = current.indexOfFirst { it.productId == newLine.productId && it.variantId == newLine.variantId }
        _lines.value = if (existingIndex >= 0) {
            current.mapIndexed { i, l -> if (i == existingIndex) l.copy(quantity = l.quantity + 1) else l }
        } else {
            current + newLine
        }
    }

    fun setQuantity(lineId: String, quantity: Int) {
        _lines.value = if (quantity <= 0) {
            _lines.value.filterNot { it.lineId == lineId }
        } else {
            _lines.value.map { if (it.lineId == lineId) it.copy(quantity = quantity) else it }
        }
    }

    fun removeLine(lineId: String) {
        _lines.value = _lines.value.filterNot { it.lineId == lineId }
    }

    fun clear() {
        _lines.value = emptyList()
        draft.reset()
    }

    fun itemCount(): Int = _lines.value.sumOf { it.quantity }
    fun total(): Money = Money.sum(_lines.value.map { it.lineTotal })
}
