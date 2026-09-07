package com.borzini.pos.core

import java.math.MathContext
import java.math.RoundingMode

enum class ExpenseCategory { RENT, SALARY, UTILITIES, ACQUIRING_FEE, TAXES, OTHER }

data class SaleForStats(val total: Money, val cogsAtSaleTime: Money, val paymentMethod: PaymentMethod)

data class ReturnForStats(
    val refundedAmount: Money,
    val paymentMethod: PaymentMethod,
    /** COGS put back on the shelf by this return, per ReturnStockPolicy; zero if ingredients were not restocked. */
    val restockedCogs: Money,
)

data class ExpenseForStats(val amount: Money, val category: ExpenseCategory)

/**
 * A period's full financial picture. Deliberately never exposes a "netProfit" that skips
 * [operatingExpenses] — the whole point is to stop revenue-minus-ingredients being mislabelled
 * as net profit.
 */
data class FinancialSummary(
    val salesBeforeReturns: Money,
    val cashSales: Money,
    val cashlessSales: Money,
    val returnsTotal: Money,
    val returnsCash: Money,
    val returnsCashless: Money,
    val revenueAfterReturns: Money,
    val costOfGoodsSold: Money,
    val grossProfit: Money,
    val operatingExpenses: Money,
    val netProfit: Money,
    val completedReceiptCount: Int,
    val averageCheck: Money,
)

/**
 * Aggregates raw sale/return/expense records for a period into [FinancialSummary].
 *
 * Average check formula (shown in the UI next to the number so it is never ambiguous):
 *   средний чек = выручка до вычета возвратов / количество чеков
 *
 * Acquiring fee double-counting guard: when [autoAcquiringFee] is supplied (calculated
 * automatically from cashless turnover), manual [ExpenseCategory.ACQUIRING_FEE] entries for the
 * same period are ignored in the total — only one of "automatic" or "manual" ever counts, never
 * both. Pass `autoAcquiringFee = null` to use manual entries only.
 */
object FinancialsAggregator {
    private val MC = MathContext(8)

    fun aggregate(
        sales: List<SaleForStats>,
        returns: List<ReturnForStats>,
        expenses: List<ExpenseForStats>,
        autoAcquiringFee: Money? = null,
    ): FinancialSummary {
        val salesBeforeReturns = Money.sum(sales.map { it.total })
        val cashSales = Money.sum(sales.filter { it.paymentMethod == PaymentMethod.CASH }.map { it.total })
        val cashlessSales = Money.sum(sales.filter { it.paymentMethod == PaymentMethod.CASHLESS }.map { it.total })

        val returnsTotal = Money.sum(returns.map { it.refundedAmount })
        val returnsCash = Money.sum(returns.filter { it.paymentMethod == PaymentMethod.CASH }.map { it.refundedAmount })
        val returnsCashless =
            Money.sum(returns.filter { it.paymentMethod == PaymentMethod.CASHLESS }.map { it.refundedAmount })

        val revenueAfterReturns = salesBeforeReturns - returnsTotal

        val cogsFromSales = Money.sum(sales.map { it.cogsAtSaleTime })
        val cogsRestockedByReturns = Money.sum(returns.map { it.restockedCogs })
        val costOfGoodsSold = cogsFromSales - cogsRestockedByReturns

        val grossProfit = revenueAfterReturns - costOfGoodsSold

        val manualExpenseTotal = Money.sum(
            expenses
                .filter { autoAcquiringFee == null || it.category != ExpenseCategory.ACQUIRING_FEE }
                .map { it.amount },
        )
        val operatingExpenses = manualExpenseTotal + (autoAcquiringFee ?: Money.ZERO)

        val netProfit = grossProfit - operatingExpenses

        val averageCheck = if (sales.isEmpty()) {
            Money.ZERO
        } else {
            Money.fromRubles(
                salesBeforeReturns.rubles.divide(sales.size.toBigDecimal(), MC),
                RoundingMode.HALF_UP,
            )
        }

        return FinancialSummary(
            salesBeforeReturns = salesBeforeReturns,
            cashSales = cashSales,
            cashlessSales = cashlessSales,
            returnsTotal = returnsTotal,
            returnsCash = returnsCash,
            returnsCashless = returnsCashless,
            revenueAfterReturns = revenueAfterReturns,
            costOfGoodsSold = costOfGoodsSold,
            grossProfit = grossProfit,
            operatingExpenses = operatingExpenses,
            netProfit = netProfit,
            completedReceiptCount = sales.size,
            averageCheck = averageCheck,
        )
    }
}
