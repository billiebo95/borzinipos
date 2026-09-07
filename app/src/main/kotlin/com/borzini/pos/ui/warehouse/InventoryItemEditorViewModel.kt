package com.borzini.pos.ui.warehouse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.borzini.pos.core.BaseUnit
import com.borzini.pos.core.Quantity
import com.borzini.pos.di.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.Instant

data class InventoryItemEditState(
    val loaded: Boolean = false,
    val itemId: String? = null,
    val name: String = "",
    val category: String = "",
    val baseUnit: BaseUnit = BaseUnit.PIECE,
    val minAllowedText: String = "0",
    val supplier: String = "",
    val onHand: BigDecimal = BigDecimal.ZERO,
    val avgCost: BigDecimal = BigDecimal.ZERO,
    val saved: Boolean = false,
    val errorMessage: String? = null,
)

class InventoryItemEditorViewModel(private val container: AppContainer, private val itemId: String?) : ViewModel() {
    private val _state = MutableStateFlow(InventoryItemEditState(itemId = itemId))
    val state: StateFlow<InventoryItemEditState> = _state

    init {
        viewModelScope.launch {
            if (itemId != null) {
                val item = container.inventoryRepository.observeItem(itemId).first()
                if (item != null) {
                    _state.value = InventoryItemEditState(
                        loaded = true, itemId = itemId, name = item.name, category = item.category,
                        baseUnit = BaseUnit.valueOf(item.baseUnit), minAllowedText = item.minAllowedAmount.toPlainString(),
                        supplier = item.supplier ?: "", onHand = item.onHandAmount, avgCost = item.avgUnitCostRubles,
                    )
                    return@launch
                }
            }
            _state.value = _state.value.copy(loaded = true)
        }
    }

    fun setName(v: String) { _state.value = _state.value.copy(name = v) }
    fun setCategory(v: String) { _state.value = _state.value.copy(category = v) }
    fun setBaseUnit(v: BaseUnit) { _state.value = _state.value.copy(baseUnit = v) }
    fun setMinAllowed(v: String) { _state.value = _state.value.copy(minAllowedText = v) }
    fun setSupplier(v: String) { _state.value = _state.value.copy(supplier = v) }

    fun save() {
        val s = _state.value
        val minAllowed = runCatching { BigDecimal(s.minAllowedText.replace(",", ".")) }.getOrNull()
        if (s.name.isBlank() || s.category.isBlank() || minAllowed == null) {
            _state.value = s.copy(errorMessage = "Заполните название, категорию и минимальный остаток")
            return
        }
        viewModelScope.launch {
            container.inventoryRepository.saveItem(
                id = s.itemId, name = s.name, category = s.category, baseUnit = s.baseUnit,
                minAllowed = Quantity(minAllowed), supplier = s.supplier.ifBlank { null }, now = Instant.now().toEpochMilli(),
            )
            _state.value = _state.value.copy(saved = true)
        }
    }
}
