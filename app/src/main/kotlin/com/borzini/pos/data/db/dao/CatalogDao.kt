package com.borzini.pos.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.borzini.pos.data.db.entities.CategoryEntity
import com.borzini.pos.data.db.entities.ProductEntity
import com.borzini.pos.data.db.entities.ProductVariantEntity
import com.borzini.pos.data.db.entities.RecipeLineEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY sortOrder")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE isArchived = 0 ORDER BY sortOrder")
    fun observeActive(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun getById(id: String): CategoryEntity?

    @Upsert
    suspend fun upsert(category: CategoryEntity)

    @Query("SELECT COALESCE(MAX(sortOrder), -1) + 1 FROM categories")
    suspend fun nextSortOrder(): Int
}

@Dao
interface ProductDao {
    @Query("SELECT * FROM products ORDER BY sortOrder")
    fun observeAll(): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products WHERE isArchived = 0 AND (isDemo = 0 OR :includeDemo = 1) ORDER BY sortOrder")
    fun observeActive(includeDemo: Boolean): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products WHERE isArchived = 0 ORDER BY sortOrder")
    fun observeActiveAll(): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products WHERE id = :id")
    suspend fun getById(id: String): ProductEntity?

    @Query("SELECT * FROM products WHERE id = :id")
    fun observeById(id: String): Flow<ProductEntity?>

    @Upsert
    suspend fun upsert(product: ProductEntity)

    @Query("SELECT COALESCE(MAX(sortOrder), -1) + 1 FROM products")
    suspend fun nextSortOrder(): Int

    @Query("UPDATE products SET isArchived = :archived, updatedAt = :now WHERE id = :id")
    suspend fun setArchived(id: String, archived: Boolean, now: Long)

    @Query("UPDATE products SET isPinnedPopular = :pinned, updatedAt = :now WHERE id = :id")
    suspend fun setPinnedPopular(id: String, pinned: Boolean, now: Long)
}

@Dao
interface ProductVariantDao {
    @Query("SELECT * FROM product_variants WHERE productId = :productId AND isArchived = 0 ORDER BY sortOrder")
    fun observeForProduct(productId: String): Flow<List<ProductVariantEntity>>

    @Query("SELECT * FROM product_variants WHERE isArchived = 0")
    fun observeAllActive(): Flow<List<ProductVariantEntity>>

    @Query("SELECT * FROM product_variants WHERE id = :id")
    suspend fun getById(id: String): ProductVariantEntity?

    @Upsert
    suspend fun upsert(variant: ProductVariantEntity)

    @Upsert
    suspend fun upsertAll(variants: List<ProductVariantEntity>)

    @Query("UPDATE product_variants SET isArchived = 1, updatedAt = :now WHERE productId = :productId")
    suspend fun archiveAllForProduct(productId: String, now: Long)
}

@Dao
interface RecipeLineDao {
    @Query("SELECT * FROM recipe_lines WHERE variantId = :variantId ORDER BY sortOrder")
    fun observeForVariant(variantId: String): Flow<List<RecipeLineEntity>>

    @Query("SELECT * FROM recipe_lines WHERE variantId = :variantId ORDER BY sortOrder")
    suspend fun getForVariantOnce(variantId: String): List<RecipeLineEntity>

    @Query("SELECT * FROM recipe_lines WHERE inventoryItemId = :inventoryItemId")
    suspend fun getUsagesOfIngredient(inventoryItemId: String): List<RecipeLineEntity>

    @Upsert
    suspend fun upsertAll(lines: List<RecipeLineEntity>)

    @Query("DELETE FROM recipe_lines WHERE variantId = :variantId")
    suspend fun deleteForVariant(variantId: String)

    @Transaction
    suspend fun replaceForVariant(variantId: String, lines: List<RecipeLineEntity>) {
        deleteForVariant(variantId)
        if (lines.isNotEmpty()) upsertAll(lines)
    }
}
