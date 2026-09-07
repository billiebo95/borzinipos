package com.borzini.pos.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal

class FinancialsTest {

    @Test
    fun `scenario 5 - cash and cashless are kept separate through the whole summary`() {
        val sales = listOf(
            SaleForStats(Money.fromRubles(BigDecimal("300")), Money.fromRubles(BigDecimal("100")), PaymentMethod.CASH),
            SaleForStats(Money.fromRubles(BigDecimal("200")), Money.fromRubles(BigDecimal("80")), PaymentMethod.CASHLESS),
        )
        val summary = FinancialsAggregator.aggregate(sales, emptyList(), emptyList())

        assertEquals(Money.fromRubles(BigDecimal("300")), summary.cashSales)
        assertEquals(Money.fromRubles(BigDecimal("200")), summary.cashlessSales)
        assertEquals(Money.fromRubles(BigDecimal("500")), summary.salesBeforeReturns)
    }

    @Test
    fun `net profit subtracts operating expenses, gross profit does not`() {
        val sales = listOf(
            SaleForStats(Money.fromRubles(BigDecimal("500")), Money.fromRubles(BigDecimal("200")), PaymentMethod.CASH),
        )
        val expenses = listOf(ExpenseForStats(Money.fromRubles(BigDecimal("100")), ExpenseCategory.RENT))
        val summary = FinancialsAggregator.aggregate(sales, emptyList(), expenses)

        assertEquals(Money.fromRubles(BigDecimal("300")), summary.grossProfit) // 500 - 200, expenses NOT involved
        assertEquals(Money.fromRubles(BigDecimal("200")), summary.netProfit) // 300 - 100
    }

    @Test
    fun `automatic acquiring fee suppresses a duplicate manual entry for the same fee`() {
        val sales = listOf(
            SaleForStats(Money.fromRubles(BigDecimal("1000")), Money.ZERO, PaymentMethod.CASHLESS),
        )
        val expenses = listOf(
            ExpenseForStats(Money.fromRubles(BigDecimal("20")), ExpenseCategory.ACQUIRING_FEE), // manual, would double count
            ExpenseForStats(Money.fromRubles(BigDecimal("50")), ExpenseCategory.RENT),
        )
        val autoFee = Money.fromRubles(BigDecimal("18")) // computed automatically from cashless turnover

        val summary = FinancialsAggregator.aggregate(sales, emptyList(), expenses, autoAcquiringFee = autoFee)

        // 18 (auto) + 50 (rent) = 68, the manual 20 acquiring-fee entry is ignored
        assertEquals(Money.fromRubles(BigDecimal("68")), summary.operatingExpenses)
    }

    @Test
    fun `a return reduces revenue and puts restocked cogs back, but only what was actually restocked`() {
        val sales = listOf(
            SaleForStats(Money.fromRubles(BigDecimal("500")), Money.fromRubles(BigDecimal("200")), PaymentMethod.CASH),
        )
        val returns = listOf(
            ReturnForStats(Money.fromRubles(BigDecimal("500")), PaymentMethod.CASH, restockedCogs = Money.ZERO),
        )
        val summary = FinancialsAggregator.aggregate(sales, returns, emptyList())

        assertEquals(Money.ZERO, summary.revenueAfterReturns)
        assertEquals(Money.fromRubles(BigDecimal("200")), summary.costOfGoodsSold) // nothing restocked -> COGS stands
    }

    @Test
    fun `average check formula is sales before returns divided by number of receipts`() {
        val sales = listOf(
            SaleForStats(Money.fromRubles(BigDecimal("100")), Money.ZERO, PaymentMethod.CASH),
            SaleForStats(Money.fromRubles(BigDecimal("300")), Money.ZERO, PaymentMethod.CASH),
        )
        val summary = FinancialsAggregator.aggregate(sales, emptyList(), emptyList())
        assertEquals(Money.fromRubles(BigDecimal("200")), summary.averageCheck) // (100+300)/2
        assertEquals(2, summary.completedReceiptCount)
    }

    @Test
    fun `margin is undefined not zero when price is zero, and never divides by zero`() {
        val result = MarginCalculator.calculate(Money.ZERO, Money.fromRubles(BigDecimal("10")))
        assertNull(result.marginPercent)
    }

    @Test
    fun `margin percent for a typical drink`() {
        // price 250, cost 62.50 -> gross profit 187.50 -> margin 75.0%
        val result = MarginCalculator.calculate(Money.fromRubles(BigDecimal("250")), Money.fromRubles(BigDecimal("62.50")))
        assertEquals(Money.fromRubles(BigDecimal("187.50")), result.grossProfit)
        assertEquals(0, BigDecimal("75.0").compareTo(result.marginPercent))
    }
}
