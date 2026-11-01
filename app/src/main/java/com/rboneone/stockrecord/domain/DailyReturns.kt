package com.rboneone.stockrecord.domain

import com.rboneone.stockrecord.data.Dividend
import com.rboneone.stockrecord.data.Trade
import com.rboneone.stockrecord.data.TradeType

/** 某一天的收盤價，date 為 LocalDate.toEpochDay() */
data class Bar(val date: Long, val close: Double)

data class DayReturn(val date: Long, val pnl: Double, val pct: Double)

data class PeriodReturn(val pnl: Double, val pct: Double)

/**
 * 每日損益 = 今日市值 − 前一交易日市值 − 當天買進金額 + 當天賣出淨收入 + 當天入帳股利
 * 報酬率 = 每日損益 ÷（前一交易日市值 + 當天買進金額）
 */
object DailyReturns {
    fun compute(
        trades: List<Trade>,
        dividends: List<Dividend>,
        bars: Map<String, List<Bar>>,
        from: Long,
        to: Long,
    ): List<DayReturn> {
        val tradingDays = bars.values.flatten().map { it.date }.filter { it <= to }.distinct().sorted()
        val symbols = (trades.map { it.symbol } + dividends.map { it.symbol }).distinct()
        val sortedBars = bars.mapValues { (_, list) -> list.sortedBy { it.date } }

        fun close(symbol: String, date: Long): Double {
            sortedBars[symbol]?.lastOrNull { it.date <= date }?.let { return it.close }
            // 沒有歷史價格時用最近一次成交價
            return trades.filter { it.symbol == symbol && it.date <= date }.maxByOrNull { it.date }?.price ?: 0.0
        }

        fun marketValue(date: Long): Double = symbols.sumOf { s ->
            val shares = Portfolio.sharesOn(
                trades.filter { it.symbol == s },
                dividends.filter { it.symbol == s },
                date,
            )
            if (shares > 1e-9) shares * close(s, date) else 0.0
        }

        val result = mutableListOf<DayReturn>()
        for (i in 1 until tradingDays.size) {
            val day = tradingDays[i]
            if (day < from) continue
            val prev = tradingDays[i - 1]
            val inRange = { date: Long -> date > prev && date <= day }
            val buys = trades.filter { it.type == TradeType.BUY && inRange(it.date) }.sumOf { it.amount + it.fee }
            val sells = trades.filter { it.type == TradeType.SELL && inRange(it.date) }
                .sumOf { it.amount - it.fee - it.tax }
            val divs = dividends.filter { inRange(it.date) }.sumOf { it.netCash }
            val mvPrev = marketValue(prev)
            val mv = marketValue(day)
            if (mvPrev == 0.0 && mv == 0.0 && buys == 0.0 && sells == 0.0 && divs == 0.0) continue
            val pnl = mv - mvPrev - buys + sells + divs
            val base = mvPrev + buys
            result += DayReturn(day, pnl, if (base > 0) pnl / base * 100 else 0.0)
        }
        return result
    }

    /** 多天合計：損益相加，報酬率以複利連乘 */
    fun combine(days: List<DayReturn>): PeriodReturn = PeriodReturn(
        pnl = days.sumOf { it.pnl },
        pct = (days.fold(1.0) { acc, d -> acc * (1 + d.pct / 100) } - 1) * 100,
    )
}
