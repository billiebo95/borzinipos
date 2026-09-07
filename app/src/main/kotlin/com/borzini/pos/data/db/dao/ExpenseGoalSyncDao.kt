package com.borzini.pos.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import com.borzini.pos.data.db.entities.ExpenseEntity
import com.borzini.pos.data.db.entities.GoalEntity
import com.borzini.pos.data.db.entities.SyncQueueEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ExpenseDao {
    @Query(
        "SELECT * FROM expenses WHERE date >= :start AND date < :end AND (isDemo = 0 OR :includeDemo = 1) " +
            "ORDER BY date DESC",
    )
    fun observeInRange(start: Long, end: Long, includeDemo: Boolean): Flow<List<ExpenseEntity>>

    @Query("SELECT * FROM expenses ORDER BY date DESC")
    fun observeAll(): Flow<List<ExpenseEntity>>

    @Upsert
    suspend fun upsert(expense: ExpenseEntity)

    @Query("DELETE FROM expenses WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface GoalDao {
    @Query("SELECT * FROM goals")
    suspend fun getAll(): List<GoalEntity>

    @Query("SELECT * FROM goals WHERE type = :type AND periodKey = :periodKey LIMIT 1")
    suspend fun get(type: String, periodKey: String): GoalEntity?

    @Query("SELECT * FROM goals WHERE type = :type AND periodKey = :periodKey LIMIT 1")
    fun observe(type: String, periodKey: String): Flow<GoalEntity?>

    @Upsert
    suspend fun upsert(goal: GoalEntity)
}

@Dao
interface SyncQueueDao {
    @Insert
    suspend fun insert(entry: SyncQueueEntity): Long

    @Query("SELECT * FROM sync_queue WHERE status IN ('PENDING', 'FAILED') ORDER BY createdAt ASC LIMIT :limit")
    suspend fun nextBatch(limit: Int): List<SyncQueueEntity>

    @Query("SELECT COUNT(*) FROM sync_queue WHERE status IN ('PENDING', 'FAILED', 'IN_PROGRESS')")
    fun observePendingCount(): Flow<Int>

    @Query("UPDATE sync_queue SET status = :status WHERE id = :id")
    suspend fun setStatus(id: Long, status: String)

    @Query("UPDATE sync_queue SET status = 'FAILED', attempts = attempts + 1, lastError = :error WHERE id = :id")
    suspend fun markFailed(id: Long, error: String)

    @Query("UPDATE sync_queue SET status = 'DONE' WHERE id = :id")
    suspend fun markDone(id: Long)

    @Query("SELECT * FROM sync_queue ORDER BY createdAt DESC LIMIT 200")
    fun observeRecent(): Flow<List<SyncQueueEntity>>
}
