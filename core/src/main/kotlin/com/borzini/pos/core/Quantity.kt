package com.borzini.pos.core

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/** Base unit an inventory item is tracked in. */
enum class BaseUnit(val displayName: String) {
    PIECE("шт"),
    GRAM("г"),
    MILLILITRE("мл");
}

/**
 * A fractional amount of a [BaseUnit] (e.g. 100.5 ml of syrup, 0.5 g of cinnamon).
 * Backed by [BigDecimal] so recipes and stock levels never lose precision to Double rounding.
 */
@JvmInline
value class Quantity(val amount: BigDecimal) : Comparable<Quantity> {

    operator fun plus(other: Quantity): Quantity = Quantity(amount + other.amount)
    operator fun minus(other: Quantity): Quantity = Quantity(amount - other.amount)
    operator fun times(factor: Int): Quantity = Quantity(amount.multiply(BigDecimal(factor)))
    operator fun unaryMinus(): Quantity = Quantity(-amount)

    fun isNegative(): Boolean = amount.signum() < 0
    fun isZero(): Boolean = amount.signum() == 0
    fun isPositive(): Boolean = amount.signum() > 0

    override fun compareTo(other: Quantity): Int = amount.compareTo(other.amount)

    /** How many times [portion] fits into this quantity (used for package -> base unit conversion checks). */
    fun dividedBy(portion: Quantity, mc: MathContext = MathContext(12)): BigDecimal {
        require(!portion.isZero()) { "Division by zero quantity" }
        return amount.divide(portion.amount, mc)
    }

    companion object {
        val ZERO = Quantity(BigDecimal.ZERO)

        fun of(value: String): Quantity = Quantity(BigDecimal(value))
        fun of(value: Double): Quantity = Quantity(BigDecimal.valueOf(value))
        fun of(value: Long): Quantity = Quantity(BigDecimal.valueOf(value))
    }
}
