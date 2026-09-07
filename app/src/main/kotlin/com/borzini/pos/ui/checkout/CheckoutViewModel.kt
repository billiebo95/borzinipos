package com.borzini.pos.ui.checkout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.borzini.pos.core.CashPaymentCalculator
import com.borzini.pos.core.Money
import com.borzini.pos.core.NegativeStockPolicy
import com.borzini.pos.core.PaymentMethod
import com.borzini.pos.data.repository.CartLine
import com.borzini.pos.data.repository.CompleteSaleResult
import com.borzini.pos.di.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.Instant

sealed class CheckoutEvent {
    data class Completed(val saleId: String, val change: Money) : CheckoutEvent()
    data class Failed(val message: String) : CheckoutEvent()
}

data class CheckoutUiState(
    val total: Money = Money.ZERO,
    val paymentMethod: PaymentMethod = PaymentMethod.CASH,
    val cashReceivedText: String = "",
    val isSubmitting: Boolean = false,
) {
    val cashReceived: Money?
        get() = cashReceivedText.trim().takeIf { it.isNotEmpty() }
            ?.let { runCatching { Money.fromRubles(BigDecimal(it.replace(",", "."))) }.getOrNull() }

    val change: Money?
        get() = cashReceived?.let { received ->
            (CashPaymentCalculator.evaluate(total, received) as? com.borzini.pos.core.CashPaymentResult.Ok)?.change
        }

    val canComplete: Boolean
        get() = when (paymentMethod) {
            PaymentMethod.CASH -> cashReceived?.let { it >= total } ?: false
            PaymentMethod.CASHLESS -> true
        }
}

class CheckoutViewModel(private val container: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(CheckoutUiState(total = container.cartHolder.total()))
    val state: StateFlow<CheckoutUiState> = _state

    private val _events = kotlinx.coroutines.flow.MutableSharedFlow<CheckoutEvent>()
    val events = _events

    fun setPaymentMethod(method: PaymentMethod) {
        _state.value = _state.value.copy(paymentMethod = method, cashReceivedText = "")
    }

    fun setCashReceivedText(text: String) {
        _state.value = _state.value.copy(cashReceivedText = text)
    }

    fun setExactAmount() {
        _state.value = _state.value.copy(cashReceivedText = _state.value.total.rubles.toPlainString())
    }

    fun addQuickAmount(amount: Money) {
        _state.value = _state.value.copy(cashReceivedText = amount.rubles.toPlainString())
    }

    fun submit() {
        val current = _state.value
        if (!current.canComplete || current.isSubmitting) return
        viewModelScope.launch {
            _state.value = current.copy(isSubmitting = true)
            val cartLines = container.cartHolder.lines.first().map { line ->
                CartLine(
                    productId = line.productId,
                    productName = line.productName,
                    variantId = line.variantId,
                    variantName = line.variantName,
                    unitPrice = line.unitPrice,
                    quantity = line.quantity,
                    isSimpleProduct = line.isSimpleProduct,
                    simpleInventoryItemId = line.simpleInventoryItemId,
                )
            }
            val settings = container.settingsDataStore.settingsFlow.first()
            val policy = if (settings.negativeStockAllowed) NegativeStockPolicy.ALLOW_NEGATIVE else NegativeStockPolicy.BLOCK_SALE
            val result = container.saleRepository.completeSale(
                idempotencyKey = container.cartHolder.draft.keyFor().value,
                lines = cartLines,
                paymentMethod = current.paymentMethod,
                cashReceived = if (current.paymentMethod == PaymentMethod.CASH) current.cashReceived else null,
                negativeStockPolicy = policy,
                now = Instant.now().toEpochMilli(),
                isDemo = settings.demoModeEnabled,
            )
            _state.value = _state.value.copy(isSubmitting = false)
            when (result) {
                is CompleteSaleResult.Success -> {
                    container.cartHolder.clear()
                    _events.emit(CheckoutEvent.Completed(result.sale.id, result.change))
                }
                is CompleteSaleResult.InsufficientStock -> {
                    val names = result.shortages.joinToString(", ") { it.ingredientName }
                    _events.emit(CheckoutEvent.Failed("Недостаточно на складе: $names"))
                }
                is CompleteSaleResult.InsufficientCash -> {
                    _events.emit(CheckoutEvent.Failed("Недостаточно наличных: не хватает ${result.shortfall.format()}"))
                }
            }
        }
    }
}
