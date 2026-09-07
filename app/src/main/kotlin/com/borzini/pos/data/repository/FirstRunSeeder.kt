package com.borzini.pos.data.repository

import com.borzini.pos.core.BaseUnit
import com.borzini.pos.core.Quantity
import com.borzini.pos.core.Money
import kotlinx.coroutines.flow.first
import java.math.BigDecimal
import java.time.Instant

/** Seeds the starting categories from spec section 3 on the very first launch only - never re-runs once any category exists. */
class FirstRunSeeder(private val catalogRepository: CatalogRepository) {
    suspend fun seedDefaultCategoriesIfEmpty() {
        val existing = catalogRepository.observeAllCategories().first()
        if (existing.isNotEmpty()) return
        val now = Instant.now().toEpochMilli()
        listOf("Горячий кофе", "Холодный кофе", "Фреш", "Тоники", "Прочее").forEach { name ->
            catalogRepository.saveCategory(null, name, now)
        }
        // "Популярное" is intentionally not a real category row - it's a virtual tab formed from
        // sales + manually pinned products (see PosViewModel.POPULAR_CATEGORY_ID).
    }
}

/**
 * Demo mode only (spec section 11: demo data - including the granatoviy tonik example - must
 * never reach real financial reports). Everything this seeds is marked isDemo = true, and every
 * stats/receipts query filters demo rows out unless the user has explicitly turned demo mode on.
 */
class DemoDataSeeder(
    private val catalogRepository: CatalogRepository,
    private val inventoryRepository: InventoryRepository,
) {
    suspend fun seedIfNeeded() {
        val products = catalogRepository.observeAllProducts().first()
        if (products.any { it.isDemo }) return

        val now = Instant.now().toEpochMilli()
        val categories = catalogRepository.observeAllCategories().first()
        val tonicsCategoryId = categories.find { it.name == "Тоники" }?.id
            ?: catalogRepository.saveCategory(null, "Тоники", now)

        suspend fun demoItem(name: String, unit: BaseUnit, min: String) =
            inventoryRepository.saveItem(null, name, "Демо-упаковка", unit, Quantity(BigDecimal(min)), null, now, isDemo = true)

        val cupId = demoItem("Стакан 400мл (демо)", BaseUnit.PIECE, "20")
        val lidId = demoItem("Крышка (демо)", BaseUnit.PIECE, "20")
        val juiceId = demoItem("Гранатовый сок (демо)", BaseUnit.MILLILITRE, "1000")
        val tonicId = demoItem("Тоник (демо)", BaseUnit.MILLILITRE, "1000")
        val iceId = demoItem("Лёд (демо)", BaseUnit.GRAM, "1000")
        val strawId = demoItem("Трубочка (демо)", BaseUnit.PIECE, "20")

        // Give the demo ingredients some starting stock (logged as a normal inventory count, so
        // it leaves the same kind of audit trail a real first count would) so a demo sale can
        // actually be completed end to end.
        listOf(cupId to "50", lidId to "50", juiceId to "5000", tonicId to "10000", iceId to "5000", strawId to "50")
            .forEach { (id, qty) -> inventoryRepository.performInventoryCount(id, Quantity(BigDecimal(qty)), "Начальный остаток (демо)", now) }

        catalogRepository.saveProductWithVariants(
            productId = null,
            categoryId = tonicsCategoryId,
            name = "Гранатовый тоник (демо)",
            description = "Демонстрационный товар - показывает, как работает рецептура",
            photoUri = null,
            isSimpleProduct = false,
            simpleInventoryItemId = null,
            variants = listOf(
                VariantInput(
                    name = "400 мл",
                    price = Money.fromRubles(BigDecimal("250")),
                    recipe = listOf(
                        cupId to Quantity.of(1),
                        lidId to Quantity.of(1),
                        juiceId to Quantity.of(100),
                        tonicId to Quantity.of(200),
                        iceId to Quantity.of(100),
                        strawId to Quantity.of(1),
                    ),
                ),
            ),
            now = now,
            isDemo = true,
        )
    }
}
