package com.borzini.pos.data.db.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.math.BigDecimal

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey val id: String,
    val name: String,
    val sortOrder: Int,
    val isPopularAuto: Boolean = false,
    val isArchived: Boolean = false,
    val updatedAt: Long,
)

@Entity(
    tableName = "products",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("categoryId")],
)
data class ProductEntity(
    @PrimaryKey val id: String,
    val categoryId: String,
    val name: String,
    val description: String? = null,
    val photoUri: String? = null,
    /** A simple product (e.g. a bottled water) deducts [simpleInventoryItemId] 1:1 per unit sold, no recipe/variants needed. */
    val isSimpleProduct: Boolean = false,
    val simpleInventoryItemId: String? = null,
    val isPinnedPopular: Boolean = false,
    val isAvailableForSale: Boolean = true,
    val isArchived: Boolean = false,
    val sortOrder: Int = 0,
    val isDemo: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "product_variants",
    foreignKeys = [
        ForeignKey(
            entity = ProductEntity::class,
            parentColumns = ["id"],
            childColumns = ["productId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("productId")],
)
data class ProductVariantEntity(
    @PrimaryKey val id: String,
    val productId: String,
    /** e.g. "400 мл" / "Средний" - null for a product with only one size. */
    val name: String?,
    val priceKopecks: Long,
    val sortOrder: Int = 0,
    val isArchived: Boolean = false,
    val updatedAt: Long,
)

@Entity(
    tableName = "recipe_lines",
    foreignKeys = [
        ForeignKey(
            entity = ProductVariantEntity::class,
            parentColumns = ["id"],
            childColumns = ["variantId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = InventoryItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["inventoryItemId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("variantId"), Index("inventoryItemId")],
)
data class RecipeLineEntity(
    @PrimaryKey val id: String,
    val variantId: String,
    val inventoryItemId: String,
    val quantityPerPortion: BigDecimal,
    val sortOrder: Int = 0,
)
