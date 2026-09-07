package com.borzini.pos.ui.receipts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.borzini.pos.core.ReturnStockPolicy
import com.borzini.pos.data.db.entities.SaleEntity
import com.borzini.pos.data.db.entities.SaleItemEntity
import com.borzini.pos.data.repository.ProcessReturnResult
import com.borzini.pos.data.repository.ReturnLineRequest
import com.borzini.pos.di.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.Instant

data class ReceiptDetailState(
    val sale: SaleEntity? = null,
    val items: List<SaleItemEntity> = emptyList(),
    val returnMessage: String? = null,
)

class ReceiptDetailViewModel(private val container: AppContainer, private val saleId: String) : ViewModel() {
    private val _state = MutableStateFlow(ReceiptDetailState())
    val state: StateFlow<ReceiptDetailState> = _state

    init {
        viewModelScope.launch {
            val sale = container.saleRepository.getSale(saleId)
            val items = container.saleRepository.getItemsForSale(saleId)
            _state.value = ReceiptDetailState(sale, items)
        }
    }

    fun submitReturn(quantities: Map<String, Int>, reason: String, restockIngredients: Boolean) {
        viewModelScope.launch {
            val lines = quantities.filterValues { it > 0 }.map { (saleItemId, qty) -> ReturnLineRequest(saleItemId, qty) }
            if (lines.isEmpty()) return@launch
            val isDemo = state.value.sale?.isDemo ?: false
            val policy = if (restockIngredients) ReturnStockPolicy.RESTOCK_INGREDIENTS else ReturnStockPolicy.DO_NOT_RESTOCK_INGREDIENTS
            val result = container.returnRepository.processReturn(saleId, lines, reason, policy, Instant.now().toEpochMilli(), isDemo)
            val message = when (result) {
                is ProcessReturnResult.Ok -> "Возврат оформлен: ${result.refunded.format()}"
                is ProcessReturnResult.ExceedsSoldQuantity -> "Нельзя вернуть больше, чем было продано (максимум ${result.maxAllowed})"
                ProcessReturnResult.NothingToReturn -> "Не выбрано ни одной позиции для возврата"
            }
            val sale = container.saleRepository.getSale(saleId)
            val items = container.saleRepository.getItemsForSale(saleId)
            _state.value = ReceiptDetailState(sale, items, message)
        }
    }

    fun clearMessage() {
        _state.value = _state.value.copy(returnMessage = null)
    }
}
