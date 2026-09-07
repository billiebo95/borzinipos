package com.borzini.pos.core

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Exact money value for the Russian ruble, stored as an integer number of kopecks (1/100 RUB).
 *
 * Using an integer minor unit instead of Double/Float removes binary floating point rounding
 * errors entirely: addition, subtraction and multiplication by an integer quantity are exact.
 * The only place rounding can occur is converting a fractional calculation (e.g. a percentage,
 * or a cost-per-millilitre times a fractional quantity) back into kopecks, which always happens
 * through [BigDecimal] with an explicit [RoundingMode.HALF_UP] so behaviour is deterministic and
 * testable rather than left to platform float rounding.
 */
@JvmInline
value class Money private constructor(val kopecks: Long) : Comparable<Money> {

    val rubles: BigDecimal
        get() = BigDecimal(kopecks).movePointLeft(2)

    operator fun plus(other: Money): Money = Money(kopecks + other.kopecks)
    operator fun minus(other: Money): Money = Money(kopecks - other.kopecks)
    operator fun unaryMinus(): Money = Money(-kopecks)
    operator fun times(factor: Int): Money = Money(kopecks * factor)

    /** Multiplies by an arbitrary-precision quantity (e.g. a fractional recipe amount), rounding to the nearest kopeck. */
    fun times(factor: BigDecimal, rounding: RoundingMode = RoundingMode.HALF_UP): Money =
        fromRubles(rubles.multiply(factor), rounding)

    fun isNegative(): Boolean = kopecks < 0
    fun isZero(): Boolean = kopecks == 0L
    fun isPositive(): Boolean = kopecks > 0

    override fun compareTo(other: Money): Int = kopecks.compareTo(other.kopecks)

    /** Formats as "1 234,56 ₽": a space every three digits, comma as the decimal separator. */
    fun format(): String {
        val negative = kopecks < 0
        val abs = kotlin.math.abs(kopecks)
        val whole = abs / 100
        val frac = abs % 100
        val wholeStr = whole.toString().reversed().chunked(3).joinToString(" ").reversed()
        val sign = if (negative) "-" else ""
        return "$sign$wholeStr,${frac.toString().padStart(2, '0')} ₽"
    }

    companion object {
        val ZERO = Money(0)

        fun ofKopecks(kopecks: Long): Money = Money(kopecks)

        fun fromRubles(rubles: BigDecimal, rounding: RoundingMode = RoundingMode.HALF_UP): Money =
            Money(rubles.movePointRight(2).setScale(0, rounding).longValueExact())

        fun fromRubles(rubles: String): Money = fromRubles(BigDecimal(rubles))

        fun sum(values: Iterable<Money>): Money = values.fold(ZERO) { acc, m -> acc + m }
    }
}
