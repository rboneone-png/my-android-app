package com.rboneone.stockrecord.domain

import com.rboneone.stockrecord.data.Market
import com.rboneone.stockrecord.data.Trade
import com.rboneone.stockrecord.data.TradeType
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class AssetTrendTest {
    private fun day(y: Int, m: Int, d: Int) = LocalDate.of(y, m, d).toEpochDay()
    private fun buy(id: Long, s: String, date: Long, shares: Double, price: Double) =
        Trade(id, Market.TW, s, s, TradeType.BUY, date, shares, price, 0.0, 0.0)
    private fun sell(id: Long, s: String, date: Long, shares: Double, price: Double) =
        Trade(id, Market.TW, s, s, TradeType.SELL, date, shares, price, 0.0, 0.0)

    private val today = day(2026, 3, 15)
    private val months = listOf(YearMonth.of(2026, 1), YearMonth.of(2026, 2), YearMonth.of(2026, 3))

    @Test
    fun monthEndValue_usesLastCloseOnOrBeforeMonthEnd() {
        val trades = listOf(buy(1, "A", day(2026, 1, 10), 1000.0, 50.0))
        val bars = mapOf("A" to listOf(
            Bar(day(2026, 1, 30), 55.0), Bar(day(2026, 2, 26), 60.0), Bar(day(2026, 3, 13), 70.0),
        ))
        val p = AssetTrend.compute(trades, emptyList(), bars, months, today)
        // 1 月底（31 日休市）用 1/30 收盤；2 月底 2/27、28 休市用 2/26；3 月用今天以前最後一筆
        assertEquals(listOf(55_000.0, 60_000.0, 70_000.0), p.map { it.marketValue })
        assertEquals(listOf(50_000.0, 50_000.0, 50_000.0), p.map { it.cost })
        assertEquals(20_000.0, p.last().gain, 1e-9)
    }

    @Test
    fun soldStock_leavesAfterSellMonth_andCostFollowsAverageCost() {
        val trades = listOf(
            buy(1, "A", day(2026, 1, 5), 1000.0, 50.0),
            buy(2, "A", day(2026, 2, 5), 1000.0, 70.0),     // 平均成本 60
            sell(3, "A", day(2026, 3, 2), 1000.0, 80.0),    // 賣一半，剩 1000 股成本 60,000
        )
        val bars = mapOf("A" to listOf(Bar(day(2026, 1, 30), 52.0), Bar(day(2026, 2, 27), 72.0), Bar(day(2026, 3, 13), 78.0)))
        val p = AssetTrend.compute(trades, emptyList(), bars, months, today)
        assertEquals(listOf(52_000.0, 144_000.0, 78_000.0), p.map { it.marketValue })
        assertEquals(listOf(50_000.0, 120_000.0, 60_000.0), p.map { it.cost })
    }

    @Test
    fun currentMonth_prefersLatestQuote_andNoHistoryFallsBackToTradePrice() {
        val trades = listOf(buy(1, "A", day(2026, 1, 5), 1000.0, 50.0), buy(2, "B", day(2026, 2, 5), 100.0, 200.0))
        val bars = mapOf("A" to listOf(Bar(day(2026, 3, 13), 70.0)))
        val p = AssetTrend.compute(trades, emptyList(), bars, months, today, latest = mapOf("A" to 72.5))
        // 3 月：A 用即時價 72.5；B 沒有歷史價格，用最近成交價 200
        assertEquals(72_500.0 + 20_000.0, p.last().marketValue, 1e-9)
        // 1 月底還沒買 B，A 沒有 1 月的歷史價格 → 用成交價 50
        assertEquals(50_000.0, p.first().marketValue, 1e-9)
    }

    @Test
    fun monthsFrom() {
        assertEquals(3, AssetTrend.monthsFrom(YearMonth.of(2026, 1), YearMonth.of(2026, 3)).size)
        assertEquals(listOf(YearMonth.of(2026, 3)), AssetTrend.monthsFrom(YearMonth.of(2026, 3), YearMonth.of(2026, 3)))
        assertEquals(emptyList<YearMonth>(), AssetTrend.monthsFrom(YearMonth.of(2026, 4), YearMonth.of(2026, 3)))
    }

    @Test
    fun niceTicks() {
        assertEquals(listOf(0.0, 500.0, 1000.0, 1500.0, 2000.0, 2500.0), AssetTrend.niceTicks(2440.0))
        assertEquals(listOf(0.0, 20.0, 40.0, 60.0, 80.0, 100.0), AssetTrend.niceTicks(97.0))
        val t = AssetTrend.niceTicks(7.3)
        assertEquals(listOf(0.0, 2.0, 4.0, 6.0, 8.0), t)
        assertEquals(listOf(0.0, 1.0), AssetTrend.niceTicks(0.0))
    }
}
