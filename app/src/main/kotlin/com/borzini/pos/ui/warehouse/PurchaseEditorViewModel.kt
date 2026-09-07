package com.borzini.pos.ui.warehouse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.borzini.pos.core.Money
import com.borzini.pos.core.Quantity
import com.borzini.pos.data.db.entities.InventoryItemEntity
import com.borzini.pos.data.repository.PurchaseLineInput
import com.borzini.pos.di.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class PurchaseLineEdit(
    val key: String = UUID.randomUUID().toString(),
    val inventoryItemId: String? = null,
    val packageCountText: String = "1",
    val unitsPerPackageText: String = "",
    val packageCostText: String = "",
)

data class PurchaseEditState(
    val loaded: Boolean = false,
    val purchaseId: String? = null,
    val isPosted: Boolean = false,
    val supplier: String = "",
    val invoiceNumber: String = "",
    val comment: String = "",
    val lines: List<PurchaseLineEdit> = listOf(PurchaseLineEdit()),
    val inventoryItems: List<InventoryItemEntity> = emptyList(),
    val saved: Boolean = false,
    val posted: Boolean = false,
    val errorMessage: String? = null,
) {
    val total: Money
        get() = Money.sum(
            lines.mapNotNull { l ->
                val count = l.packageCountText.toIntOrNull() ?: return@mapNotNull null
                val cost = l.packageCostText.toBigDecimalOrNullSafe() ?: return@mapNotNull null
                Money.fromRubles(cost) * count
            },
        )
}

private fun String.toBigDecimalOrNullSafe(): BigDecimal? = runCatching { BigDecimal(replace(",", ".")) }.getOrNull()

class PurchaseEditorViewModel(private val container: AppContainer, private val purchaseId: String?) : ViewModel() {
    private val _state = MutableStateFlow(PurchaseEditState(purchaseId = purchaseId))
    val state: StateFlow<PurchaseEditState> = _state

    init {
        viewModelScope.launch {
            val items = container.inventoryRepository.observeActiveItems().first()
            var s = _state.value.copy(loaded = true, inventoryItems = items)
            if (purchaseId != null) {
                val purchase = container.purchaseRepository.observeAll().first().find { it.id == purchaseId }
                if (purchase != null) {
                    val lines = container.purchaseRepository.observeLines(purchaseId).first()
                    s = s.copy(
                        supplier = purchase.supplier,
                        invoiceNumber = purchase.invoiceNumber ?: "",
                        comment = purchase.comment ?: "",
                        isPosted = purchase.isPosted,
                        lines = lines.map {
                            PurchaseLineEdit(
                                inventoryItemId = it.inventoryItemId,
                                packageCountText = it.packageCount.toString(),
                                unitsPerPackageText = it.unitsPerPackage.toPlainString(),
                                packageCostText = Money.ofKopecks(it.packageCostKopecks).rubles.toPlainString(),
                            )
                        }.ifEmpty { listOf(PurchaseLineEdit()) },
                    )
                }
            }
            _state.value = s
        }
    }

    fun setSupplier(v: String) { _state.value = _state.value.copy(supplier = v) }
    fun setInvoiceNumber(v: String) { _state.value = _state.value.copy(invoiceNumber = v) }
    fun setComment(v: String) { _state.value = _state.value.copy(comment = v) }
    fun addLine() { _state.value = _state.value.copy(lines = _state.value.lines + PurchaseLineEdit()) }
    fun removeLine(key: String) { _state.value = _state.value.copy(lines = _state.value.lines.filterNot { it.key == key }) }
    fun updateLine(key: String, itemId: String? = null, packageCount: String? = null, unitsPerPackage: String? = null, packageCost: String? = null) {
        _state.value = _state.value.copy(
            lines = _state.value.lines.map {
                if (it.key != key) it else it.copy(
                    inventoryItemId = itemId ?: it.inventoryItemId,
                    packageCountText = packageCount ?: it.packageCountText,
                    unitsPerPackageText = unitsPerPackage ?: it.unitsPerPackageText,
                    packageCostText = packageCost ?: it.packageCostText,
                )
            },
        )
    }

    private fun buildLineInputs(): List<PurchaseLineInput>? {
        val s = _state.value
        val inputs = s.lines.mapNotNull { l ->
            val itemId = l.inventoryItemId ?: return@mapNotNull null
            val count = l.packageCountText.toIntOrNull() ?: return null
            val units = l.unitsPerPackageText.toBigDecimalOrNullSafe() ?: return null
            val cost = l.packageCostText.toBigDecimalOrNullSafe() ?: return null
            PurchaseLineInput(itemId, count, Quantity(units), Money.fromRubles(cost))
        }
        return if (inputs.isEmpty()) null else inputs
    }

    fun saveDraft() {
        val s = _state.value
        if (s.supplier.isBlank()) { _state.value = s.copy(errorMessage = "Укажите поставщика"); return }
        val inputs = buildLineInputs()
        if (inputs == null) { _state.value = s.copy(errorMessage = "Заполните хотя бы одну строку закупки корректно"); return }
        viewModelScope.launch {
            container.purchaseRepository.saveDraft(
                s.purchaseId, Instant.now().toEpochMilli(), s.invoiceNumber.ifBlank { null }, s.supplier,
                s.comment.ifBlank { null }, null, inputs, Instant.now().toEpochMilli(),
            )
            _state.value = _state.value.copy(saved = true)
        }
    }

    fun saveAndPost() {
        val s = _state.value
        if (s.supplier.isBlank()) { _state.value = s.copy(errorMessage = "Укажите поставщика"); return }
        val inputs = buildLineInputs()
        if (inputs == null) { _state.value = s.copy(errorMessage = "Заполните хотя бы одну строку закупки корректно"); return }
        viewModelScope.launch {
            val id = container.purchaseRepository.saveDraft(
                s.purchaseId, Instant.now().toEpochMilli(), s.invoiceNumber.ifBlank { null }, s.supplier,
                s.comment.ifBlank { null }, null, inputs, Instant.now().toEpochMilli(),
            )
            container.purchaseRepository.postPurchase(id, Instant.now().toEpochMilli())
            _state.value = _state.value.copy(posted = true, saved = true)
        }
    }
}
