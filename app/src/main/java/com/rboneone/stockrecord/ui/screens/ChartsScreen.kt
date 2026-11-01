package com.rboneone.stockrecord.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rboneone.stockrecord.data.Market
import com.rboneone.stockrecord.domain.MonthIncome
import com.rboneone.stockrecord.domain.PortfolioView
import com.rboneone.stockrecord.ui.AppViewModel
import com.rboneone.stockrecord.ui.formatAmount
import com.rboneone.stockrecord.ui.formatSignedAmount
import com.rboneone.stockrecord.ui.theme.LossGreen
import com.rboneone.stockrecord.ui.theme.ProfitRed
import com.rboneone.stockrecord.ui.theme.pnlColor

private val PieColors = listOf(
    Color(0xFF1E88E5), Color(0xFFFB8C00), Color(0xFF43A047), Color(0xFF8E24AA),
    Color(0xFFE53935), Color(0xFF00ACC1), Color(0xFFFDD835), Color(0xFF6D4C41), Color(0xFF9E9E9E),
)
private val DividendColor = Color(0xFF1E88E5)
private const val MAX_SLICES = 8

private data class Slice(val label: String, val value: Double, val color: Color)

@Composable
fun ChartsScreen(vm: AppViewModel, onBack: () -> Unit) {
    val portfolio by vm.portfolio.collectAsStateWithLifecycle()
    val monthly by vm.monthlyIncome.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var market by rememberSaveable { mutableStateOf(Market.TW) }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
    ) { uri -> uri?.let { vm.exportExcel(it) } }

    LaunchedEffect(message) {
        message?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            vm.clearMessage()
        }
    }

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(
            "圖表",
            navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回") }
            },
            actions = {
                TextButton(onClick = { exportLauncher.launch(vm.exportFileName()) }) { Text("匯出 Excel") }
            },
        )
        TabRow(selectedTabIndex = market.ordinal) {
            Market.entries.forEach { m ->
                Tab(selected = market == m, onClick = { market = m }, text = { Text(m.label) })
            }
        }
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            PieCard(market, portfolio)
            BarCard(market, monthly[market].orEmpty())
        }
    }
}

private fun slices(market: Market, view: PortfolioView?): List<Slice> {
    val items = view?.positions.orEmpty()
        .filter { it.holding.market == market && it.holding.isOpen }
        .map { it.displayName to (it.marketValue ?: it.holding.totalCost) }
        .filter { it.second > 0 }
        .sortedByDescending { it.second }
    // 超過 8 檔時，較小的合併成「其他」
    val top = if (items.size > MAX_SLICES) items.take(MAX_SLICES - 1) else items
    val rest = items.drop(top.size).sumOf { it.second }
    val list = top.mapIndexed { i, (name, v) -> Slice(name, v, PieColors[i]) }
    return if (rest > 0) list + Slice("其他", rest, PieColors.last()) else list
}

@Composable
private fun PieCard(market: Market, view: PortfolioView?) {
    val data = slices(market, view)
    val total = data.sumOf { it.value }
    val measurer = rememberTextMeasurer()
    val centerColor = MaterialTheme.colorScheme.onSurface
    val subColor = MaterialTheme.colorScheme.onSurfaceVariant
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("持股比例（依市值）", style = MaterialTheme.typography.titleMedium)
            if (data.isEmpty()) {
                EmptyHint("${market.label}目前沒有持股")
                return@Column
            }
            Box(Modifier.fillMaxWidth().height(220.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(200.dp)) {
                    val stroke = size.minDimension * 0.18f
                    val inset = stroke / 2
                    var start = -90f
                    data.forEach { s ->
                        val sweep = (s.value / total * 360).toFloat()
                        drawArc(
                            color = s.color,
                            startAngle = start,
                            sweepAngle = sweep,
                            useCenter = false,
                            topLeft = Offset(inset, inset),
                            size = Size(size.width - stroke, size.height - stroke),
                            style = Stroke(width = stroke),
                        )
                        start += sweep
                    }
                    val label = measurer.measure("總市值", TextStyle(fontSize = 12.sp, color = subColor))
                    val value = measurer.measure(
                        formatAmount(market, total),
                        TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, color = centerColor),
                    )
                    val h = label.size.height + value.size.height
                    drawText(label, topLeft = Offset((size.width - label.size.width) / 2, (size.height - h) / 2))
                    drawText(
                        value,
                        topLeft = Offset((size.width - value.size.width) / 2, (size.height - h) / 2 + label.size.height),
                    )
                }
            }
            data.forEach { s ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(12.dp).clip(CircleShape).background(s.color))
                    Text("  ${s.label}", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                    Text(formatAmount(market, s.value), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "%.1f%%".format(s.value / total * 100),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 12.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun BarCard(market: Market, months: List<MonthIncome>) {
    val measurer = rememberTextMeasurer()
    val axisColor = MaterialTheme.colorScheme.outline
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("最近 12 個月已實現損益與股利", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                LegendDot(ProfitRed, "已實現獲利")
                LegendDot(LossGreen, "已實現虧損")
                LegendDot(DividendColor, "股利")
            }
            val maxPos = months.maxOfOrNull { maxOf(it.realized, it.dividends, 0.0) } ?: 0.0
            val maxNeg = months.maxOfOrNull { maxOf(-it.realized, 0.0) } ?: 0.0
            if (maxPos == 0.0 && maxNeg == 0.0) {
                EmptyHint("最近 12 個月沒有賣出或股利紀錄")
            } else {
                Canvas(Modifier.fillMaxWidth().height(220.dp)) {
                    val labelH = 18.dp.toPx()
                    val chartH = size.height - labelH
                    val range = maxPos + maxNeg
                    val zeroY = (chartH * maxPos / range).toFloat()
                    val slot = size.width / months.size
                    val barW = slot * 0.32f
                    fun y(v: Double) = zeroY - (v / range * chartH).toFloat()
                    months.forEachIndexed { i, m ->
                        val x = i * slot + slot * 0.1f
                        if (m.realized != 0.0) {
                            val top = minOf(y(m.realized), zeroY)
                            drawRect(
                                color = if (m.realized > 0) ProfitRed else LossGreen,
                                topLeft = Offset(x, top),
                                size = Size(barW, kotlin.math.abs(y(m.realized) - zeroY)),
                            )
                        }
                        if (m.dividends > 0) {
                            drawRect(
                                color = DividendColor,
                                topLeft = Offset(x + barW + slot * 0.06f, y(m.dividends)),
                                size = Size(barW, zeroY - y(m.dividends)),
                            )
                        }
                        val text = measurer.measure("${m.month.monthValue}月", TextStyle(fontSize = 10.sp, color = labelColor))
                        drawText(text, topLeft = Offset(i * slot + (slot - text.size.width) / 2, chartH + 2.dp.toPx()))
                    }
                    drawLine(axisColor, Offset(0f, zeroY), Offset(size.width, zeroY), strokeWidth = 1.dp.toPx())
                }
                val realized = months.sumOf { it.realized }
                val dividends = months.sumOf { it.dividends }
                LabelValue("12 個月已實現損益", formatSignedAmount(market, realized), pnl = realized)
                LabelValue("12 個月股利收入", formatAmount(market, dividends))
                LabelValue("合計", formatSignedAmount(market, realized + dividends), pnl = realized + dividends)
                // 每月明細（只列有資料的月份）
                months.filter { it.total != 0.0 }.reversed().forEach { m ->
                    Row(Modifier.fillMaxWidth()) {
                        Text(
                            "%d/%02d".format(m.month.year, m.month.monthValue),
                            Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall,
                            color = labelColor,
                        )
                        Text(
                            "已實現 ${formatSignedAmount(market, m.realized)}　股利 ${formatAmount(market, m.dividends)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = pnlColor(m.realized).takeIf { it != Color.Unspecified } ?: labelColor,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).background(color))
        Text(" $label", style = MaterialTheme.typography.bodySmall)
    }
}
