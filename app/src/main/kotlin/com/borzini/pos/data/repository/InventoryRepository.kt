package com.borzini.pos.data.repository

import androidx.room.withTransaction
import com.borzini.pos.core.BaseUnit
import com.borzini.pos.core.InventoryMath
import com.borzini.pos.core.NegativeStockPolicy
import com.borzini.pos.core.Quantity
import com.borzini.pos.core.StockDeductionResult
import com.borzini.pos.core.StockLevel
import com.borzini.pos.core.StockMovementReason
import com.borzini.pos.core.UnitCost
import com.borzini.pos.data.db.BorziniDatabase
import com.borzini.pos.data.db.entities.InventoryItemEntity
import com.borzini.pos.data.db.entities.StockMovementEntity
import com.borzini.pos.data.db.entities.SyncEntityType
import com.borzini.pos.data.db.entities.SyncOperation
import kotlinx.coroutines.flow.Flow
import org.json.JSONObject
import java.math.BigDecimal

sealed class WriteOffResult {
    data object Ok : WriteOffResult()
    data class InsufficientStock(val available: BigDecimal, val requested: BigDecimal) : WriteOffResult()
}

class InventoryRepository(private val db: BorziniDatabase, private val syncQueue: SyncQueueHelper) {
    private val itemDao = db.inventoryItemDao()
    private val movementDao = db.stockMovementDao()

    fun observeActiveItems(): Flow<List<InventoryItemEntity>> = itemDao.observeActive()
    fun observeAllItems(): Flow<List<InventoryItemEntity>> = itemDao.observeAll()
    fun observeItem(id: String): Flow<InventoryItemEntity?> = itemDao.observeById(id)
    fun observeLowStock(): Flow<List<InventoryItemEntity>> = itemDao.observeLowStock()
    fun observeMovementsForItem(id: String): Flow<List<StockMovementEntity>> = movementDao.observeForItem(id)

    private suspend fun enqueueItemSnapshot(item: InventoryItemEntity, now: Long) {
        syncQueue.enqueue(
            SyncEntityType.INVENTORY_ITEM,
            item.id,
            SyncOperation.UPSERT,
            JSONObject().apply {
                put("id", item.id)
                put("name", item.name)
                put("category", item.category)
                put("baseUnit", item.baseUnit)
                put("onHandAmount", item.onHandAmount.toPlainString())
                put("avgUnitCostRubles", item.avgUnitCostRubles.toPlainString())
                put("minAllowedAmount", item.minAllowedAmount.toPlainString())
                put("isArchived", item.isArchived)
            },
            now,
        )
    }

    private suspend fun enqueueMovement(movement: StockMovementEntity, now: Long) {
        syncQueue.enqueue(
            SyncEntityType.STOCK_MOVEMENT,
            movement.id,
            SyncOperation.UPSERT,
            JSONObject().apply {
                put("id", movement.id)
                put("inventoryItemId", movement.inventoryItemId)
                put("reason", movement.reason)
                put("quantityDelta", movement.quantityDelta.toPlainString())
                put("unitCostAtMovementRubles", movement.unitCostAtMovementRubles.toPlainString())
                put("note", movement.note)
                put("createdAt", movement.createdAt)
            },
            now,
        )
    }

    suspend fun saveItem(
        id: String?,
        name: String,
        category: String,
        baseUnit: BaseUnit,
        minAllowed: Quantity,
        supplier: String?,
        now: Long,
        isDemo: Boolean = false,
    ): String {
        val itemId = id ?: IdGenerator.newId()
        val existing = if (id != null) itemDao.getById(id) else null
        val entity = InventoryItemEntity(
            id = itemId,
            name = name,
            category = category,
            baseUnit = baseUnit.name,
            onHandAmount = existing?.onHandAmount ?: BigDecimal.ZERO,
            avgUnitCostRubles = existing?.avgUnitCostRubles ?: BigDecimal.ZERO,
            minAllowedAmount = minAllowed.amount,
            supplier = supplier,
            isArchived = existing?.isArchived ?: false,
            isDemo = isDemo || (existing?.isDemo ?: false),
            updatedAt = now,
        )
        itemDao.upsert(entity)
        enqueueItemSnapshot(entity, now)
        return itemId
    }

    suspend fun setArchived(id: String, archived: Boolean, now: Long) {
        itemDao.setArchived(id, archived, now)
        itemDao.getById(id)?.let { enqueueItemSnapshot(it, now) }
    }

    /** Manual write-off: spillage, breakage, staff consumption, etc. Respects the negative-stock policy like a sale would. */
    suspend fun manualWriteOff(
        itemId: String,
        quantity: Quantity,
        note: String?,
        negativeStockPolicy: NegativeStockPolicy,
        now: Long,
    ): WriteOffResult = db.withTransaction {
        val entity = itemDao.getById(itemId) ?: return@withTransaction WriteOffResult.InsufficientStock(BigDecimal.ZERO, quantity.amount)
        val level = entity.toStockLevel()
        when (val result = InventoryMath.deduct(entity.name, level, quantity, negativeStockPolicy)) {
            is StockDeductionResult.InsufficientStock ->
                WriteOffResult.InsufficientStock(result.available.amount, result.required.amount)
            is StockDeductionResult.Ok -> {
                itemDao.updateStockLevel(itemId, result.newLevel.onHand.amount, result.newLevel.avgUnitCost.rublesPerUnit, now)
                val movement = StockMovementEntity(
                    id = IdGenerator.newId(),
                    inventoryItemId = itemId,
                    reason = StockMovementReason.MANUAL_WRITE_OFF.name,
                    quantityDelta = -quantity.amount,
                    unitCostAtMovementRubles = entity.avgUnitCostRubles,
                    note = note,
                    isDemo = entity.isDemo,
                    createdAt = now,
                )
                movementDao.insert(movement)
                enqueueMovement(movement, now)
                itemDao.getById(itemId)?.let { enqueueItemSnapshot(it, now) }
                WriteOffResult.Ok
            }
        }
    }

    /** Physical inventory count: sets on-hand to the counted amount and logs the signed difference. */
    suspend fun performInventoryCount(itemId: String, countedQuantity: Quantity, note: String?, now: Long) = db.withTransaction {
        val entity = itemDao.getById(itemId) ?: return@withTransaction
        val level = entity.toStockLevel()
        val diff = InventoryMath.inventoryCountDifference(level, countedQuantity)
        if (diff.isZero()) return@withTransaction
        itemDao.updateStockLevel(itemId, countedQuantity.amount, entity.avgUnitCostRubles, now)
        val movement = StockMovementEntity(
            id = IdGenerator.newId(),
            inventoryItemId = itemId,
            reason = StockMovementReason.INVENTORY_ADJUSTMENT.name,
            quantityDelta = diff.amount,
            unitCostAtMovementRubles = entity.avgUnitCostRubles,
            note = note,
            isDemo = entity.isDemo,
            createdAt = now,
        )
        movementDao.insert(movement)
        enqueueMovement(movement, now)
        itemDao.getById(itemId)?.let { enqueueItemSnapshot(it, now) }
    }

    /** Reverses a previously posted movement with a new, linked CORRECTION movement - the original row is never deleted. */
    suspend fun correctMovement(movementId: String, note: String, now: Long) = db.withTransaction {
        val original = movementDao.getById(movementId) ?: return@withTransaction
        val entity = itemDao.getById(original.inventoryItemId) ?: return@withTransaction
        val reversedDelta = original.quantityDelta.negate()
        val newOnHand = entity.onHandAmount.add(reversedDelta)
        itemDao.updateStockLevel(original.inventoryItemId, newOnHand, entity.avgUnitCostRubles, now)
        val movement = StockMovementEntity(
            id = IdGenerator.newId(),
            inventoryItemId = original.inventoryItemId,
            reason = StockMovementReason.CORRECTION.name,
            quantityDelta = reversedDelta,
            unitCostAtMovementRubles = entity.avgUnitCostRubles,
            correctsMovementId = movementId,
            note = note,
            isDemo = entity.isDemo,
            createdAt = now,
        )
        movementDao.insert(movement)
        enqueueMovement(movement, now)
        itemDao.getById(original.inventoryItemId)?.let { enqueueItemSnapshot(it, now) }
    }
}

fun InventoryItemEntity.toStockLevel(): StockLevel = StockLevel(
    onHand = Quantity(onHandAmount),
    avgUnitCost = UnitCost(avgUnitCostRubles),
    minAllowed = Quantity(minAllowedAmount),
)
