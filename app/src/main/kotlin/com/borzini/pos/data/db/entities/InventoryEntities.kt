package com.borzini.pos.data.db.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.math.BigDecimal

@Entity(tableName = "inventory_items")
data class InventoryItemEntity(
    @PrimaryKey val id: String,
    val name: String,
    val category: String,
    /** PIECE / GRAM / MILLILITRE - see com.borzini.pos.core.BaseUnit. */
    val baseUnit: String,
    val onHandAmount: BigDecimal,
    /** Weighted-average cost per 1 base unit, kept at high precision - see com.borzini.pos.core.UnitCost. */
    val avgUnitCostRubles: BigDecimal,
    val minAllowedAmount: BigDecimal,
    val supplier: String? = null,
    val isArchived: Boolean = false,
    val sortOrder: Int = 0,
    val isDemo: Boolean = false,
    val updatedAt: Long,
)

@Entity(tableName = "purchases")
data class PurchaseEntity(
    @PrimaryKey val id: String,
    val date: Long,
    val invoiceNumber: String? = null,
    val supplier: String,
    val totalKopecks: Long,
    val comment: String? = null,
    val attachmentUri: String? = null,
    /** A draft purchase never touched stock; posting it is what runs WeightedAverageCost and writes movements. */
    val isPosted: Boolean = false,
    val isDemo: Boolean = false,
    val createdAt: Long,
    val postedAt: Long? = null,
)

@Entity(
    tableName = "purchase_lines",
    foreignKeys = [
        ForeignKey(
            entity = PurchaseEntity::class,
            parentColumns = ["id"],
            childColumns = ["purchaseId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = InventoryItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["inventoryItemId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("purchaseId"), Index("inventoryItemId")],
)
data class PurchaseLineEntity(
    @PrimaryKey val id: String,
    val purchaseId: String,
    val inventoryItemId: String,
    val packageCount: Int,
    val unitsPerPackage: BigDecimal,
    val packageCostKopecks: Long,
    val totalCostKopecks: Long,
    val totalBaseUnits: BigDecimal,
)

@Entity(
    tableName = "stock_movements",
    foreignKeys = [
        ForeignKey(
            entity = InventoryItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["inventoryItemId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("inventoryItemId"), Index("createdAt")],
)
data class StockMovementEntity(
    @PrimaryKey val id: String,
    val inventoryItemId: String,
    /** See com.borzini.pos.core.StockMovementReason. */
    val reason: String,
    /** Signed: positive = stock increased, negative = stock decreased. */
    val quantityDelta: BigDecimal,
    val unitCostAtMovementRubles: BigDecimal,
    val relatedPurchaseId: String? = null,
    val relatedSaleId: String? = null,
    val relatedReturnId: String? = null,
    /** Set when this movement corrects an earlier mistaken one - the original row is kept, never deleted. */
    val correctsMovementId: String? = null,
    val note: String? = null,
    val isDemo: Boolean = false,
    val createdAt: Long,
)
