package com.borzini.pos.ui.warehouse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.borzini.pos.data.db.entities.InventoryItemEntity
import com.borzini.pos.di.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class WarehouseUiState(
    val items: List<InventoryItemEntity> = emptyList(),
    val searchQuery: String = "",
    val lowStockOnly: Boolean = false,
)

class WarehouseViewModel(container: AppContainer) : ViewModel() {
    private val searchQuery = MutableStateFlow("")
    private val lowStockOnly = MutableStateFlow(false)

    val uiState: StateFlow<WarehouseUiState> = combine(
        container.inventoryRepository.observeActiveItems(),
        searchQuery,
        lowStockOnly,
    ) { items, query, lowOnly ->
        var filtered = items
        if (query.isNotBlank()) filtered = filtered.filter { it.name.contains(query, ignoreCase = true) }
        if (lowOnly) filtered = filtered.filter { it.onHandAmount <= it.minAllowedAmount }
        WarehouseUiState(filtered, query, lowOnly)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), WarehouseUiState())

    fun setSearchQuery(query: String) { searchQuery.value = query }
    fun setLowStockOnly(value: Boolean) { lowStockOnly.value = value }
}
