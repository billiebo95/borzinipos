package com.borzini.pos.data.repository

import com.borzini.pos.core.BusinessCalendar
import com.borzini.pos.core.ExpenseCategory
import com.borzini.pos.core.ExpenseForStats
import com.borzini.pos.core.FinancialSummary
import com.borzini.pos.core.FinancialsAggregator
import com.borzini.pos.core.Money
import com.borzini.pos.core.PaymentMethod
import com.borzini.pos.core.ReturnForStats
import com.borzini.pos.core.SaleForStats
import com.borzini.pos.data.db.BorziniDatabase
import com.borzini.pos.data.db.dao.TopProductRow
import kotlinx.coroutines.flow.first
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.ZoneId

data class DayPoint(val date: LocalDate, val revenue: Money, val receiptCount: Int, val grossProfit: Money)
data class CategoryBreakdownRow(val categoryName: String, val total: Money)

class StatsRepository(private val db: BorziniDatabase) {
    private val saleDao = db.saleDao()
    private val returnDao = db.returnDao()
    private val expenseDao = db.expenseDao()
    private val categoryDao = db.categoryDao()
    private val productDao = db.productDao()

    suspend fun getSummary(
        start: Long,
        end: Long,
        includeDemo: Boolean,
        autoAcquiringFeePercent: Float?,
    ): FinancialSummary {
        val sales = saleDao.observeInRange(start, end, includeDemo).first().map {
            SaleForStats(Money.ofKopecks(it.totalKopecks), Money.ofKopecks(it.cogsKopecks), PaymentMethod.valueOf(it.paymentMethod))
        }
        // Refund payment method mirrors the original sale's method; look it up per return.
        val returnsWithMethod = returnDao.observeInRange(start, end, includeDemo).first().map { r ->
            val originalSale = saleDao.getById(r.saleId)
            val method = originalSale?.paymentMethod?.let { PaymentMethod.valueOf(it) } ?: PaymentMethod.CASH
            val restocked = returnDao.getItemsForReturn(r.id).sumOf { it.restockedCogsKopecks }
            ReturnForStats(Money.ofKopecks(r.refundedKopecks), method, Money.ofKopecks(restocked))
        }
        val expenses = expenseDao.observeInRange(start, end, includeDemo).first().map {
            ExpenseForStats(Money.ofKopecks(it.amountKopecks), runCatching { ExpenseCategory.valueOf(it.category) }.getOrDefault(ExpenseCategory.OTHER))
        }

        val autoFee = autoAcquiringFeePercent?.let { percent ->
            val cashlessTotal = sales.filter { it.paymentMethod == PaymentMethod.CASHLESS }.sumOf { it.total.kopecks }
            // percent.toString(), not toDouble(): a Float -> BigDecimal(Double) conversion can pick up
            // binary floating point noise (e.g. 1.8 becoming 1.7999999...); parsing the decimal text
            // representation instead keeps this exact, same as everywhere else money is handled.
            val percentAsDecimal = BigDecimal(percent.toString())
            Money.fromRubles(
                BigDecimal(cashlessTotal).movePointLeft(2).multiply(percentAsDecimal).movePointLeft(2),
                RoundingMode.HALF_UP,
            )
        }

        return FinancialsAggregator.aggregate(sales, returnsWithMethod, expenses, autoFee)
    }

    /** Mon..Sun revenue/receipt-count/gross-profit for the week containing [anyDateInWeek], zero-filled. */
    suspend fun getWeekChart(anyDateInWeek: LocalDate, zone: ZoneId, includeDemo: Boolean): List<DayPoint> =
        BusinessCalendar.weekDates(anyDateInWeek).map { date ->
            val range = BusinessCalendar.dayRange(date, zone)
            val sales = saleDao.observeInRange(range.startInclusive.toEpochMilli(), range.endExclusive.toEpochMilli(), includeDemo).first()
            val revenue = Money.sum(sales.map { Money.ofKopecks(it.totalKopecks) })
            val cogs = Money.sum(sales.map { Money.ofKopecks(it.cogsKopecks) })
            DayPoint(date, revenue, sales.size, revenue - cogs)
        }

    suspend fun getTopProducts(start: Long, end: Long, includeDemo: Boolean, limit: Int = 10): List<TopProductRow> =
        saleDao.topProducts(start, end, includeDemo, limit)

    suspend fun getCategoryBreakdown(start: Long, end: Long, includeDemo: Boolean): List<CategoryBreakdownRow> {
        val rows = saleDao.salesByCategory(start, end, includeDemo)
        val categories = categoryDao.observeAll().first().associateBy { it.id }
        return rows.map { row ->
            val name = row.categoryId?.let { categories[it]?.name } ?: "Без категории"
            CategoryBreakdownRow(name, Money.ofKopecks(row.totalKopecks))
        }
    }
}
