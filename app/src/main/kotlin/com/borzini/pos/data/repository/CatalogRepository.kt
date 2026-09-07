package com.borzini.pos.data.repository

import androidx.room.withTransaction
import com.borzini.pos.core.Money
import com.borzini.pos.core.Quantity
import com.borzini.pos.core.RecipeCostCalculator
import com.borzini.pos.core.RecipeLine
import com.borzini.pos.core.UnitCost
import com.borzini.pos.data.db.BorziniDatabase
import com.borzini.pos.data.db.entities.CategoryEntity
import com.borzini.pos.data.db.entities.ProductEntity
import com.borzini.pos.data.db.entities.ProductVariantEntity
import com.borzini.pos.data.db.entities.RecipeLineEntity
import com.borzini.pos.data.db.entities.SyncEntityType
import com.borzini.pos.data.db.entities.SyncOperation
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal

data class VariantInput(
    val id: String? = null,
    val name: String?,
    val price: Money,
    /** inventoryItemId -> quantity per portion */
    val recipe: List<Pair<String, Quantity>>,
)

class CatalogRepository(private val db: BorziniDatabase, private val syncQueue: SyncQueueHelper) {
    private val categoryDao = db.categoryDao()
    private val productDao = db.productDao()
    private val variantDao = db.productVariantDao()
    private val recipeDao = db.recipeLineDao()
    private val inventoryDao = db.inventoryItemDao()

    fun observeActiveCategories(): Flow<List<CategoryEntity>> = categoryDao.observeActive()
    fun observeAllCategories(): Flow<List<CategoryEntity>> = categoryDao.observeAll()

    suspend fun saveCategory(id: String?, name: String, now: Long): String {
        val categoryId = id ?: IdGenerator.newId()
        val sortOrder = if (id == null) categoryDao.nextSortOrder() else categoryDao.getById(id)?.sortOrder ?: 0
        categoryDao.upsert(CategoryEntity(id = categoryId, name = name, sortOrder = sortOrder, updatedAt = now))
        syncQueue.enqueue(
            SyncEntityType.CATEGORY,
            categoryId,
            SyncOperation.UPSERT,
            JSONObject().apply { put("id", categoryId); put("name", name); put("sortOrder", sortOrder) },
            now,
        )
        return categoryId
    }

    suspend fun setCategoryArchived(id: String, archived: Boolean, now: Long) {
        val existing = categoryDao.getById(id) ?: return
        categoryDao.upsert(existing.copy(isArchived = archived, updatedAt = now))
        syncQueue.enqueue(
            SyncEntityType.CATEGORY,
            id,
            SyncOperation.UPSERT,
            JSONObject().apply { put("id", id); put("name", existing.name); put("sortOrder", existing.sortOrder); put("isArchived", archived) },
            now,
        )
    }

    fun observeActiveProducts(includeDemo: Boolean): Flow<List<ProductEntity>> = productDao.observeActive(includeDemo)
    fun observeAllProducts(): Flow<List<ProductEntity>> = productDao.observeAll()
    fun observeProduct(id: String): Flow<ProductEntity?> = productDao.observeById(id)
    fun observeVariantsForProduct(productId: String): Flow<List<ProductVariantEntity>> =
        variantDao.observeForProduct(productId)
    fun observeAllActiveVariants(): Flow<List<ProductVariantEntity>> = variantDao.observeAllActive()

    fun observeRecipeForVariant(variantId: String): Flow<List<RecipeLineEntity>> =
        recipeDao.observeForVariant(variantId)

    suspend fun setProductArchived(id: String, archived: Boolean, now: Long) = productDao.setArchived(id, archived, now)
    suspend fun setProductPinned(id: String, pinned: Boolean, now: Long) = productDao.setPinnedPopular(id, pinned, now)

    /**
     * Creates or updates a product together with all of its price/size variants and each
     * variant's recipe, atomically. A variant whose id is null is a brand new size; recipe lines
     * for a variant are always fully replaced (delete-then-insert) rather than diffed, which is
     * simple and safe because recipe lines carry no history of their own - only the resulting
     * per-sale [com.borzini.pos.data.db.entities.SaleItemDeductionEntity] snapshot does.
     */
    suspend fun saveProductWithVariants(
        productId: String?,
        categoryId: String,
        name: String,
        description: String?,
        photoUri: String?,
        isSimpleProduct: Boolean,
        simpleInventoryItemId: String?,
        variants: List<VariantInput>,
        now: Long,
        isDemo: Boolean = false,
    ): String = db.withTransaction {
        val id = productId ?: IdGenerator.newId()
        val existing = if (productId != null) productDao.getById(productId) else null
        val sortOrder = existing?.sortOrder ?: productDao.nextSortOrder()
        productDao.upsert(
            ProductEntity(
                id = id,
                categoryId = categoryId,
                name = name,
                description = description,
                photoUri = photoUri,
                isSimpleProduct = isSimpleProduct,
                simpleInventoryItemId = simpleInventoryItemId,
                isPinnedPopular = existing?.isPinnedPopular ?: false,
                isAvailableForSale = existing?.isAvailableForSale ?: true,
                isArchived = existing?.isArchived ?: false,
                sortOrder = sortOrder,
                isDemo = isDemo || (existing?.isDemo ?: false),
                createdAt = existing?.createdAt ?: now,
                updatedAt = now,
            ),
        )

        if (!isSimpleProduct) {
            for (variant in variants) {
                val variantId = variant.id ?: IdGenerator.newId()
                variantDao.upsert(
                    ProductVariantEntity(
                        id = variantId,
                        productId = id,
                        name = variant.name,
                        priceKopecks = variant.price.kopecks,
                        updatedAt = now,
                    ),
                )
                val recipeLines = variant.recipe.mapIndexed { index, (inventoryItemId, qty) ->
                    RecipeLineEntity(
                        id = IdGenerator.newId(),
                        variantId = variantId,
                        inventoryItemId = inventoryItemId,
                        quantityPerPortion = qty.amount,
                        sortOrder = index,
                    )
                }
                recipeDao.replaceForVariant(variantId, recipeLines)
            }
        }

        syncQueue.enqueue(
            SyncEntityType.PRODUCT,
            id,
            SyncOperation.UPSERT,
            JSONObject().apply {
                put("id", id)
                put("categoryId", categoryId)
                put("name", name)
                put("isSimpleProduct", isSimpleProduct)
                put(
                    "variants",
                    JSONArray().apply {
                        variants.forEach { v ->
                            put(
                                JSONObject().apply {
                                    put("name", v.name)
                                    put("priceKopecks", v.price.kopecks)
                                    put(
                                        "recipe",
                                        v.recipe.joinToString("; ") { (itemId, qty) -> "$itemId x${qty.amount.toPlainString()}" },
                                    )
                                },
                            )
                        }
                    },
                )
            },
            now,
        )
        id
    }

    /** Cost, gross profit and margin for one variant, computed live from the current recipe and current ingredient costs. */
    suspend fun currentCostForVariant(variantId: String): Money {
        val lines = recipeDao.getForVariantOnce(variantId)
        if (lines.isEmpty()) return Money.ZERO
        val items = inventoryDao.getByIds(lines.map { it.inventoryItemId }).associateBy { it.id }
        val coreLines = lines.mapNotNull { line ->
            val item = items[line.inventoryItemId] ?: return@mapNotNull null
            RecipeLine(item.name, Quantity(line.quantityPerPortion), UnitCost(item.avgUnitCostRubles))
        }
        return RecipeCostCalculator.costPerPortion(coreLines)
    }

    /** Cost of one unit of a simple (no-recipe) product: just the linked inventory item's current unit cost. */
    suspend fun currentCostForSimpleProduct(inventoryItemId: String): Money {
        val item = inventoryDao.getById(inventoryItemId) ?: return Money.ZERO
        return UnitCost(item.avgUnitCostRubles).costOf(Quantity(BigDecimal.ONE))
    }
}
