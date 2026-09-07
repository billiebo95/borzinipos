package com.borzini.pos.ui.catalog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.borzini.pos.core.Money
import com.borzini.pos.core.Quantity
import com.borzini.pos.core.RecipeCostCalculator
import com.borzini.pos.core.RecipeLine
import com.borzini.pos.core.UnitCost
import com.borzini.pos.data.db.entities.CategoryEntity
import com.borzini.pos.data.db.entities.InventoryItemEntity
import com.borzini.pos.data.repository.VariantInput
import com.borzini.pos.di.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class RecipeLineEdit(val key: String = UUID.randomUUID().toString(), val inventoryItemId: String?, val quantityText: String)
data class VariantEdit(
    val key: String = UUID.randomUUID().toString(),
    val existingId: String? = null,
    val name: String,
    val priceText: String,
    val recipeLines: List<RecipeLineEdit> = emptyList(),
)

data class ProductEditorState(
    val loaded: Boolean = false,
    val productId: String? = null,
    val name: String = "",
    val description: String = "",
    val categoryId: String? = null,
    val isSimpleProduct: Boolean = false,
    val simpleInventoryItemId: String? = null,
    val variants: List<VariantEdit> = listOf(VariantEdit(name = "", priceText = "")),
    val categories: List<CategoryEntity> = emptyList(),
    val inventoryItems: List<InventoryItemEntity> = emptyList(),
    val saved: Boolean = false,
    val errorMessage: String? = null,
) {
    fun costFor(variant: VariantEdit): Money {
        val items = inventoryItems.associateBy { it.id }
        val lines = variant.recipeLines.mapNotNull { rl ->
            val item = rl.inventoryItemId?.let { items[it] } ?: return@mapNotNull null
            val qty = rl.quantityText.toBigDecimalOrNull() ?: return@mapNotNull null
            RecipeLine(item.name, Quantity(qty), UnitCost(item.avgUnitCostRubles))
        }
        return RecipeCostCalculator.costPerPortion(lines)
    }
}

private fun String.toBigDecimalOrNull(): BigDecimal? = runCatching { BigDecimal(this.replace(",", ".")) }.getOrNull()

class ProductEditorViewModel(private val container: AppContainer, private val productId: String?) : ViewModel() {
    private val _state = MutableStateFlow(ProductEditorState(productId = productId))
    val state: StateFlow<ProductEditorState> = _state

    init {
        viewModelScope.launch {
            val categories = container.catalogRepository.observeAllCategories().first()
            val items = container.inventoryRepository.observeActiveItems().first()
            var loadedState = _state.value.copy(loaded = true, categories = categories, inventoryItems = items)

            if (productId != null) {
                val product = container.catalogRepository.observeProduct(productId).first()
                if (product != null) {
                    val variants = container.catalogRepository.observeVariantsForProduct(productId).first()
                    val variantEdits = variants.map { v ->
                        val recipe = container.catalogRepository.observeRecipeForVariant(v.id).first()
                        VariantEdit(
                            existingId = v.id,
                            name = v.name ?: "",
                            priceText = Money.ofKopecks(v.priceKopecks).rubles.toPlainString(),
                            recipeLines = recipe.map { RecipeLineEdit(inventoryItemId = it.inventoryItemId, quantityText = it.quantityPerPortion.toPlainString()) },
                        )
                    }
                    loadedState = loadedState.copy(
                        name = product.name,
                        description = product.description ?: "",
                        categoryId = product.categoryId,
                        isSimpleProduct = product.isSimpleProduct,
                        simpleInventoryItemId = product.simpleInventoryItemId,
                        variants = variantEdits.ifEmpty { listOf(VariantEdit(name = "", priceText = "")) },
                    )
                }
            } else if (categories.isNotEmpty()) {
                loadedState = loadedState.copy(categoryId = categories.first().id)
            }
            _state.value = loadedState
        }
    }

    fun setName(v: String) = update { it.copy(name = v) }
    fun setDescription(v: String) = update { it.copy(description = v) }
    fun setCategoryId(v: String) = update { it.copy(categoryId = v) }
    fun setSimpleProduct(v: Boolean) = update { it.copy(isSimpleProduct = v) }
    fun setSimpleInventoryItemId(v: String) = update { it.copy(simpleInventoryItemId = v) }

    fun addVariant() = update { it.copy(variants = it.variants + VariantEdit(name = "", priceText = "")) }
    fun removeVariant(key: String) = update { it.copy(variants = it.variants.filterNot { v -> v.key == key }) }
    fun updateVariant(key: String, name: String? = null, price: String? = null) = update { s ->
        s.copy(variants = s.variants.map { v -> if (v.key == key) v.copy(name = name ?: v.name, priceText = price ?: v.priceText) else v })
    }

    fun addRecipeLine(variantKey: String) = update { s ->
        s.copy(
            variants = s.variants.map { v ->
                if (v.key == variantKey) v.copy(recipeLines = v.recipeLines + RecipeLineEdit(inventoryItemId = null, quantityText = "")) else v
            },
        )
    }
    fun removeRecipeLine(variantKey: String, lineKey: String) = update { s ->
        s.copy(variants = s.variants.map { v -> if (v.key == variantKey) v.copy(recipeLines = v.recipeLines.filterNot { it.key == lineKey }) else v })
    }
    fun updateRecipeLine(variantKey: String, lineKey: String, inventoryItemId: String? = null, quantity: String? = null) = update { s ->
        s.copy(
            variants = s.variants.map { v ->
                if (v.key != variantKey) return@map v
                v.copy(
                    recipeLines = v.recipeLines.map { rl ->
                        if (rl.key != lineKey) rl else rl.copy(inventoryItemId = inventoryItemId ?: rl.inventoryItemId, quantityText = quantity ?: rl.quantityText)
                    },
                )
            },
        )
    }

    private fun update(transform: (ProductEditorState) -> ProductEditorState) {
        _state.value = transform(_state.value)
    }

    fun save() {
        val s = _state.value
        val categoryId = s.categoryId
        if (s.name.isBlank() || categoryId == null) {
            update { it.copy(errorMessage = "Укажите название и категорию") }
            return
        }
        if (s.isSimpleProduct && s.simpleInventoryItemId == null) {
            update { it.copy(errorMessage = "Выберите складскую позицию для простого товара") }
            return
        }
        if (!s.isSimpleProduct) {
            for (v in s.variants) {
                if (v.name.isBlank() && s.variants.size > 1) {
                    update { it.copy(errorMessage = "У каждого варианта должно быть название, если их несколько") }
                    return
                }
                if (v.priceText.toBigDecimalOrNull() == null) {
                    update { it.copy(errorMessage = "Укажите цену для каждого варианта") }
                    return
                }
            }
        }

        viewModelScope.launch {
            val variantInputs = s.variants.map { v ->
                VariantInput(
                    id = v.existingId,
                    name = v.name.ifBlank { null },
                    price = Money.fromRubles(v.priceText.toBigDecimalOrNull() ?: BigDecimal.ZERO),
                    recipe = v.recipeLines.mapNotNull { rl ->
                        val itemId = rl.inventoryItemId ?: return@mapNotNull null
                        val qty = rl.quantityText.toBigDecimalOrNull() ?: return@mapNotNull null
                        itemId to Quantity(qty)
                    },
                )
            }
            container.catalogRepository.saveProductWithVariants(
                productId = s.productId,
                categoryId = categoryId,
                name = s.name,
                description = s.description.ifBlank { null },
                photoUri = null,
                isSimpleProduct = s.isSimpleProduct,
                simpleInventoryItemId = s.simpleInventoryItemId,
                variants = variantInputs,
                now = Instant.now().toEpochMilli(),
            )
            update { it.copy(saved = true) }
        }
    }
}
