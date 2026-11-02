package com.rboneone.stockrecord.domain

import com.rboneone.stockrecord.data.Dividend
import com.rboneone.stockrecord.data.Trade
import java.time.LocalDate
import java.time.YearMonth

/** 某個月底的持股市值與持股成本（同一市場、各自幣別） */
data class AssetPoint(val month: YearMonth, val marketValue: Double, val cost: Double) {
    /** 未實現損益（未扣預估賣出費用） */
    val gain: Double get() = marketValue - cost
}

/**
 * 資產走勢：每個月底的持股市值（當月則為今天）。
 * 市值 = 月底持有股數 × 月底最後一個交易日的收盤價；成本為當時持股的平均成本法總成本。
 */
object AssetTrend {
    fun compute(
        trades: List<Trade>,
        dividends: List<Dividend>,
        bars: Map<String, List<Bar>>,
        months: List<YearMonth>,
        today: Long,
        /** 今天的即時價（有的話取代收盤價），key 為股票代號 */
        latest: Map<String, Double> = emptyMap(),
    ): List<AssetPoint> {
        val sortedBars = bars.mapValues { (_, list) -> list.sortedBy { it.date } }
        val symbols = (trades.map { it.symbol } + dividends.map { it.symbol }).distinct()

        fun price(symbol: String, date: Long): Double {
            if (date >= today) latest[symbol]?.let { return it }
            sortedBars[symbol]?.lastOrNull { it.date <= date }?.let { return it.close }
            // 沒有歷史價格時用最近一次成交價
            return trades.filter { it.symbol == symbol && it.date <= date }.maxByOrNull { it.date }?.price ?: 0.0
        }

        return months.map { month ->
            val date = minOf(month.atEndOfMonth().toEpochDay(), today)
            val tradesUpTo = trades.filter { it.date <= date }
            val dividendsUpTo = dividends.filter { it.date <= date }
            val value = symbols.sumOf { s ->
                val shares = Portfolio.sharesOn(tradesUpTo.filter { it.symbol == s }, dividendsUpTo.filter { it.symbol == s }, date)
                if (shares > 1e-9) shares * price(s, date) else 0.0
            }
            val cost = Portfolio.build(tradesUpTo, dividendsUpTo).filter { it.isOpen }.sumOf { it.totalCost }
            AssetPoint(month, value, cost)
        }
    }

    /** 從第一筆紀錄的月份到本月 */
    fun monthsFrom(first: YearMonth, current: YearMonth): List<YearMonth> =
        generateSequence(first) { it.plusMonths(1) }.takeWhile { it <= current }.toList()

    fun monthOf(epochDay: Long): YearMonth = YearMonth.from(LocalDate.ofEpochDay(epochDay))

    /** 圖表 Y 軸的刻度：0 開始、間距取 1／2／2.5／5 × 10^n，涵蓋 max，最多 5 格 */
    fun niceTicks(max: Double): List<Double> {
        if (max <= 0) return listOf(0.0, 1.0)
        val raw = max / 5
        val pow = Math.pow(10.0, Math.floor(Math.log10(raw)))
        val step = listOf(1.0, 2.0, 2.5, 5.0, 10.0).map { it * pow }.first { it >= raw }
        val count = Math.ceil(max / step).toInt()
        return (0..count).map { it * step }
    }
}
