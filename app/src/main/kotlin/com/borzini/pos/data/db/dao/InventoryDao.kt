package com.borzini.pos.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.borzini.pos.data.db.entities.InventoryItemEntity
import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal

@Dao
interface InventoryItemDao {
    @Query("SELECT * FROM inventory_items WHERE isArchived = 0 ORDER BY name")
    fun observeActive(): Flow<List<InventoryItemEntity>>

    @Query("SELECT * FROM inventory_items ORDER BY name")
    fun observeAll(): Flow<List<InventoryItemEntity>>

    @Query("SELECT * FROM inventory_items WHERE id = :id")
    suspend fun getById(id: String): InventoryItemEntity?

    @Query("SELECT * FROM inventory_items WHERE id = :id")
    fun observeById(id: String): Flow<InventoryItemEntity?>

    @Query("SELECT * FROM inventory_items WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<String>): List<InventoryItemEntity>

    @Upsert
    suspend fun upsert(item: InventoryItemEntity)

    @Query(
        "UPDATE inventory_items SET onHandAmount = :onHand, avgUnitCostRubles = :avgCost, updatedAt = :now WHERE id = :id",
    )
    suspend fun updateStockLevel(id: String, onHand: BigDecimal, avgCost: BigDecimal, now: Long)

    @Query("UPDATE inventory_items SET isArchived = :archived, updatedAt = :now WHERE id = :id")
    suspend fun setArchived(id: String, archived: Boolean, now: Long)

    @Query("SELECT * FROM inventory_items WHERE isArchived = 0 AND onHandAmount <= minAllowedAmount ORDER BY name")
    fun observeLowStock(): Flow<List<InventoryItemEntity>>
}
