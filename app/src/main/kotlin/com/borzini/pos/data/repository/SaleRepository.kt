package com.borzini.pos.data.repository

import androidx.room.withTransaction
import com.borzini.pos.core.CashPaymentCalculator
import com.borzini.pos.core.CashPaymentResult
import com.borzini.pos.core.InventoryMath
import com.borzini.pos.core.Money
import com.borzini.pos.core.NegativeStockPolicy
import com.borzini.pos.core.PaymentMethod
import com.borzini.pos.core.Quantity
import com.borzini.pos.core.StockDeductionResult
import com.borzini.pos.core.StockMovementReason
import com.borzini.pos.core.UnitCost
import com.borzini.pos.data.db.BorziniDatabase
import com.borzini.pos.data.db.entities.InventoryItemEntity
import com.borzini.pos.data.db.entities.SaleEntity
import com.borzini.pos.data.db.entities.SaleItemDeductionEntity
import com.borzini.pos.data.db.entities.SaleItemEntity
import com.borzini.pos.data.db.entities.StockMovementEntity
import com.borzini.pos.data.db.entities.SyncEntityType
import com.borzini.pos.data.db.entities.SyncOperation
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray
import org.json.JSONObject

data class CartLine(
    val productId: String?,
    val productName: String,
    val variantId: String?,
    val variantName: String?,
    val unitPrice: Money,
    val quantity: Int,
    val isSimpleProduct: Boolean,
    val simpleInventoryItemId: String?,
)

data class StockShortage(val ingredientName: String, val available: java.math.BigDecimal, val required: java.math.BigDecimal)

sealed class CompleteSaleResult {
    data class Success(val sale: SaleEntity, val change: Money) : CompleteSaleResult()
    data class InsufficientStock(val shortages: List<StockShortage>) : CompleteSaleResult()
    data class InsufficientCash(val shortfall: Money) : CompleteSaleResult()
}

private class InsufficientStockException(val shortages: List<StockShortage>) : Exception()
private class InsufficientCashException(val shortfall: Money) : Exception()

/**
 * Owns the one operation the whole app exists for: turning a cart into a completed sale.
 * Everything happens inside a single Room transaction (record the receipt, deduct every
 * ingredient, log every stock movement, queue the sync row) - see section 4/6/9 of the spec on
 * why a crash must never leave a receipt without its stock deduction or vice versa.
 */
class SaleRepository(
    private val db: BorziniDatabase,
    private val syncQueue: SyncQueueHelper,
) {
    private val saleDao = db.saleDao()
    private val itemDao = db.inventoryItemDao()
    private val recipeDao = db.recipeLineDao()
    private val movementDao = db.stockMovementDao()

    fun observeSalesInRange(start: Long, end: Long, includeDemo: Boolean): Flow<List<SaleEntity>> =
        saleDao.observeInRange(start, end, includeDemo)

    fun observeItemsForSale(saleId: String) = saleDao.observeItemsForSale(saleId)

    suspend fun getSale(id: String): SaleEntity? = saleDao.getById(id)
    suspend fun getItemsForSale(saleId: String): List<SaleItemEntity> = saleDao.getItemsForSaleOnce(saleId)
    suspend fun getDeductionsForItem(saleItemId: String): List<SaleItemDeductionEntity> =
        saleDao.getDeductionsForItem(saleItemId)

    suspend fun completeSale(
        idempotencyKey: String,
        lines: List<CartLine>,
        paymentMethod: PaymentMethod,
        cashReceived: Money?,
        negativeStockPolicy: NegativeStockPolicy,
        now: Long,
        isDemo: Boolean,
    ): CompleteSaleResult {
        return try {
            db.withTransaction {
                val existing = saleDao.getById(idempotencyKey)
                if (existing != null) {
                    return@withTransaction CompleteSaleResult.Success(existing, changeOf(existing))
                }

                val receiptNumber = saleDao.nextReceiptNumber()
                var totalKopecks = 0L
                var cogsKopecks = 0L
                val saleItems = mutableListOf<SaleItemEntity>()
                val allDeductions = mutableListOf<SaleItemDeductionEntity>()
                val shortages = mutableListOf<StockShortage>()

                for (line in lines) {
                    val saleItemId = IdGenerator.newId()
                    val lineTotal = line.unitPrice * line.quantity
                    totalKopecks += lineTotal.kopecks

                    val requiredByItem: List<Pair<InventoryItemEntity, Quantity>> = if (line.isSimpleProduct) {
                        val item = itemDao.getById(requireNotNull(line.simpleInventoryItemId) { "simple product needs an inventory item" })
                        if (item == null) emptyList() else listOf(item to Quantity.of(line.quantity.toLong()))
                    } else {
                        val recipe = recipeDao.getForVariantOnce(requireNotNull(line.variantId) { "variant required" })
                        recipe.mapNotNull { rl ->
                            val item = itemDao.getById(rl.inventoryItemId) ?: return@mapNotNull null
                            item to Quantity(rl.quantityPerPortion) * line.quantity
                        }
                    }

                    var lineCogs = Money.ZERO
                    for ((item, requiredQty) in requiredByItem) {
                        when (val result = InventoryMath.deduct(item.name, item.toStockLevel(), requiredQty, negativeStockPolicy)) {
                            is StockDeductionResult.InsufficientStock ->
                                shortages += StockShortage(item.name, result.available.amount, result.required.amount)

                            is StockDeductionResult.Ok -> {
                                itemDao.updateStockLevel(
                                    item.id,
                                    result.newLevel.onHand.amount,
                                    result.newLevel.avgUnitCost.rublesPerUnit,
                                    now,
                                )
                                val lineCost = UnitCost(item.avgUnitCostRubles).costOf(requiredQty)
                                lineCogs += lineCost
                                allDeductions += SaleItemDeductionEntity(
                                    id = IdGenerator.newId(),
                                    saleItemId = saleItemId,
                                    inventoryItemId = item.id,
                                    inventoryItemNameSnapshot = item.name,
                                    quantityDeducted = requiredQty.amount,
                                    unitCostAtSaleRubles = item.avgUnitCostRubles,
                                    lineCostKopecks = lineCost.kopecks,
                                )
                            }
                        }
                    }
                    cogsKopecks += lineCogs.kopecks
                    saleItems += SaleItemEntity(
                        id = saleItemId,
                        saleId = idempotencyKey,
                        productId = line.productId,
                        variantId = line.variantId,
                        productNameSnapshot = line.productName,
                        variantNameSnapshot = line.variantName,
                        unitPriceKopecks = line.unitPrice.kopecks,
                        quantity = line.quantity,
                        lineTotalKopecks = lineTotal.kopecks,
                        lineCogsKopecks = lineCogs.kopecks,
                    )
                }

                if (shortages.isNotEmpty()) throw InsufficientStockException(shortages)

                val total = Money.ofKopecks(totalKopecks)
                var changeGivenKopecks: Long? = null
                if (paymentMethod == PaymentMethod.CASH) {
                    val received = requireNotNull(cashReceived) { "cashReceived required for a cash sale" }
                    when (val evalResult = CashPaymentCalculator.evaluate(total, received)) {
                        is CashPaymentResult.InsufficientAmount -> throw InsufficientCashException(evalResult.shortfall)
                        is CashPaymentResult.Ok -> changeGivenKopecks = evalResult.change.kopecks
                    }
                }

                val sale = SaleEntity(
                    id = idempotencyKey,
                    receiptNumber = receiptNumber,
                    createdAt = now,
                    paymentMethod = paymentMethod.name,
                    totalKopecks = total.kopecks,
                    cashReceivedKopecks = cashReceived?.kopecks,
                    changeGivenKopecks = changeGivenKopecks,
                    cogsKopecks = cogsKopecks,
                    isDemo = isDemo,
                )

                if (saleDao.insertSaleIfAbsent(sale) == -1L) {
                    // Lost a race with another writer inserting the exact same idempotency key.
                    val already = requireNotNull(saleDao.getById(idempotencyKey))
                    return@withTransaction CompleteSaleResult.Success(already, changeOf(already))
                }

                saleDao.insertItems(saleItems)
                if (allDeductions.isNotEmpty()) saleDao.insertDeductions(allDeductions)

                val movements = allDeductions.map { d ->
                    StockMovementEntity(
                        id = IdGenerator.newId(),
                        inventoryItemId = d.inventoryItemId,
                        reason = StockMovementReason.SALE_DEDUCTION.name,
                        quantityDelta = d.quantityDeducted.negate(),
                        unitCostAtMovementRubles = d.unitCostAtSaleRubles,
                        relatedSaleId = idempotencyKey,
                        isDemo = isDemo,
                        createdAt = now,
                    )
                }
                if (movements.isNotEmpty()) movementDao.insertAll(movements)

                syncQueue.enqueue(
                    SyncEntityType.SALE,
                    idempotencyKey,
                    SyncOperation.UPSERT,
                    buildSalePayload(sale, saleItems),
                    now,
                )

                CompleteSaleResult.Success(sale, Money.ofKopecks(changeGivenKopecks ?: 0L))
            }
        } catch (e: InsufficientStockException) {
            CompleteSaleResult.InsufficientStock(e.shortages)
        } catch (e: InsufficientCashException) {
            CompleteSaleResult.InsufficientCash(e.shortfall)
        }
    }

    private fun changeOf(sale: SaleEntity): Money =
        if (sale.paymentMethod == PaymentMethod.CASH.name) Money.ofKopecks(sale.changeGivenKopecks ?: 0L) else Money.ZERO

    private fun buildSalePayload(sale: SaleEntity, items: List<SaleItemEntity>): JSONObject = JSONObject().apply {
        put("id", sale.id)
        put("receiptNumber", sale.receiptNumber)
        put("createdAt", sale.createdAt)
        put("paymentMethod", sale.paymentMethod)
        put("totalKopecks", sale.totalKopecks)
        put("cogsKopecks", sale.cogsKopecks)
        put(
            "items",
            JSONArray().apply {
                items.forEach { item ->
                    put(
                        JSONObject().apply {
                            put("id", item.id)
                            put("productNameSnapshot", item.productNameSnapshot)
                            put("variantNameSnapshot", item.variantNameSnapshot)
                            put("unitPriceKopecks", item.unitPriceKopecks)
                            put("quantity", item.quantity)
                            put("lineTotalKopecks", item.lineTotalKopecks)
                            put("lineCogsKopecks", item.lineCogsKopecks)
                        },
                    )
                }
            },
        )
    }
}
