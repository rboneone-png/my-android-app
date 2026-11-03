package com.rboneone.stockrecord.domain

import com.rboneone.stockrecord.data.Dividend
import com.rboneone.stockrecord.data.Quote
import com.rboneone.stockrecord.data.Trade
import com.rboneone.stockrecord.data.TradeType

/** 某一天的收盤價，date 為 LocalDate.toEpochDay() */
data class Bar(val date: Long, val close: Double)

data class DayReturn(val date: Long, val pnl: Double, val pct: Double)

data class PeriodReturn(val pnl: Double, val pct: Double)

/** 即時報價換算成的「交易日、現價、前一交易日收盤」 */
data class LiveClose(val date: Long, val price: Double, val previousClose: Double) {
    companion object {
        /** 從報價表挑出指定股票的即時報價；價格不合理的略過 */
        fun from(quotes: Collection<Quote>, symbols: Collection<String>): Map<String, LiveClose> =
            quotes.filter { it.symbol in symbols && it.price > 0 && it.previousClose > 0 }
                .associate { it.symbol to LiveClose(it.tradingDay(), it.price, it.previousClose) }
    }
}

/**
 * 每日損益 = 今日市值 − 前一交易日市值 − 當天買進金額 + 當天賣出淨收入 + 當天入帳股利
 * 報酬率 = 每日損益 ÷（前一交易日市值 + 當天買進金額）
 *
 * 月報酬（compute）與持股頁的「持股日損益」（today）用同一個算式與同一份即時報價，兩邊數字一致。
 */
object DailyReturns {
    fun compute(
        trades: List<Trade>,
        dividends: List<Dividend>,
        bars: Map<String, List<Bar>>,
        from: Long,
        to: Long,
        /** 即時報價：最新一天的現價與前一日收盤以它為準（歷史日線有延遲） */
        live: Map<String, LiveClose> = emptyMap(),
    ): List<DayReturn> {
        val liveUsed = live.filterValues { it.date <= to }
        val tradingDays = (bars.values.flatten().map { it.date } + liveUsed.values.map { it.date })
            .filter { it <= to }.distinct().sorted()
        val symbols = (trades.map { it.symbol } + dividends.map { it.symbol }).distinct()
        val sortedBars = bars.mapValues { (_, list) -> list.sortedBy { it.date } }

        fun close(symbol: String, date: Long): Double {
            liveUsed[symbol]?.let { l ->
                if (date >= l.date) return l.price
                if (date == tradingDays.lastOrNull { it < l.date }) return l.previousClose
            }
            sortedBars[symbol]?.lastOrNull { it.date <= date }?.let { return it.close }
            // 沒有歷史價格時用最近一次成交價
            return trades.filter { it.symbol == symbol && it.date <= date }.maxByOrNull { it.date }?.price ?: 0.0
        }

        val result = mutableListOf<DayReturn>()
        for (i in 1 until tradingDays.size) {
            val day = tradingDays[i]
            if (day < from) continue
            dayReturn(trades, dividends, symbols, tradingDays[i - 1], day, ::close)?.let { result += it }
        }
        return result
    }

    /**
     * 持股頁用：只靠即時報價與交易紀錄算出最新交易日的損益（不需要歷史日線）。
     * 沒有即時報價的股票以最近成交價計，當天損益為 0。
     */
    fun today(trades: List<Trade>, dividends: List<Dividend>, live: Map<String, LiveClose>): DayReturn? {
        val day = live.values.maxOfOrNull { it.date } ?: return null
        val symbols = (trades.map { it.symbol } + dividends.map { it.symbol }).distinct()
        fun close(symbol: String, date: Long): Double {
            live[symbol]?.let { l ->
                return when {
                    date >= day -> l.price
                    l.date >= day -> l.previousClose
                    else -> l.price   // 這檔今天沒有新報價，視為沒有漲跌
                }
            }
            return trades.filter { it.symbol == symbol && it.date <= date }.maxByOrNull { it.date }?.price ?: 0.0
        }
        return dayReturn(trades, dividends, symbols, day - 1, day, ::close)
    }

    /** prev（不含）到 day（含）之間的損益；完全沒有部位也沒有交易時回傳 null */
    private fun dayReturn(
        trades: List<Trade>,
        dividends: List<Dividend>,
        symbols: List<String>,
        prev: Long,
        day: Long,
        close: (String, Long) -> Double,
    ): DayReturn? {
        fun marketValue(date: Long): Double = symbols.sumOf { s ->
            val shares = Portfolio.sharesOn(
                trades.filter { it.symbol == s },
                dividends.filter { it.symbol == s },
                date,
            )
            if (shares > 1e-9) shares * close(s, date) else 0.0
        }
        val inRange = { date: Long -> date > prev && date <= day }
        val buys = trades.filter { it.type == TradeType.BUY && inRange(it.date) }.sumOf { it.amount + it.fee }
        val sells = trades.filter { it.type == TradeType.SELL && inRange(it.date) }
            .sumOf { it.amount - it.fee - it.tax }
        val divs = dividends.filter { inRange(it.date) }.sumOf { it.netCash }
        val mvPrev = marketValue(prev)
        val mv = marketValue(day)
        if (mvPrev == 0.0 && mv == 0.0 && buys == 0.0 && sells == 0.0 && divs == 0.0) return null
        val pnl = mv - mvPrev - buys + sells + divs
        val base = mvPrev + buys
        return DayReturn(day, pnl, if (base > 0) pnl / base * 100 else 0.0)
    }

    /** 多天合計：損益相加，報酬率以複利連乘 */
    fun combine(days: List<DayReturn>): PeriodReturn = PeriodReturn(
        pnl = days.sumOf { it.pnl },
        pct = (days.fold(1.0) { acc, d -> acc * (1 + d.pct / 100) } - 1) * 100,
    )
}
