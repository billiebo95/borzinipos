package com.borzini.pos.data.repository

import androidx.room.withTransaction
import com.borzini.pos.core.Money
import com.borzini.pos.core.Quantity
import com.borzini.pos.core.StockMovementReason
import com.borzini.pos.core.UnitCost
import com.borzini.pos.core.WeightedAverageCost
import com.borzini.pos.data.db.BorziniDatabase
import com.borzini.pos.data.db.entities.PurchaseEntity
import com.borzini.pos.data.db.entities.PurchaseLineEntity
import com.borzini.pos.data.db.entities.StockMovementEntity
import com.borzini.pos.data.db.entities.SyncEntityType
import com.borzini.pos.data.db.entities.SyncOperation
import kotlinx.coroutines.flow.Flow
import org.json.JSONObject
import java.math.BigDecimal

data class PurchaseLineInput(
    val inventoryItemId: String,
    val packageCount: Int,
    val unitsPerPackage: Quantity,
    val packageCost: Money,
)

class PurchaseRepository(
    private val db: BorziniDatabase,
    private val syncQueue: SyncQueueHelper,
) {
    private val purchaseDao = db.purchaseDao()
    private val itemDao = db.inventoryItemDao()
    private val movementDao = db.stockMovementDao()

    fun observeAll(): Flow<List<PurchaseEntity>> = purchaseDao.observeAll()
    fun observeLines(purchaseId: String): Flow<List<PurchaseLineEntity>> = purchaseDao.observeLinesForPurchase(purchaseId)

    /** Saves a draft purchase. A draft never touches stock or cost - only [postPurchase] does. */
    suspend fun saveDraft(
        purchaseId: String?,
        date: Long,
        invoiceNumber: String?,
        supplier: String,
        comment: String?,
        attachmentUri: String?,
        lines: List<PurchaseLineInput>,
        now: Long,
        isDemo: Boolean = false,
    ): String = db.withTransaction {
        val id = purchaseId ?: IdGenerator.newId()
        val existing = if (purchaseId != null) purchaseDao.getById(purchaseId) else null
        check(existing?.isPosted != true) { "Cannot edit a purchase that was already posted" }

        val total = Money.sum(lines.map { it.packageCost * it.packageCount })
        purchaseDao.upsert(
            PurchaseEntity(
                id = id,
                date = date,
                invoiceNumber = invoiceNumber,
                supplier = supplier,
                totalKopecks = total.kopecks,
                comment = comment,
                attachmentUri = attachmentUri,
                isPosted = false,
                isDemo = isDemo,
                createdAt = existing?.createdAt ?: now,
            ),
        )
        purchaseDao.deleteLinesForPurchase(id)
        purchaseDao.insertLines(
            lines.map { line ->
                PurchaseLineEntity(
                    id = IdGenerator.newId(),
                    purchaseId = id,
                    inventoryItemId = line.inventoryItemId,
                    packageCount = line.packageCount,
                    unitsPerPackage = line.unitsPerPackage.amount,
                    packageCostKopecks = line.packageCost.kopecks,
                    totalCostKopecks = (line.packageCost * line.packageCount).kopecks,
                    totalBaseUnits = line.unitsPerPackage.amount.multiply(BigDecimal(line.packageCount)),
                )
            },
        )
        id
    }

    /**
     * Posts a draft purchase: increases stock, recomputes each ingredient's weighted-average
     * cost, writes one stock movement per line, and queues everything for sync - all inside one
     * transaction. Posting an already-posted purchase again is a no-op (returns false).
     */
    suspend fun postPurchase(purchaseId: String, now: Long): Boolean = db.withTransaction {
        val purchase = purchaseDao.getById(purchaseId) ?: return@withTransaction false
        if (purchase.isPosted) return@withTransaction false

        val lines = purchaseDao.getLinesForPurchaseOnce(purchaseId)
        for (line in lines) {
            val item = itemDao.getById(line.inventoryItemId) ?: continue
            val incomingUnitCost = UnitCost.fromPackagePurchase(
                packageCost = Money.ofKopecks(line.packageCostKopecks),
                packageCount = line.packageCount,
                unitsPerPackage = Quantity(line.unitsPerPackage),
            )
            val newAvgCost = WeightedAverageCost.recalculate(
                currentQuantity = Quantity(item.onHandAmount),
                currentAvgCost = UnitCost(item.avgUnitCostRubles),
                incomingQuantity = Quantity(line.totalBaseUnits),
                incomingUnitCost = incomingUnitCost,
            )
            val newOnHand = item.onHandAmount.add(line.totalBaseUnits)
            itemDao.updateStockLevel(item.id, newOnHand, newAvgCost.rublesPerUnit, now)

            val movementId = IdGenerator.newId()
            movementDao.insert(
                StockMovementEntity(
                    id = movementId,
                    inventoryItemId = item.id,
                    reason = StockMovementReason.PURCHASE_RECEIPT.name,
                    quantityDelta = line.totalBaseUnits,
                    unitCostAtMovementRubles = incomingUnitCost.rublesPerUnit,
                    relatedPurchaseId = purchaseId,
                    isDemo = purchase.isDemo,
                    createdAt = now,
                ),
            )

            syncQueue.enqueue(
                SyncEntityType.INVENTORY_ITEM,
                item.id,
                SyncOperation.UPSERT,
                JSONObject().apply {
                    put("id", item.id)
                    put("name", item.name)
                    put("category", item.category)
                    put("baseUnit", item.baseUnit)
                    put("onHandAmount", newOnHand.toPlainString())
                    put("avgUnitCostRubles", newAvgCost.rublesPerUnit.toPlainString())
                    put("minAllowedAmount", item.minAllowedAmount.toPlainString())
                    put("isArchived", item.isArchived)
                },
                now,
            )
            syncQueue.enqueue(
                SyncEntityType.STOCK_MOVEMENT,
                movementId,
                SyncOperation.UPSERT,
                JSONObject().apply {
                    put("id", movementId)
                    put("inventoryItemId", item.id)
                    put("reason", StockMovementReason.PURCHASE_RECEIPT.name)
                    put("quantityDelta", line.totalBaseUnits.toPlainString())
                    put("unitCostAtMovementRubles", incomingUnitCost.rublesPerUnit.toPlainString())
                    put("relatedPurchaseId", purchaseId)
                    put("createdAt", now)
                },
                now,
            )
        }

        purchaseDao.markPosted(purchaseId, now)
        syncQueue.enqueue(
            SyncEntityType.PURCHASE,
            purchaseId,
            SyncOperation.UPSERT,
            JSONObject().apply {
                put("id", purchaseId)
                put("date", purchase.date)
                put("invoiceNumber", purchase.invoiceNumber)
                put("supplier", purchase.supplier)
                put("totalKopecks", purchase.totalKopecks)
                put("comment", purchase.comment)
                put("lineCount", lines.size)
            },
            now,
        )
        true
    }
}
