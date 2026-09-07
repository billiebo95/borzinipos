@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.borzini.pos.ui.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.compose.runtime.collectAsState
import com.borzini.pos.LocalAppContainer
import com.borzini.pos.core.StatsPeriodPreset
import com.borzini.pos.ui.common.SimpleViewModelFactory

private fun presetLabel(preset: StatsPeriodPreset): String = when (preset) {
    StatsPeriodPreset.TODAY -> "Сегодня"
    StatsPeriodPreset.YESTERDAY -> "Вчера"
    StatsPeriodPreset.THIS_WEEK -> "Текущая неделя"
    StatsPeriodPreset.THIS_MONTH -> "Текущий месяц"
    StatsPeriodPreset.CUSTOM -> "Диапазон"
}

@Composable
fun StatsScreen(navController: NavController) {
    val container = LocalAppContainer.current
    val viewModel: StatsViewModel = viewModel(factory = SimpleViewModelFactory { StatsViewModel(container) })
    val state by viewModel.state.collectAsState()
    val chartMetric by viewModel.chartMetric.collectAsState()

    Scaffold(topBar = { TopAppBar(title = { Text("Статистика") }) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(StatsPeriodPreset.TODAY, StatsPeriodPreset.YESTERDAY, StatsPeriodPreset.THIS_WEEK, StatsPeriodPreset.THIS_MONTH).forEach { preset ->
                        FilterChip(selected = state.preset == preset, onClick = { viewModel.refresh(preset) }, label = { Text(presetLabel(preset)) })
                    }
                }
            }
            val summary = state.summary
            if (summary != null) {
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            KpiRow("Выручка (до возвратов)", summary.salesBeforeReturns.format())
                            KpiRow("Наличные", summary.cashSales.format())
                            KpiRow("Безналичные", summary.cashlessSales.format())
                            KpiRow("Возвраты", summary.returnsTotal.format())
                            KpiRow("Выручка после возвратов", summary.revenueAfterReturns.format())
                            KpiRow("Себестоимость", summary.costOfGoodsSold.format())
                            KpiRow("Валовая прибыль", summary.grossProfit.format())
                            KpiRow("Расходы", summary.operatingExpenses.format())
                            KpiRow("Чистая прибыль (расчётная)", summary.netProfit.format(), emphasized = true)
                            KpiRow("Количество чеков", "${summary.completedReceiptCount}")
                            KpiRow("Средний чек (выручка/чеки)", summary.averageCheck.format())
                        }
                    }
                }
            }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Выручка по дням недели", style = MaterialTheme.typography.titleMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                            FilterChip(selected = chartMetric == ChartMetric.REVENUE, onClick = { viewModel.setChartMetric(ChartMetric.REVENUE) }, label = { Text("Выручка") })
                            FilterChip(selected = chartMetric == ChartMetric.RECEIPTS, onClick = { viewModel.setChartMetric(ChartMetric.RECEIPTS) }, label = { Text("Чеки") })
                            FilterChip(selected = chartMetric == ChartMetric.PROFIT, onClick = { viewModel.setChartMetric(ChartMetric.PROFIT) }, label = { Text("Прибыль") })
                        }
                        WeekChart(state.weekChart, chartMetric)
                    }
                }
            }
            state.dailyGoal?.let { goal ->
                item { GoalCard("Цель на день", goal) }
            }
            state.monthlyGoal?.let { goal ->
                item { GoalCard("Цель на месяц", goal) }
            }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Самые продаваемые товары", style = MaterialTheme.typography.titleMedium)
                        state.topProducts.forEach { row ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(row.productNameSnapshot)
                                Text("${row.totalQuantity} шт.")
                            }
                        }
                    }
                }
            }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Продажи по категориям", style = MaterialTheme.typography.titleMedium)
                        state.categoryBreakdown.forEach { row ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(row.categoryName)
                                Text(row.total.format())
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun KpiRow(label: String, value: String, emphasized: Boolean = false) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = if (emphasized) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun GoalCard(title: String, goal: GoalProgress) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            LinearProgressIndicator(progress = { (goal.percent / 100f).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
            Text("${goal.current.format()} из ${goal.target.format()} (${goal.percent}%)")
            if (goal.remaining.isPositive()) Text("Осталось: ${goal.remaining.format()}", style = MaterialTheme.typography.bodySmall)
        }
    }
}
