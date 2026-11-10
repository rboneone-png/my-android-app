package com.rboneone.stockrecord.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.rboneone.stockrecord.ui.theme.AppCard as Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.graphics.Path
import com.rboneone.stockrecord.data.Market
import com.rboneone.stockrecord.domain.AssetPoint
import com.rboneone.stockrecord.domain.AssetTrend
import com.rboneone.stockrecord.ui.AppViewModel
import com.rboneone.stockrecord.ui.formatAmount
import com.rboneone.stockrecord.ui.formatSignedAmount
import com.rboneone.stockrecord.ui.theme.pnlColor

private val BarColor = Color(0xFFC53A28)
private val LineColor = Color(0xFF1E2A44)

private enum class TrendRange(val label: String, val months: Int) {
    Y1("近 12 個月", 12), Y2("近 24 個月", 24), ALL("全部", Int.MAX_VALUE)
}

/** 資產走勢：每月月底的持股市值（長條）與持股成本（折線） */
@Composable
fun AssetTrendBody(vm: AppViewModel, market: Market) {
    val state by vm.assetTrend.collectAsStateWithLifecycle()
    LaunchedEffect(market) { vm.loadAssetTrend(market) }
    val same = state.market == market
    AssetTrendContent(
        market,
        points = if (same) state.points else emptyList(),
        ready = same && !state.loading,
        error = state.error.takeIf { same },
    )
}

@Composable
internal fun AssetTrendContent(market: Market, points: List<AssetPoint>, ready: Boolean, error: String?) {
    var range by rememberSaveable { mutableStateOf(TrendRange.Y1) }
    val shown = points.takeLast(range.months)

    if (!ready) LinearProgressIndicator(Modifier.fillMaxWidth())
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("幣別｜${if (market == Market.TW) "台幣" else "美元"}", style = MaterialTheme.typography.titleSmall)
            TrendRange.entries.forEach { r ->
                FilterChip(selected = range == r, onClick = { range = r }, label = { Text(r.label) })
            }
        }
        if (ready && points.isEmpty()) {
            EmptyHint("還沒有${market.label}的交易紀錄")
        } else if (shown.isNotEmpty()) {
            val last = shown.last()
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("目前持股市值", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(formatAmount(market, last.marketValue), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("未實現損益", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        formatSignedAmount(market, last.gain),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = pnlColor(last.gain),
                    )
                }
            }
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        LegendSwatch(BarColor, "持股市值（長條）")
                        LegendSwatch(LineColor, "持股成本（折線）")
                    }
                    TrendChart(market, shown)
                }
            }
            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            // 每月明細，新的在上面
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Row(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
                        listOf("月份" to 0.8f, "持股市值" to 1.3f, "持股成本" to 1.3f, "未實現" to 1.2f).forEach { (t, w) ->
                            Text(
                                t, Modifier.weight(w), style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = if (t == "月份") null else androidx.compose.ui.text.style.TextAlign.End,
                            )
                        }
                    }
                    HorizontalDivider()
                    shown.reversed().forEach { p ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            val style = MaterialTheme.typography.bodySmall
                            Text("%d/%02d".format(p.month.year, p.month.monthValue), Modifier.weight(0.8f), style = style)
                            Text(formatAmount(market, p.marketValue), Modifier.weight(1.3f), style = style, textAlign = androidx.compose.ui.text.style.TextAlign.End)
                            Text(formatAmount(market, p.cost), Modifier.weight(1.3f), style = style, textAlign = androidx.compose.ui.text.style.TextAlign.End)
                            Text(
                                formatSignedAmount(market, p.gain), Modifier.weight(1.2f), style = style,
                                color = pnlColor(p.gain), textAlign = androidx.compose.ui.text.style.TextAlign.End,
                            )
                        }
                    }
                }
            }
            Text(
                "持股市值 = 月底持有股數 × 月底最後交易日收盤價（本月為最新股價），不含現金；" +
                    "持股成本為平均成本法下當時持股的買進成本（含手續費）。已賣出的股票不計入。" +
                    "歷史收盤價來源 Yahoo Finance。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 16.dp),
            )
        }
    }
}

@Composable
private fun LegendSwatch(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(10.dp)) { drawRect(color) }
        Text(" $label", style = MaterialTheme.typography.bodySmall)
    }
}

/** 長條圖（月底持股市值）疊折線（持股成本），左側刻度，下方每隔幾個月標示月份與年份 */
@Composable
private fun TrendChart(market: Market, points: List<AssetPoint>) {
    val measurer = rememberTextMeasurer()
    val grid = MaterialTheme.colorScheme.outlineVariant
    val labelColor = MaterialTheme.colorScheme.onSurface
    // 台幣以萬元為單位，美元直接用元
    val divisor = if (market == Market.TW) 10_000.0 else 1.0
    val unit = if (market == Market.TW) "萬元" else "美元"
    val max = points.maxOf { maxOf(it.marketValue, it.cost) } / divisor
    val ticks = AssetTrend.niceTicks(max)
    val top = ticks.last()

    Text("（$unit）", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Canvas(Modifier.fillMaxWidth().height(280.dp)) {
        val tickStyle = TextStyle(fontSize = 11.sp, color = labelColor, fontWeight = FontWeight.Bold)
        val axisW = ticks.maxOf { measurer.measure("%,.0f".format(it), tickStyle).size.width } + 8.dp.toPx()
        val labelH = 36.dp.toPx()
        val chartTop = 8.dp.toPx()
        val chartH = size.height - labelH - chartTop
        val chartW = size.width - axisW
        fun y(v: Double) = chartTop + chartH - (v / divisor / top * chartH).toFloat()

        ticks.forEach { t ->
            val ty = chartTop + chartH - (t / top * chartH).toFloat()
            drawLine(grid, Offset(axisW, ty), Offset(size.width, ty), strokeWidth = 1.dp.toPx())
            val text = measurer.measure("%,.0f".format(t), tickStyle)
            drawText(text, topLeft = Offset(axisW - 6.dp.toPx() - text.size.width, ty - text.size.height / 2f))
        }

        val slot = chartW / points.size
        val barW = slot * 0.62f
        // 月份標籤太擠時隔幾個顯示一個，從第一個月開始
        val every = when {
            points.size <= 8 -> 1
            points.size <= 16 -> 2
            points.size <= 30 -> 3
            else -> 6
        }
        points.forEachIndexed { i, p ->
            val x = axisW + i * slot + (slot - barW) / 2
            val barTop = y(p.marketValue)
            drawRect(BarColor, Offset(x, barTop), Size(barW, chartTop + chartH - barTop))
            if (i % every == 0) {
                val label = measurer.measure(
                    "${p.month.monthValue}月\n${p.month.year}",
                    TextStyle(fontSize = 11.sp, color = labelColor, fontWeight = FontWeight.Bold, lineHeight = 14.sp),
                )
                drawText(label, topLeft = Offset(x + barW / 2 - label.size.width / 2f, chartTop + chartH + 4.dp.toPx()))
            }
        }
        if (points.size >= 2) {
            val path = Path()
            points.forEachIndexed { i, p ->
                val cx = axisW + i * slot + slot / 2
                if (i == 0) path.moveTo(cx, y(p.cost)) else path.lineTo(cx, y(p.cost))
            }
            drawPath(path, LineColor, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        } else {
            drawCircle(LineColor, 3.dp.toPx(), Offset(axisW + slot / 2, y(points[0].cost)))
        }
    }
}
