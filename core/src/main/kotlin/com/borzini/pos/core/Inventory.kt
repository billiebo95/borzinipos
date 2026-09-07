package com.borzini.pos.core

/** Why a stock movement happened — kept explicit so the movement log stays a real audit trail. */
enum class StockMovementReason {
    PURCHASE_RECEIPT,
    SALE_DEDUCTION,
    MANUAL_WRITE_OFF,
    INVENTORY_ADJUSTMENT,
    RETURN_RESTOCK,
    CORRECTION,
}

data class StockLevel(
    val onHand: Quantity,
    val avgUnitCost: UnitCost,
    val minAllowed: Quantity,
) {
    val valueOnHand: Money get() = avgUnitCost.costOf(onHand)
    fun isLowStock(): Boolean = onHand <= minAllowed
}

sealed class StockDeductionResult {
    data class Ok(val newLevel: StockLevel) : StockDeductionResult()
    data class InsufficientStock(val ingredientName: String, val available: Quantity, val required: Quantity) :
        StockDeductionResult()
}

/**
 * Whether a sale is allowed to deduct an ingredient below what is on hand.
 * Default is to block the sale; enabling negative stock is an explicit, warned opt-in setting.
 */
enum class NegativeStockPolicy { BLOCK_SALE, ALLOW_NEGATIVE }

object InventoryMath {
    /**
     * Checks a single ingredient deduction. Never divides by anything, never mutates history —
     * callers persist the resulting movement + new level themselves inside one DB transaction.
     */
    fun deduct(
        ingredientName: String,
        level: StockLevel,
        required: Quantity,
        policy: NegativeStockPolicy,
    ): StockDeductionResult {
        require(required.isPositive() || required.isZero()) { "required quantity cannot be negative" }
        val remaining = level.onHand - required
        if (remaining.isNegative() && policy == NegativeStockPolicy.BLOCK_SALE) {
            return StockDeductionResult.InsufficientStock(ingredientName, level.onHand, required)
        }
        // Cost basis (avgUnitCost) is left unchanged by a deduction — weighted average cost only
        // moves on a new purchase (see WeightedAverageCost). This is what keeps "allow negative
        // stock" safe: there is no division by the (possibly zero or negative) remaining quantity
        // anywhere in this path, so a negative balance cannot produce a div-by-zero or an
        // undefined cost. The next purchase's WeightedAverageCost.recalculate call already treats
        // a <= 0 current quantity as "discard the stale average, adopt the incoming cost".
        return StockDeductionResult.Ok(level.copy(onHand = remaining))
    }

    fun restock(level: StockLevel, quantity: Quantity): StockLevel {
        require(quantity.isPositive()) { "restock quantity must be positive" }
        return level.copy(onHand = level.onHand + quantity)
    }

    /** Full physical count replaces on-hand and returns the signed difference for the adjustment log. */
    fun inventoryCountDifference(level: StockLevel, countedQuantity: Quantity): Quantity =
        countedQuantity - level.onHand
}
