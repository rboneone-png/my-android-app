package com.rboneone.stockrecord.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rboneone.stockrecord.data.Market
import com.rboneone.stockrecord.domain.DailyReturns
import com.rboneone.stockrecord.domain.DayReturn
import com.rboneone.stockrecord.ui.AppViewModel
import com.rboneone.stockrecord.ui.formatAmount
import com.rboneone.stockrecord.ui.formatPct
import com.rboneone.stockrecord.ui.formatWan
import com.rboneone.stockrecord.ui.theme.LossGreen
import com.rboneone.stockrecord.ui.theme.ProfitRed
import com.rboneone.stockrecord.ui.theme.pnlColor
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters

private val WEEKDAYS = listOf("一", "二", "三", "四", "五")
private val HighlightBorder = Color(0xFF4DD0C4)

@Composable
fun MonthlyReturnScreen(vm: AppViewModel) {
    val state by vm.month.collectAsStateWithLifecycle()
    var market by rememberSaveable { mutableStateOf(Market.TW) }
    var monthText by rememberSaveable { mutableStateOf(YearMonth.now().toString()) }
    val month = YearMonth.parse(monthText)
    val current = YearMonth.now()

    LaunchedEffect(market, monthText) { vm.loadMonth(market, month) }

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar("月報酬")
        TabRow(selectedTabIndex = market.ordinal) {
            Market.entries.forEach { m ->
                Tab(selected = market == m, onClick = { market = m }, text = { Text(m.label) })
            }
        }
        if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())

        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                IconButton(
                    onClick = { monthText = month.minusMonths(1).toString() },
                    enabled = month > vm.earliestMonth(),
                ) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "上個月") }
                Text(
                    "%d/%02d 報酬".format(month.year, month.monthValue),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                IconButton(
                    onClick = { monthText = month.plusMonths(1).toString() },
                    enabled = month < current,
                ) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "下個月") }
                Box(Modifier.weight(1f))
                Text("報酬率", style = MaterialTheme.typography.titleMedium)
            }

            val total = state.total?.takeIf { state.market == market && state.month == month && !state.loading }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    total?.let { formatSignedTotal(market, it.pnl) } ?: "--",
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                    color = total?.let { pnlColor(it.pnl) } ?: Color.Unspecified,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    total?.let { formatPct(it.pct) } ?: "--",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = total?.let { pnlColor(it.pct) } ?: Color.Unspecified,
                )
            }

            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                (WEEKDAYS + "週損益").forEach {
                    Text(
                        it,
                        Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.titleSmall,
                    )
                }
            }

            val days = if (state.market == market && state.month == month) state.days else emptyList()
            CalendarGrid(month, days.associateBy { it.date })

            state.error?.takeIf { state.market == market && state.month == month }?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            Text(
                "每日損益 = 收盤市值變化 − 買進金額 + 賣出淨收入 + 股利入帳；報酬率以前一交易日市值為基準，" +
                    "週與月報酬率以每日報酬連乘計算。股價來源 Yahoo Finance 每日收盤價。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 16.dp),
            )
        }
    }
}

private fun formatSignedTotal(market: Market, value: Double): String =
    (if (value > 0.0001) "+" else "") + formatAmount(market, value)

@Composable
private fun CalendarGrid(month: YearMonth, days: Map<Long, DayReturn>) {
    val today = LocalDate.now()
    val firstMonday = month.atDay(1).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val lastDay = month.atEndOfMonth()
    var weekStart = firstMonday
    var weekIndex = 1
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        while (!weekStart.isAfter(lastDay)) {
            val weekDates = (0L until 5L).map { weekStart.plusDays(it) }
            val inMonth = weekDates.filter { YearMonth.from(it) == month }
            val weekReturns = inMonth.mapNotNull { days[it.toEpochDay()] }
            val isCurrentWeek = !today.isBefore(weekStart) && today.isBefore(weekStart.plusDays(7))
            Row(
                Modifier
                    .fillMaxWidth()
                    .then(
                        if (isCurrentWeek) Modifier.border(2.dp, HighlightBorder, RoundedCornerShape(10.dp)).padding(3.dp)
                        else Modifier
                    ),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                weekDates.forEach { date ->
                    DayCell(
                        dayOfMonth = date.dayOfMonth,
                        inMonth = YearMonth.from(date) == month,
                        result = days[date.toEpochDay()],
                        modifier = Modifier.weight(1f),
                    )
                }
                WeekCell(
                    index = weekIndex,
                    result = if (weekReturns.isEmpty()) null else DailyReturns.combine(weekReturns).let {
                        DayReturn(0, it.pnl, it.pct)
                    },
                    modifier = Modifier.weight(1f),
                )
            }
            weekStart = weekStart.plusWeeks(1)
            weekIndex++
        }
    }
}

private fun cellColor(pnl: Double): Color = when {
    pnl > 0.0001 -> ProfitRed.copy(alpha = 0.22f)
    pnl < -0.0001 -> LossGreen.copy(alpha = 0.22f)
    else -> Color.Transparent
}

@Composable
private fun DayCell(dayOfMonth: Int, inMonth: Boolean, result: DayReturn?, modifier: Modifier) {
    val outline = MaterialTheme.colorScheme.outlineVariant
    Column(
        modifier
            .height(80.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (result != null) cellColor(result.pnl) else Color.Transparent)
            .border(1.dp, outline, RoundedCornerShape(8.dp))
            .padding(horizontal = 2.dp, vertical = 4.dp),
    ) {
        Text(
            dayOfMonth.toString(),
            fontSize = 11.sp,
            color = if (inMonth) MaterialTheme.colorScheme.onSurfaceVariant
            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            textAlign = TextAlign.End,
            modifier = Modifier.fillMaxWidth().padding(end = 2.dp),
        )
        if (result != null) {
            ResultText(result, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun WeekCell(index: Int, result: DayReturn?, modifier: Modifier) {
    val outline = MaterialTheme.colorScheme.outline
    Column(
        modifier
            .height(80.dp)
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, outline, RoundedCornerShape(8.dp))
            .padding(horizontal = 2.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.End,
    ) {
        Text(
            index.toString(),
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .padding(end = 2.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 4.dp),
        )
        if (result != null) {
            ResultText(result, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun ResultText(result: DayReturn, modifier: Modifier) {
    Column(modifier.padding(top = 2.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            formatWan(result.pnl),
            fontSize = 12.sp,
            lineHeight = 16.sp,
            fontWeight = FontWeight.Bold,
            color = pnlColor(result.pnl),
            maxLines = 1,
        )
        Text(
            formatPct(result.pct),
            fontSize = 10.sp,
            lineHeight = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}
