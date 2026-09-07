package com.borzini.pos.data.repository

import com.borzini.pos.core.ExpenseCategory
import com.borzini.pos.core.Money
import com.borzini.pos.data.db.BorziniDatabase
import com.borzini.pos.data.db.entities.ExpenseEntity
import com.borzini.pos.data.db.entities.GoalEntity
import com.borzini.pos.data.db.entities.SyncEntityType
import com.borzini.pos.data.db.entities.SyncOperation
import kotlinx.coroutines.flow.Flow
import org.json.JSONObject

class ExpenseRepository(private val db: BorziniDatabase, private val syncQueue: SyncQueueHelper) {
    private val dao = db.expenseDao()

    fun observeInRange(start: Long, end: Long, includeDemo: Boolean): Flow<List<ExpenseEntity>> =
        dao.observeInRange(start, end, includeDemo)

    fun observeAll(): Flow<List<ExpenseEntity>> = dao.observeAll()

    suspend fun save(id: String?, date: Long, category: ExpenseCategory, amount: Money, comment: String?, now: Long, isDemo: Boolean = false): String {
        val expenseId = id ?: IdGenerator.newId()
        dao.upsert(
            ExpenseEntity(
                id = expenseId,
                date = date,
                category = category.name,
                amountKopecks = amount.kopecks,
                comment = comment,
                isDemo = isDemo,
                createdAt = now,
            ),
        )
        syncQueue.enqueue(
            SyncEntityType.EXPENSE,
            expenseId,
            SyncOperation.UPSERT,
            JSONObject().apply {
                put("id", expenseId)
                put("date", date)
                put("category", category.name)
                put("amountKopecks", amount.kopecks)
                put("comment", comment)
            },
            now,
        )
        return expenseId
    }

    suspend fun delete(id: String, now: Long) {
        dao.delete(id)
        syncQueue.enqueue(SyncEntityType.EXPENSE, id, SyncOperation.DELETE, JSONObject().apply { put("id", id) }, now)
    }
}

class GoalRepository(private val db: BorziniDatabase, private val syncQueue: SyncQueueHelper) {
    private val dao = db.goalDao()

    fun observeDailyGoal(periodKey: String): Flow<GoalEntity?> = dao.observe("DAILY", periodKey)
    fun observeMonthlyGoal(periodKey: String): Flow<GoalEntity?> = dao.observe("MONTHLY", periodKey)

    suspend fun setGoal(type: String, periodKey: String, target: Money, now: Long) {
        val goal = GoalEntity(id = "$type|$periodKey", type = type, periodKey = periodKey, targetKopecks = target.kopecks, updatedAt = now)
        dao.upsert(goal)
        syncQueue.enqueue(
            SyncEntityType.GOAL,
            goal.id,
            SyncOperation.UPSERT,
            JSONObject().apply {
                put("id", goal.id)
                put("type", type)
                put("periodKey", periodKey)
                put("targetKopecks", target.kopecks)
            },
            now,
        )
    }
}
