package com.borzini.pos.core

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/**
 * Cost of one base unit (1 piece / 1 gram / 1 millilitre), kept at higher precision than money
 * (kopecks) so repeated weighted-average recalculations do not accumulate rounding drift.
 * Only converted down to [Money] (kopecks) at the point a concrete recipe line or purchase line
 * total is recorded.
 */
@JvmInline
value class UnitCost(val rublesPerUnit: BigDecimal) {

    fun costOf(quantity: Quantity, rounding: RoundingMode = RoundingMode.HALF_UP): Money =
        Money.fromRubles(rublesPerUnit.multiply(quantity.amount), rounding)

    companion object {
        val ZERO = UnitCost(BigDecimal.ZERO)
        private val MC = MathContext(16)

        fun of(rubles: BigDecimal): UnitCost = UnitCost(rubles)

        /** Cost per base unit implied by a package purchase: packageCost / (packageCount * unitsPerPackage). */
        fun fromPackagePurchase(packageCost: Money, packageCount: Int, unitsPerPackage: Quantity): UnitCost {
            require(packageCount > 0) { "packageCount must be positive" }
            require(unitsPerPackage.isPositive()) { "unitsPerPackage must be positive" }
            val totalUnits = unitsPerPackage.amount.multiply(BigDecimal(packageCount))
            return UnitCost(packageCost.rubles.divide(totalUnits, MC))
        }
    }
}

/**
 * Recomputes the weighted-average unit cost of a stock item after a new purchase is posted.
 *
 *   newAvgCost = (currentQty * currentAvgCost + incomingQty * incomingUnitCost) / (currentQty + incomingQty)
 *
 * If the item currently has zero or negative stock (allowed only when the "negative stock" policy
 * is enabled), the existing average is discarded in favour of the incoming purchase cost — you
 * cannot weight-average against a meaningless or negative base quantity.
 */
object WeightedAverageCost {
    private val MC = MathContext(16)

    fun recalculate(
        currentQuantity: Quantity,
        currentAvgCost: UnitCost,
        incomingQuantity: Quantity,
        incomingUnitCost: UnitCost,
    ): UnitCost {
        require(incomingQuantity.isPositive()) { "incomingQuantity must be positive for a purchase" }

        if (currentQuantity.amount.signum() <= 0) {
            return incomingUnitCost
        }

        val currentValue = currentQuantity.amount.multiply(currentAvgCost.rublesPerUnit)
        val incomingValue = incomingQuantity.amount.multiply(incomingUnitCost.rublesPerUnit)
        val totalQuantity = currentQuantity.amount.add(incomingQuantity.amount)

        // totalQuantity > 0 is guaranteed here: currentQuantity > 0 (checked above) and
        // incomingQuantity > 0 (required), so this division is always safe.
        return UnitCost(currentValue.add(incomingValue).divide(totalQuantity, MC))
    }
}

data class RecipeLine(
    val ingredientName: String,
    val quantityPerPortion: Quantity,
    val unitCost: UnitCost,
)

/**
 * Total cost of one portion made from a recipe. Each ingredient line is rounded to the nearest
 * kopeck first and the rounded lines are summed, mirroring how a paper recipe costing sheet
 * would be filled in and keeping the stored per-sale cost breakdown reproducible line by line.
 */
object RecipeCostCalculator {
    fun costPerPortion(lines: List<RecipeLine>): Money =
        Money.sum(lines.map { it.unitCost.costOf(it.quantityPerPortion) })
}

data class MarginResult(
    val price: Money,
    val cost: Money,
    val grossProfit: Money,
    /** Null when price is zero (margin is undefined, not infinite or zero). */
    val marginPercent: BigDecimal?,
)

object MarginCalculator {
    private val MC = MathContext(8)

    fun calculate(price: Money, cost: Money): MarginResult {
        val grossProfit = price - cost
        val marginPercent = if (price.isZero()) {
            null
        } else {
            grossProfit.rubles.divide(price.rubles, MC).multiply(BigDecimal(100))
                .setScale(1, RoundingMode.HALF_UP)
        }
        return MarginResult(price, cost, grossProfit, marginPercent)
    }
}
