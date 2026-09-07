package com.borzini.pos.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.borzini.pos.data.db.entities.ReturnEntity
import com.borzini.pos.data.db.entities.ReturnItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ReturnDao {
    @Insert
    suspend fun insert(returnEntity: ReturnEntity)

    @Insert
    suspend fun insertItems(items: List<ReturnItemEntity>)

    @Query("SELECT * FROM returns WHERE saleId = :saleId ORDER BY createdAt DESC")
    suspend fun getForSale(saleId: String): List<ReturnEntity>

    @Query("SELECT * FROM return_items WHERE returnId = :returnId")
    suspend fun getItemsForReturn(returnId: String): List<ReturnItemEntity>

    @Query(
        "SELECT COALESCE(SUM(quantityReturned), 0) FROM return_items WHERE saleItemId = :saleItemId",
    )
    suspend fun totalReturnedForSaleItem(saleItemId: String): Int

    @Query(
        "SELECT * FROM returns WHERE createdAt >= :start AND createdAt < :end AND (isDemo = 0 OR :includeDemo = 1) " +
            "ORDER BY createdAt DESC",
    )
    fun observeInRange(start: Long, end: Long, includeDemo: Boolean): Flow<List<ReturnEntity>>
}
