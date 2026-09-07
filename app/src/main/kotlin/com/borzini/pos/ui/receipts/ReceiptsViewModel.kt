package com.borzini.pos.ui.receipts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.borzini.pos.core.BusinessCalendar
import com.borzini.pos.core.StatsPeriodPreset
import com.borzini.pos.data.db.entities.SaleEntity
import com.borzini.pos.di.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import java.time.ZoneId

data class ReceiptsUiState(
    val sales: List<SaleEntity> = emptyList(),
    val query: String = "",
    val paymentFilter: String? = null,
)

class ReceiptsViewModel(container: AppContainer) : ViewModel() {
    private val query = MutableStateFlow("")
    private val paymentFilter = MutableStateFlow<String?>(null)

    val uiState: StateFlow<ReceiptsUiState> = container.settingsDataStore.settingsFlow.flatMapLatest { settings ->
        val z = ZoneId.of(settings.timezoneId)
        val range = BusinessCalendar.resolve(StatsPeriodPreset.THIS_MONTH, z, Instant.now())
        combine(
            container.saleRepository.observeSalesInRange(range.startInclusive.toEpochMilli(), range.endExclusive.toEpochMilli(), settings.demoModeEnabled),
            query,
            paymentFilter,
        ) { sales, q, filter ->
            var filtered = sales
            if (q.isNotBlank()) filtered = filtered.filter { it.receiptNumber.toString().contains(q) || it.id.contains(q) }
            if (filter != null) filtered = filtered.filter { it.paymentMethod == filter }
            ReceiptsUiState(filtered, q, filter)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ReceiptsUiState())

    fun setQuery(v: String) { query.value = v }
    fun setPaymentFilter(v: String?) { paymentFilter.value = v }
}
