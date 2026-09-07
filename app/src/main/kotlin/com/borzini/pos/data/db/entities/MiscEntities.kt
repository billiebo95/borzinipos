package com.borzini.pos.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "expenses")
data class ExpenseEntity(
    @PrimaryKey val id: String,
    val date: Long,
    /** RENT / SALARY / UTILITIES / ACQUIRING_FEE / TAXES / OTHER - see com.borzini.pos.core.ExpenseCategory. */
    val category: String,
    val amountKopecks: Long,
    val comment: String? = null,
    val isDemo: Boolean = false,
    val createdAt: Long,
)

@Entity(tableName = "goals")
data class GoalEntity(
    @PrimaryKey val id: String,
    /** DAILY / MONTHLY */
    val type: String,
    /** "2026-09-07" for a daily goal, "2026-09" for a monthly goal - lets each period keep its own target. */
    val periodKey: String,
    val targetKopecks: Long,
    val updatedAt: Long,
)

enum class SyncEntityType { PRODUCT, CATEGORY, INVENTORY_ITEM, PURCHASE, STOCK_MOVEMENT, SALE, RETURN, EXPENSE, GOAL }
enum class SyncOperation { UPSERT, DELETE }
enum class SyncStatus { PENDING, IN_PROGRESS, DONE, FAILED }

/**
 * One outbox row per change that needs to reach Google Sheets/Drive. Durable (its own Room
 * table, survives app restart), processed by SyncWorker, and never marked DONE unless the whole
 * push for that row actually succeeded - a partial failure leaves it PENDING/FAILED so it is
 * retried rather than silently treated as synced.
 */
@Entity(tableName = "sync_queue")
data class SyncQueueEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val entityType: String,
    val entityId: String,
    val operation: String,
    /** JSON snapshot of the row at enqueue time - what actually gets pushed, independent of later local edits. */
    val payloadJson: String,
    val createdAt: Long,
    val attempts: Int = 0,
    val lastError: String? = null,
    val status: String = "PENDING",
)
