package com.borzini.pos.data.db.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.math.BigDecimal

@Entity(tableName = "sales")
data class SaleEntity(
    /** Primary key IS the idempotency key generated when checkout started - see
     * com.borzini.pos.core.IdempotencyKey. Retrying the same checkout attempt (double tap,
     * app restart, a retried sync upload) reuses this same id, so inserting it twice is
     * naturally a no-op instead of a duplicate sale. */
    @PrimaryKey val id: String,
    /** Short human-friendly sequential number shown on screen/receipts, distinct from [id]. */
    val receiptNumber: Long,
    val createdAt: Long,
    /** CASH / CASHLESS - see com.borzini.pos.core.PaymentMethod. */
    val paymentMethod: String,
    val totalKopecks: Long,
    val cashReceivedKopecks: Long? = null,
    val changeGivenKopecks: Long? = null,
    /** Snapshotted total cost of goods for this sale - never recomputed from current prices. */
    val cogsKopecks: Long,
    val isFullyReturned: Boolean = false,
    val isPartiallyReturned: Boolean = false,
    val isDemo: Boolean = false,
)

@Entity(
    tableName = "sale_items",
    foreignKeys = [
        ForeignKey(
            entity = SaleEntity::class,
            parentColumns = ["id"],
            childColumns = ["saleId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("saleId")],
)
data class SaleItemEntity(
    @PrimaryKey val id: String,
    val saleId: String,
    val productId: String?,
    val variantId: String?,
    val productNameSnapshot: String,
    val variantNameSnapshot: String? = null,
    val unitPriceKopecks: Long,
    val quantity: Int,
    val lineTotalKopecks: Long,
    val lineCogsKopecks: Long,
    val returnedQuantity: Int = 0,
)

/** Exact ingredient/packaging deduction snapshot for one sale item - the "состав списания на момент продажи". */
@Entity(
    tableName = "sale_item_deductions",
    foreignKeys = [
        ForeignKey(
            entity = SaleItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["saleItemId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("saleItemId")],
)
data class SaleItemDeductionEntity(
    @PrimaryKey val id: String,
    val saleItemId: String,
    val inventoryItemId: String,
    val inventoryItemNameSnapshot: String,
    val quantityDeducted: BigDecimal,
    val unitCostAtSaleRubles: BigDecimal,
    val lineCostKopecks: Long,
)

@Entity(
    tableName = "returns",
    foreignKeys = [
        ForeignKey(
            entity = SaleEntity::class,
            parentColumns = ["id"],
            childColumns = ["saleId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("saleId")],
)
data class ReturnEntity(
    @PrimaryKey val id: String,
    val saleId: String,
    val createdAt: Long,
    val reason: String,
    val refundedKopecks: Long,
    /** DO_NOT_RESTOCK_INGREDIENTS / RESTOCK_INGREDIENTS - see com.borzini.pos.core.ReturnStockPolicy. */
    val restockPolicy: String,
    val isDemo: Boolean = false,
)

@Entity(
    tableName = "return_items",
    foreignKeys = [
        ForeignKey(
            entity = ReturnEntity::class,
            parentColumns = ["id"],
            childColumns = ["returnId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = SaleItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["saleItemId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("returnId"), Index("saleItemId")],
)
data class ReturnItemEntity(
    @PrimaryKey val id: String,
    val returnId: String,
    val saleItemId: String,
    val quantityReturned: Int,
    val refundedLineKopecks: Long,
    val restockedCogsKopecks: Long,
)
