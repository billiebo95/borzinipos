package com.borzini.pos.ui.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.borzini.pos.core.Money
import com.borzini.pos.data.repository.DayPoint
import java.time.format.DateTimeFormatter

/** Simple hand-drawn Mon..Sun line chart - no charting library needed, kept dependency-free. */
@Composable
fun WeekChart(points: List<DayPoint>, metric: ChartMetric, modifier: Modifier = Modifier) {
    var selectedIndex by remember { mutableIntStateOf(-1) }
    val values = points.map {
        when (metric) {
            ChartMetric.REVENUE -> it.revenue.rubles.toDouble()
            ChartMetric.RECEIPTS -> it.receiptCount.toDouble()
            ChartMetric.PROFIT -> it.grossProfit.rubles.toDouble()
        }
    }
    val maxValue = (values.maxOrNull() ?: 0.0).coerceAtLeast(1.0)
    val lineColor = MaterialTheme.colorScheme.primary
    val dayLabelFormatter = DateTimeFormatter.ofPattern("EE")

    Box(modifier) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(180.dp)
                .pointerInput(points) {
                    detectTapGestures { offset ->
                        if (points.isEmpty()) return@detectTapGestures
                        val step = size.width / points.size
                        selectedIndex = (offset.x / step).toInt().coerceIn(0, points.size - 1)
                    }
                },
        ) {
            if (points.isEmpty()) return@Canvas
            val stepX = size.width / points.size
            val usableHeight = size.height - 24f
            val coords = values.mapIndexed { i, v ->
                Offset(stepX * i + stepX / 2, usableHeight - (v / maxValue * usableHeight).toFloat())
            }
            for (i in 0 until coords.size - 1) {
                drawLine(lineColor, coords[i], coords[i + 1], strokeWidth = 4f)
            }
            coords.forEachIndexed { i, c ->
                drawCircle(lineColor, radius = if (i == selectedIndex) 8f else 5f, center = c)
            }
        }
    }
    androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween) {
        points.forEach { p ->
            Text(dayLabelFormatter.format(p.date), style = MaterialTheme.typography.labelSmall)
        }
    }
    if (selectedIndex in points.indices) {
        val point = points[selectedIndex]
        val label = when (metric) {
            ChartMetric.REVENUE -> point.revenue.format()
            ChartMetric.RECEIPTS -> "${point.receiptCount} чек(ов)"
            ChartMetric.PROFIT -> point.grossProfit.format()
        }
        Text("${point.date}: $label", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.fillMaxWidth())
    }
}
