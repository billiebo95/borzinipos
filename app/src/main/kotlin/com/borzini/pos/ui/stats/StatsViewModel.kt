package com.borzini.pos.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.borzini.pos.core.BusinessCalendar
import com.borzini.pos.core.FinancialSummary
import com.borzini.pos.core.Money
import com.borzini.pos.core.StatsPeriodPreset
import com.borzini.pos.data.db.dao.TopProductRow
import com.borzini.pos.data.repository.CategoryBreakdownRow
import com.borzini.pos.data.repository.DayPoint
import com.borzini.pos.di.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class GoalProgress(val target: Money, val current: Money) {
    val percent: Int get() = if (target.isZero() || target.isNegative()) 0 else (current.kopecks * 100 / target.kopecks).toInt().coerceIn(0, 999)
    val remaining: Money get() = if (target > current) target - current else Money.ZERO
}

data class StatsUiState(
    val loading: Boolean = true,
    val preset: StatsPeriodPreset = StatsPeriodPreset.TODAY,
    val customStart: LocalDate? = null,
    val customEnd: LocalDate? = null,
    val summary: FinancialSummary? = null,
    val topProducts: List<TopProductRow> = emptyList(),
    val categoryBreakdown: List<CategoryBreakdownRow> = emptyList(),
    val weekChart: List<DayPoint> = emptyList(),
    val dailyGoal: GoalProgress? = null,
    val monthlyGoal: GoalProgress? = null,
)

enum class ChartMetric { REVENUE, RECEIPTS, PROFIT }

class StatsViewModel(private val container: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(StatsUiState())
    val state: StateFlow<StatsUiState> = _state

    val chartMetric = MutableStateFlow(ChartMetric.REVENUE)

    init {
        refresh(StatsPeriodPreset.TODAY)
    }

    fun refresh(preset: StatsPeriodPreset, customStart: LocalDate? = null, customEnd: LocalDate? = null) {
        _state.value = _state.value.copy(loading = true, preset = preset, customStart = customStart, customEnd = customEnd)
        viewModelScope.launch {
            val settings = container.settingsDataStore.settingsFlow.first()
            val zone = ZoneId.of(settings.timezoneId)
            val range = BusinessCalendar.resolve(preset, zone, Instant.now(), customStart, customEnd)
            val autoFee = if (settings.autoAcquiringFeeEnabled) settings.autoAcquiringFeePercent else null

            val summary = container.statsRepository.getSummary(
                range.startInclusive.toEpochMilli(), range.endExclusive.toEpochMilli(), settings.demoModeEnabled, autoFee,
            )
            val top = container.statsRepository.getTopProducts(range.startInclusive.toEpochMilli(), range.endExclusive.toEpochMilli(), settings.demoModeEnabled)
            val byCategory = container.statsRepository.getCategoryBreakdown(range.startInclusive.toEpochMilli(), range.endExclusive.toEpochMilli(), settings.demoModeEnabled)
            val today = BusinessCalendar.today(zone)
            val weekChart = container.statsRepository.getWeekChart(today, zone, settings.demoModeEnabled)

            val dailyKey = today.toString()
            val monthlyKey = today.toString().substring(0, 7)
            val dailyGoalEntity = container.goalRepository.observeDailyGoal(dailyKey).first()
            val monthlyGoalEntity = container.goalRepository.observeMonthlyGoal(monthlyKey).first()
            val todayRange = BusinessCalendar.dayRange(today, zone)
            val monthRange = BusinessCalendar.monthRange(java.time.YearMonth.from(today), zone)
            val todaySummary = container.statsRepository.getSummary(todayRange.startInclusive.toEpochMilli(), todayRange.endExclusive.toEpochMilli(), settings.demoModeEnabled, autoFee)
            val monthSummary = container.statsRepository.getSummary(monthRange.startInclusive.toEpochMilli(), monthRange.endExclusive.toEpochMilli(), settings.demoModeEnabled, autoFee)

            _state.value = StatsUiState(
                loading = false,
                preset = preset,
                customStart = customStart,
                customEnd = customEnd,
                summary = summary,
                topProducts = top,
                categoryBreakdown = byCategory,
                weekChart = weekChart,
                dailyGoal = dailyGoalEntity?.let { GoalProgress(Money.ofKopecks(it.targetKopecks), todaySummary.revenueAfterReturns) },
                monthlyGoal = monthlyGoalEntity?.let { GoalProgress(Money.ofKopecks(it.targetKopecks), monthSummary.revenueAfterReturns) },
            )
        }
    }

    fun setChartMetric(metric: ChartMetric) {
        chartMetric.value = metric
    }
}
