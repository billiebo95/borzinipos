package com.borzini.pos.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.borzini.pos.data.db.entities.StockMovementEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface StockMovementDao {
    @Insert
    suspend fun insert(movement: StockMovementEntity)

    @Insert
    suspend fun insertAll(movements: List<StockMovementEntity>)

    @Query("SELECT * FROM stock_movements WHERE inventoryItemId = :itemId ORDER BY createdAt DESC")
    fun observeForItem(itemId: String): Flow<List<StockMovementEntity>>

    @Query("SELECT * FROM stock_movements WHERE createdAt >= :start AND createdAt < :end ORDER BY createdAt DESC")
    fun observeInRange(start: Long, end: Long): Flow<List<StockMovementEntity>>

    @Query("SELECT * FROM stock_movements WHERE id = :id")
    suspend fun getById(id: String): StockMovementEntity?
}
