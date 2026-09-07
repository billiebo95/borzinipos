package com.borzini.pos.ui.pos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.borzini.pos.core.Money
import com.borzini.pos.data.db.entities.CategoryEntity
import com.borzini.pos.data.db.entities.ProductEntity
import com.borzini.pos.data.db.entities.ProductVariantEntity
import com.borzini.pos.data.repository.IdGenerator
import com.borzini.pos.di.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

const val POPULAR_CATEGORY_ID = "__popular__"

data class ProductCardUi(
    val product: ProductEntity,
    val variants: List<ProductVariantEntity>,
) {
    val displayPrice: Money get() = Money.ofKopecks(variants.minOfOrNull { it.priceKopecks } ?: 0L)
    val hasSingleVariant: Boolean get() = variants.size == 1
}

data class PosUiState(
    val categories: List<CategoryEntity> = emptyList(),
    val selectedCategoryId: String = POPULAR_CATEGORY_ID,
    val searchQuery: String = "",
    val products: List<ProductCardUi> = emptyList(),
    val cart: List<CartUiLine> = emptyList(),
    val cartCount: Int = 0,
    val cartTotal: Money = Money.ZERO,
)

class PosViewModel(private val container: AppContainer) : ViewModel() {
    private val selectedCategoryId = MutableStateFlow(POPULAR_CATEGORY_ID)
    private val searchQuery = MutableStateFlow("")
    private val popularProductIds = MutableStateFlow<Set<String>>(emptySet())

    init {
        viewModelScope.launch {
            val zone = ZoneId.of(container.settingsDataStore.settingsFlow.first().timezoneId)
            val since = Instant.now().minus(30, ChronoUnit.DAYS).toEpochMilli()
            val top = container.statsRepository.getTopProducts(since, Instant.now().toEpochMilli(), true, 12)
            popularProductIds.value = top.mapNotNull { it.productId }.toSet()
        }
    }

    private val productsWithVariants = combine(
        container.catalogRepository.observeActiveProducts(includeDemo = true),
        container.catalogRepository.observeAllActiveVariants(),
    ) { products, variants ->
        products.map { p -> ProductCardUi(p, variants.filter { it.productId == p.id }.sortedBy { it.sortOrder }) }
    }

    val uiState: StateFlow<PosUiState> = combine(
        container.catalogRepository.observeActiveCategories(),
        productsWithVariants,
        selectedCategoryId,
        searchQuery,
        popularProductIds,
        container.cartHolder.lines,
    ) { flows ->
        @Suppress("UNCHECKED_CAST")
        val categories = flows[0] as List<CategoryEntity>
        @Suppress("UNCHECKED_CAST")
        val allProducts = flows[1] as List<ProductCardUi>
        val selectedCategory = flows[2] as String
        val query = flows[3] as String
        @Suppress("UNCHECKED_CAST")
        val popular = flows[4] as Set<String>
        @Suppress("UNCHECKED_CAST")
        val cart = flows[5] as List<CartUiLine>

        var filtered = allProducts.filter { it.product.isAvailableForSale }
        filtered = if (query.isNotBlank()) {
            filtered.filter { it.product.name.contains(query, ignoreCase = true) }
        } else if (selectedCategory == POPULAR_CATEGORY_ID) {
            filtered.filter { it.product.isPinnedPopular || popular.contains(it.product.id) }
        } else {
            filtered.filter { it.product.categoryId == selectedCategory }
        }

        PosUiState(
            categories = categories,
            selectedCategoryId = selectedCategory,
            searchQuery = query,
            products = filtered,
            cart = cart,
            cartCount = cart.sumOf { it.quantity },
            cartTotal = Money.sum(cart.map { it.lineTotal }),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PosUiState())

    fun selectCategory(categoryId: String) {
        selectedCategoryId.value = categoryId
    }

    fun setSearchQuery(query: String) {
        searchQuery.value = query
    }

    fun addSimpleTap(card: ProductCardUi) {
        val variant = card.variants.firstOrNull() ?: return
        addVariant(card.product, variant)
    }

    fun addVariant(product: ProductEntity, variant: ProductVariantEntity) {
        container.cartHolder.addOrIncrement(
            CartUiLine(
                lineId = IdGenerator.newId(),
                productId = product.id,
                productName = product.name,
                variantId = variant.id,
                variantName = variant.name,
                unitPrice = Money.ofKopecks(variant.priceKopecks),
                quantity = 1,
                isSimpleProduct = product.isSimpleProduct,
                simpleInventoryItemId = product.simpleInventoryItemId,
            ),
        )
    }

    fun setLineQuantity(lineId: String, quantity: Int) = container.cartHolder.setQuantity(lineId, quantity)
    fun removeLine(lineId: String) = container.cartHolder.removeLine(lineId)
    fun clearCart() = container.cartHolder.clear()
}
