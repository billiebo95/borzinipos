package com.borzini.pos.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.borzini.pos.data.db.dao.CategoryDao
import com.borzini.pos.data.db.dao.ExpenseDao
import com.borzini.pos.data.db.dao.GoalDao
import com.borzini.pos.data.db.dao.InventoryItemDao
import com.borzini.pos.data.db.dao.ProductDao
import com.borzini.pos.data.db.dao.ProductVariantDao
import com.borzini.pos.data.db.dao.PurchaseDao
import com.borzini.pos.data.db.dao.RecipeLineDao
import com.borzini.pos.data.db.dao.ReturnDao
import com.borzini.pos.data.db.dao.SaleDao
import com.borzini.pos.data.db.dao.StockMovementDao
import com.borzini.pos.data.db.dao.SyncQueueDao
import com.borzini.pos.data.db.entities.CategoryEntity
import com.borzini.pos.data.db.entities.ExpenseEntity
import com.borzini.pos.data.db.entities.GoalEntity
import com.borzini.pos.data.db.entities.InventoryItemEntity
import com.borzini.pos.data.db.entities.ProductEntity
import com.borzini.pos.data.db.entities.ProductVariantEntity
import com.borzini.pos.data.db.entities.PurchaseEntity
import com.borzini.pos.data.db.entities.PurchaseLineEntity
import com.borzini.pos.data.db.entities.RecipeLineEntity
import com.borzini.pos.data.db.entities.ReturnEntity
import com.borzini.pos.data.db.entities.ReturnItemEntity
import com.borzini.pos.data.db.entities.SaleEntity
import com.borzini.pos.data.db.entities.SaleItemDeductionEntity
import com.borzini.pos.data.db.entities.SaleItemEntity
import com.borzini.pos.data.db.entities.StockMovementEntity
import com.borzini.pos.data.db.entities.SyncQueueEntity

/**
 * Single local Room database - the source of truth the cash register runs on. Google Sheets is
 * only ever a synced *view* of this data (see sync/), never the other way round for the tables
 * that matter for money: sales, returns and stock movements are written here first, inside one
 * transaction together with their sync-queue row (see repository/SaleRepository etc.), and only
 * pushed out afterwards.
 *
 * version = 1: no migrations exist yet. When the schema changes after this app is already in
 * users' hands, add a real Migration(fromVersion, toVersion) here instead of destructive
 * fallback - see MIGRATIONS.md for the checklist.
 */
@Database(
    entities = [
        CategoryEntity::class,
        ProductEntity::class,
        ProductVariantEntity::class,
        RecipeLineEntity::class,
        InventoryItemEntity::class,
        PurchaseEntity::class,
        PurchaseLineEntity::class,
        StockMovementEntity::class,
        SaleEntity::class,
        SaleItemEntity::class,
        SaleItemDeductionEntity::class,
        ReturnEntity::class,
        ReturnItemEntity::class,
        ExpenseEntity::class,
        GoalEntity::class,
        SyncQueueEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class BorziniDatabase : RoomDatabase() {
    abstract fun categoryDao(): CategoryDao
    abstract fun productDao(): ProductDao
    abstract fun productVariantDao(): ProductVariantDao
    abstract fun recipeLineDao(): RecipeLineDao
    abstract fun inventoryItemDao(): InventoryItemDao
    abstract fun purchaseDao(): PurchaseDao
    abstract fun stockMovementDao(): StockMovementDao
    abstract fun saleDao(): SaleDao
    abstract fun returnDao(): ReturnDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun goalDao(): GoalDao
    abstract fun syncQueueDao(): SyncQueueDao

    companion object {
        const val DATABASE_NAME = "borzini.db"
    }
}
