package com.rboneone.stockrecord.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rboneone.stockrecord.data.Market
import com.rboneone.stockrecord.domain.Candle
import com.rboneone.stockrecord.domain.Indicators
import com.rboneone.stockrecord.ui.AppViewModel
import com.rboneone.stockrecord.ui.formatPrice
import com.rboneone.stockrecord.ui.theme.LossGreen
import com.rboneone.stockrecord.ui.theme.ProfitRed
import java.time.LocalDate

private val Ma5Color = Color(0xFFE6A23C)
private val Ma20Color = Color(0xFF3F51B5)
private val Ma60Color = Color(0xFF8E44AD)
private val RsiColor = Color(0xFF00897B)

/** 各個時間範圍顯示的交易日數 */
private val RANGES = listOf("3個月" to 63, "6個月" to 126, "1年" to 252)

/** 展開後的走勢圖：日 K、MA5／MA20／MA60、成交量、RSI(14)；點或左右拖曳圖面可看某一天的數字 */
@Composable
fun StockChartPanel(vm: AppViewModel, market: Market, symbol: String) {
    var loaded by remember(market, symbol) { mutableStateOf(false) }
    var data by remember(market, symbol) { mutableStateOf<List<Candle>?>(null) }
    var rangeIndex by rememberSaveable { mutableIntStateOf(1) }
    LaunchedEffect(market, symbol) {
        data = vm.candles(market, symbol)
        loaded = true
    }

    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("走勢圖", style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RANGES.forEachIndexed { i, (label, _) ->
                FilterChip(selected = rangeIndex == i, onClick = { rangeIndex = i }, label = { Text(label) })
            }
        }
        val candles = data
        when {
            !loaded -> Text("載入中…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            candles == null || candles.size < 2 ->
                Text("抓不到走勢資料，請確認網路後再試", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            else -> CandleChart(candles, RANGES[rangeIndex].second, market)
        }
    }
}

private fun volumeText(v: Long, market: Market): String {
    val unit = if (market == Market.TW) v / 1000.0 else v.toDouble()   // 台股用張
    val suffix = if (market == Market.TW) "張" else "股"
    return if (unit >= 10_000) "%.1f萬%s".format(unit / 10_000, suffix) else "%,.0f%s".format(unit, suffix)
}

@Composable
private fun CandleChart(all: List<Candle>, days: Int, market: Market) {
    val closes = remember(all) { all.map { it.close } }
    val ma5 = remember(all) { Indicators.sma(closes, 5) }
    val ma20 = remember(all) { Indicators.sma(closes, 20) }
    val ma60 = remember(all) { Indicators.sma(closes, 60) }
    val rsi = remember(all) { Indicators.rsi(closes, 14) }
    val from = (all.size - days).coerceAtLeast(0)
    val count = all.size - from
    var selected by remember(all, days) { mutableIntStateOf(count - 1) }   // 相對於 from 的位置
    val idx = selected.coerceIn(0, count - 1)
    val c = all[from + idx]
    val prevClose = all.getOrNull(from + idx - 1)?.close ?: c.open
    val tone = if (c.close >= prevClose) ProfitRed else LossGreen

    // 選到的那一天的數字
    Text(
        buildString {
            append(LocalDate.ofEpochDay(c.date)).append("  開 ").append(formatPrice(c.open))
            append("  高 ").append(formatPrice(c.high)).append("  低 ").append(formatPrice(c.low))
            append("  收 ").append(formatPrice(c.close)).append("  量 ").append(volumeText(c.volume, market))
            rsi[from + idx]?.let { append("  RSI ").append("%.0f".format(it)) }
        },
        style = MaterialTheme.typography.bodyMedium, color = tone,
    )

    val grid = MaterialTheme.colorScheme.outlineVariant
    val label = MaterialTheme.colorScheme.onSurfaceVariant
    val measurer = rememberTextMeasurer()
    val axisW = with(androidx.compose.ui.platform.LocalDensity.current) { 52.dp.toPx() }
    Column {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("MA5", color = Ma5Color, style = MaterialTheme.typography.labelMedium)
            Text("MA20", color = Ma20Color, style = MaterialTheme.typography.labelMedium)
            Text("MA60", color = Ma60Color, style = MaterialTheme.typography.labelMedium)
        }
        Box(Modifier.fillMaxWidth().height(380.dp)) {
            Canvas(
                Modifier
                    .fillMaxWidth().height(380.dp)
                    .pointerInput(count, axisW) {
                        fun pick(x: Float) {
                            val w = size.width - axisW
                            selected = (x / w * count).toInt().coerceIn(0, count - 1)
                        }
                        detectTapGestures { pick(it.x) }
                    }
                    .pointerInput(count, axisW) {
                        detectHorizontalDragGestures { change, _ ->
                            change.consume()
                            val w = size.width - axisW
                            selected = (change.position.x / w * count).toInt().coerceIn(0, count - 1)
                        }
                    },
            ) {
                val w = size.width - axisW
                val priceH = size.height * 0.58f
                val volTop = priceH + 8.dp.toPx()
                val volH = size.height * 0.16f
                val rsiTop = volTop + volH + 14.dp.toPx()
                val rsiH = size.height * 0.14f
                val step = w / count
                val xOf = { i: Int -> step * i + step / 2 }

                // 價格範圍
                var hi = Double.MIN_VALUE; var lo = Double.MAX_VALUE
                for (i in from until all.size) { hi = maxOf(hi, all[i].high); lo = minOf(lo, all[i].low) }
                val pad = (hi - lo).coerceAtLeast(hi * 0.01) * 0.04
                hi += pad; lo -= pad
                val yP = { v: Double -> (priceH * (1 - (v - lo) / (hi - lo))).toFloat() }
                val axisStyle = TextStyle(fontSize = 10.sp, color = label)

                // 格線與右側刻度
                for (k in 0..2) {
                    val v = hi - (hi - lo) * k / 2
                    val y = yP(v)
                    drawLine(grid, Offset(0f, y), Offset(w, y), 1f)
                    drawText(measurer, formatPrice(v), Offset(w + 4.dp.toPx(), (y - 7.dp.toPx()).coerceAtLeast(0f)), axisStyle)
                }
                // K 線
                val body = (step * 0.6f).coerceAtLeast(1f)
                for (i in 0 until count) {
                    val k = all[from + i]
                    val up = k.close >= k.open
                    val col = if (up) ProfitRed else LossGreen
                    val x = xOf(i)
                    drawLine(col, Offset(x, yP(k.high)), Offset(x, yP(k.low)), 1f)
                    val top = yP(maxOf(k.open, k.close)); val bot = yP(minOf(k.open, k.close))
                    drawRect(col, Offset(x - body / 2, top), Size(body, (bot - top).coerceAtLeast(1f)))
                }
                // 均線
                fun line(values: List<Double?>, col: Color) {
                    var prev: Offset? = null
                    for (i in 0 until count) {
                        val v = values[from + i]
                        if (v == null) { prev = null; continue }
                        val p = Offset(xOf(i), yP(v))
                        prev?.let { drawLine(col, it, p, 2f) }
                        prev = p
                    }
                }
                line(ma5, Ma5Color); line(ma20, Ma20Color); line(ma60, Ma60Color)

                // 成交量
                var maxVol = 1L
                for (i in 0 until count) maxVol = maxOf(maxVol, all[from + i].volume)
                drawText(measurer, "成交量", Offset(w + 4.dp.toPx(), volTop), axisStyle)
                for (i in 0 until count) {
                    val k = all[from + i]
                    val prev = all.getOrNull(from + i - 1)?.close ?: k.open
                    val col = (if (k.close >= prev) ProfitRed else LossGreen).copy(alpha = 0.55f)
                    val h = volH * k.volume / maxVol
                    drawRect(col, Offset(xOf(i) - body / 2, volTop + volH - h), Size(body, h))
                }

                // RSI(14)：70、30 虛線
                drawText(measurer, "RSI(14)", Offset(w + 4.dp.toPx(), rsiTop - 12.dp.toPx()), axisStyle)
                val yR = { v: Double -> (rsiTop + rsiH * (1 - v / 100)).toFloat() }
                val dash = PathEffect.dashPathEffect(floatArrayOf(8f, 6f))
                for (lvl in listOf(70.0, 30.0)) {
                    drawLine(grid, Offset(0f, yR(lvl)), Offset(w, yR(lvl)), 1f, pathEffect = dash)
                    drawText(measurer, "%.0f".format(lvl), Offset(w + 4.dp.toPx(), yR(lvl) - 7.dp.toPx()), axisStyle)
                }
                var prevR: Offset? = null
                for (i in 0 until count) {
                    val v = rsi[from + i]
                    if (v == null) { prevR = null; continue }
                    val p = Offset(xOf(i), yR(v))
                    prevR?.let { drawLine(RsiColor, it, p, 2f) }
                    prevR = p
                }

                // 月份標示
                var lastMonth = -1
                for (i in 0 until count) {
                    val d = LocalDate.ofEpochDay(all[from + i].date)
                    val m = d.year * 12 + d.monthValue
                    if (m != lastMonth) {
                        if (lastMonth != -1 && xOf(i) < w - 24.dp.toPx() && xOf(i) > 16.dp.toPx())
                            drawText(measurer, "%02d-%02d".format(d.year % 100, d.monthValue), Offset(xOf(i) - 14.dp.toPx(), rsiTop + rsiH + 2.dp.toPx()), axisStyle)
                        lastMonth = m
                    }
                }
                // 選到的那一天：直線
                val sx = xOf(idx)
                drawLine(label.copy(alpha = 0.6f), Offset(sx, 0f), Offset(sx, rsiTop + rsiH), 1f, pathEffect = dash)
            }
        }
        Text(
            "點一下或左右拖曳圖面，可看該日的開高低收量與 RSI。紅漲綠跌。RSI(14) 高於 70 偏熱、低於 30 偏冷。",
            style = MaterialTheme.typography.bodySmall, color = label,
        )
    }
}

/** 列右側的「走勢」按鈕：按一下展開走勢圖，再按收合 */
@Composable
fun ChartToggle(open: Boolean, onClick: () -> Unit) {
    androidx.compose.material3.TextButton(
        onClick = onClick,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp, vertical = 0.dp),
    ) { Text(if (open) "走勢▴" else "走勢▾", style = MaterialTheme.typography.labelMedium) }
}
