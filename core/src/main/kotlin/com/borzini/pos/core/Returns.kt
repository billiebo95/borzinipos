package com.borzini.pos.core

sealed class ReturnValidationResult {
    data object Ok : ReturnValidationResult()
    data class ExceedsSoldQuantity(val requested: Quantity, val maxAllowed: Quantity) : ReturnValidationResult()
    data class InvalidQuantity(val requested: Quantity) : ReturnValidationResult()
}

data class SaleLineState(
    val soldQuantity: Quantity,
    val alreadyReturnedQuantity: Quantity,
)

/**
 * A return (full or partial) can never take a line's returned quantity above what was actually
 * sold on the original receipt, across possibly multiple partial returns over time.
 */
object ReturnValidator {
    fun validate(line: SaleLineState, requestedReturnQuantity: Quantity): ReturnValidationResult {
        if (!requestedReturnQuantity.isPositive()) {
            return ReturnValidationResult.InvalidQuantity(requestedReturnQuantity)
        }
        val remaining = line.soldQuantity - line.alreadyReturnedQuantity
        return if (requestedReturnQuantity > remaining) {
            ReturnValidationResult.ExceedsSoldQuantity(requestedReturnQuantity, remaining)
        } else {
            ReturnValidationResult.Ok
        }
    }
}

/** Whether returning this line should also restock its recipe ingredients. */
enum class ReturnStockPolicy {
    /** Default for a prepared drink: the ingredients were consumed making it, they do not come back. */
    DO_NOT_RESTOCK_INGREDIENTS,

    /** For an unmade/cancelled order, or a simple resold item (e.g. a bottled water): reverse the deduction. */
    RESTOCK_INGREDIENTS,
}
