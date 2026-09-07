package com.borzini.pos.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import com.borzini.pos.data.db.entities.PurchaseEntity
import com.borzini.pos.data.db.entities.PurchaseLineEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PurchaseDao {
    @Query("SELECT * FROM purchases ORDER BY date DESC, createdAt DESC")
    fun observeAll(): Flow<List<PurchaseEntity>>

    @Query("SELECT * FROM purchases WHERE id = :id")
    suspend fun getById(id: String): PurchaseEntity?

    @Query("SELECT * FROM purchases WHERE id = :id")
    fun observeById(id: String): Flow<PurchaseEntity?>

    @Upsert
    suspend fun upsert(purchase: PurchaseEntity)

    @Query("UPDATE purchases SET isPosted = 1, postedAt = :postedAt WHERE id = :id")
    suspend fun markPosted(id: String, postedAt: Long)

    @Query("SELECT * FROM purchase_lines WHERE purchaseId = :purchaseId")
    fun observeLinesForPurchase(purchaseId: String): Flow<List<PurchaseLineEntity>>

    @Query("SELECT * FROM purchase_lines WHERE purchaseId = :purchaseId")
    suspend fun getLinesForPurchaseOnce(purchaseId: String): List<PurchaseLineEntity>

    @Insert
    suspend fun insertLines(lines: List<PurchaseLineEntity>)

    @Query("DELETE FROM purchase_lines WHERE purchaseId = :purchaseId")
    suspend fun deleteLinesForPurchase(purchaseId: String)
}
