package com.borzini.pos.data.repository

import com.borzini.pos.data.db.dao.SyncQueueDao
import com.borzini.pos.data.db.entities.SyncEntityType
import com.borzini.pos.data.db.entities.SyncOperation
import com.borzini.pos.data.db.entities.SyncQueueEntity
import org.json.JSONObject

/**
 * Enqueues one durable outbox row. Always called from inside the SAME Room @Transaction as the
 * business write it describes (see SaleRepository/PurchaseRepository/etc.) - so a crash between
 * "sale saved" and "sync job queued" cannot happen: either both commit or neither does.
 */
class SyncQueueHelper(private val syncQueueDao: SyncQueueDao) {
    suspend fun enqueue(type: SyncEntityType, entityId: String, operation: SyncOperation, payload: JSONObject, now: Long) {
        syncQueueDao.insert(
            SyncQueueEntity(
                entityType = type.name,
                entityId = entityId,
                operation = operation.name,
                payloadJson = payload.toString(),
                createdAt = now,
            ),
        )
    }
}
