package com.borzini.pos.data.repository

import androidx.room.withTransaction
import com.borzini.pos.core.InventoryMath
import com.borzini.pos.core.Money
import com.borzini.pos.core.Quantity
import com.borzini.pos.core.ReturnStockPolicy
import com.borzini.pos.core.ReturnValidationResult
import com.borzini.pos.core.ReturnValidator
import com.borzini.pos.core.SaleLineState
import com.borzini.pos.core.StockMovementReason
import com.borzini.pos.data.db.BorziniDatabase
import com.borzini.pos.data.db.entities.ReturnEntity
import com.borzini.pos.data.db.entities.ReturnItemEntity
import com.borzini.pos.data.db.entities.StockMovementEntity
import com.borzini.pos.data.db.entities.SyncEntityType
import com.borzini.pos.data.db.entities.SyncOperation
import org.json.JSONObject
import java.math.MathContext
import java.math.RoundingMode

data class ReturnLineRequest(val saleItemId: String, val quantityToReturn: Int)

sealed class ProcessReturnResult {
    data class Ok(val returnId: String, val refunded: Money) : ProcessReturnResult()
    data class ExceedsSoldQuantity(val saleItemId: String, val maxAllowed: Int) : ProcessReturnResult()
    data object NothingToReturn : ProcessReturnResult()
}

private class ExceedsException(val saleItemId: String, val maxAllowed: Int) : Exception()

/**
 * Processes a full or partial return. Money is always refunded for whatever is returned; whether
 * ingredients go back on the shelf is a separate, explicit choice ([ReturnStockPolicy]) - default
 * is "no" for an already-prepared drink (see spec section 4). The original sale row is never
 * edited, only referenced - see ReturnEntity/ReturnItemEntity.
 */
class ReturnRepository(
    private val db: BorziniDatabase,
    private val syncQueue: SyncQueueHelper,
) {
    private val saleDao = db.saleDao()
    private val returnDao = db.returnDao()
    private val itemDao = db.inventoryItemDao()
    private val movementDao = db.stockMovementDao()
    private val mc = MathContext(12)

    suspend fun processReturn(
        saleId: String,
        lines: List<ReturnLineRequest>,
        reason: String,
        restockPolicy: ReturnStockPolicy,
        now: Long,
        isDemo: Boolean,
    ): ProcessReturnResult {
        if (lines.all { it.quantityToReturn <= 0 }) return ProcessReturnResult.NothingToReturn
        return try {
            db.withTransaction {
                val returnId = IdGenerator.newId()
                var totalRefundKopecks = 0L
                val returnItems = mutableListOf<ReturnItemEntity>()

                for (line in lines) {
                    if (line.quantityToReturn <= 0) continue
                    val saleItem = requireNotNull(saleDao.getItemById(line.saleItemId)) { "sale item not found" }
                    val alreadyReturned = returnDao.totalReturnedForSaleItem(line.saleItemId)
                    val state = SaleLineState(Quantity.of(saleItem.quantity.toLong()), Quantity.of(alreadyReturned.toLong()))
                    val validation = ReturnValidator.validate(state, Quantity.of(line.quantityToReturn.toLong()))
                    if (validation is ReturnValidationResult.ExceedsSoldQuantity) {
                        throw ExceedsException(line.saleItemId, validation.maxAllowed.amount.toInt())
                    }

                    val refundedLine = Money.ofKopecks(saleItem.unitPriceKopecks) * line.quantityToReturn
                    totalRefundKopecks += refundedLine.kopecks

                    var restockedCogsKopecks = 0L
                    if (restockPolicy == ReturnStockPolicy.RESTOCK_INGREDIENTS) {
                        val deductions = saleDao.getDeductionsForItem(line.saleItemId)
                        val ratio = line.quantityToReturn.toBigDecimal().divide(saleItem.quantity.toBigDecimal(), mc)
                        for (d in deductions) {
                            val restockQty = Quantity(d.quantityDeducted.multiply(ratio))
                            if (!restockQty.isPositive()) continue
                            val item = itemDao.getById(d.inventoryItemId) ?: continue
                            val newLevel = InventoryMath.restock(item.toStockLevel(), restockQty)
                            itemDao.updateStockLevel(item.id, newLevel.onHand.amount, item.avgUnitCostRubles, now)
                            movementDao.insert(
                                StockMovementEntity(
                                    id = IdGenerator.newId(),
                                    inventoryItemId = item.id,
                                    reason = StockMovementReason.RETURN_RESTOCK.name,
                                    quantityDelta = restockQty.amount,
                                    unitCostAtMovementRubles = item.avgUnitCostRubles,
                                    relatedReturnId = returnId,
                                    isDemo = isDemo,
                                    createdAt = now,
                                ),
                            )
                            // Restocked cost is valued at the ORIGINAL sale-time cost snapshot, so a
                            // period's cost-of-goods-sold is reduced by exactly what it was charged.
                            val restockedCost = Money.fromRubles(
                                d.unitCostAtSaleRubles.multiply(restockQty.amount),
                                RoundingMode.HALF_UP,
                            )
                            restockedCogsKopecks += restockedCost.kopecks
                        }
                    }

                    saleDao.updateItemReturnedQuantity(line.saleItemId, alreadyReturned + line.quantityToReturn)
                    returnItems += ReturnItemEntity(
                        id = IdGenerator.newId(),
                        returnId = returnId,
                        saleItemId = line.saleItemId,
                        quantityReturned = line.quantityToReturn,
                        refundedLineKopecks = refundedLine.kopecks,
                        restockedCogsKopecks = restockedCogsKopecks,
                    )
                }

                if (returnItems.isEmpty()) return@withTransaction ProcessReturnResult.NothingToReturn

                returnDao.insert(
                    ReturnEntity(
                        id = returnId,
                        saleId = saleId,
                        createdAt = now,
                        reason = reason,
                        refundedKopecks = totalRefundKopecks,
                        restockPolicy = restockPolicy.name,
                        isDemo = isDemo,
                    ),
                )
                returnDao.insertItems(returnItems)

                val allItems = saleDao.getItemsForSaleOnce(saleId)
                val allFullyReturned = allItems.all { it.returnedQuantity + (returnItems.find { r -> r.saleItemId == it.id }?.quantityReturned ?: 0) >= it.quantity }
                val anyReturned = allItems.any { it.returnedQuantity > 0 } || returnItems.isNotEmpty()
                saleDao.updateReturnFlags(saleId, allFullyReturned, anyReturned && !allFullyReturned)

                syncQueue.enqueue(
                    SyncEntityType.RETURN,
                    returnId,
                    SyncOperation.UPSERT,
                    JSONObject().apply {
                        put("id", returnId)
                        put("saleId", saleId)
                        put("createdAt", now)
                        put("reason", reason)
                        put("refundedKopecks", totalRefundKopecks)
                        put("restockPolicy", restockPolicy.name)
                    },
                    now,
                )

                ProcessReturnResult.Ok(returnId, Money.ofKopecks(totalRefundKopecks))
            }
        } catch (e: ExceedsException) {
            ProcessReturnResult.ExceedsSoldQuantity(e.saleItemId, e.maxAllowed)
        }
    }
}
