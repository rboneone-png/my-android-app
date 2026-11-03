package com.rboneone.stockrecord.domain

import com.rboneone.stockrecord.data.Market
import com.rboneone.stockrecord.data.Quote
import com.rboneone.stockrecord.data.Trade
import com.rboneone.stockrecord.data.TradeType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/** 持股頁的「持股日損益」與月報酬今天的損益，必須是同一個數字 */
class DayPnlConsistencyTest {
    private fun day(y: Int, m: Int, d: Int) = LocalDate.of(y, m, d).toEpochDay()
    private val prev = day(2026, 10, 30)
    private val today = day(2026, 11, 2)
    private val settings = FeeSettings(twDiscount = 0.28, twMinFee = 20.0, usCommission = 0.0)

    private fun buy(id: Long, date: Long, shares: Double, price: Double, fee: Double = 0.0) =
        Trade(id, Market.TW, "A", "A", TradeType.BUY, date, shares, price, fee, 0.0)

    private fun sell(id: Long, date: Long, shares: Double, price: Double, fee: Double, tax: Double) =
        Trade(id, Market.TW, "A", "A", TradeType.SELL, date, shares, price, fee, tax)

    /** 台北時間 today 10:30 的報價 */
    private fun quote(price: Double, prevClose: Double): Quote {
        val t = today.let { LocalDate.ofEpochDay(it).atTime(10, 30).atZone(ZoneId.of("Asia/Taipei")).toEpochSecond() }
        return Quote(Market.TW, "A", price, prevClose, t, 0)
    }

    /** 三種算法：月報酬今天、LiveClose.today、持股頁彙總 */
    private fun all(trades: List<Trade>, bars: List<Bar>, q: Quote): Triple<DayReturn?, DayReturn?, Double> {
        val live = LiveClose.from(listOf(q), setOf("A"))
        val monthly = DailyReturns.compute(trades, emptyList(), mapOf("A" to bars), prev - 10, today, live).lastOrNull { it.date == today }
        val direct = DailyReturns.today(trades, emptyList(), live)
        val view = Valuation.build(Portfolio.build(trades), mapOf((Market.TW to "A") to q), emptyMap(), settings, trades)
        return Triple(monthly, direct, view.summary(Market.TW)!!.dayPnl)
    }

    private fun assertAllEqual(expected: Double, r: Triple<DayReturn?, DayReturn?, Double>) {
        assertEquals(expected, r.first!!.pnl, 1e-6)
        assertEquals(expected, r.second!!.pnl, 1e-6)
        assertEquals(expected, r.third, 1e-6)
        assertEquals(r.first!!.pct, r.second!!.pct, 1e-9)
    }

    @Test
    fun noTradesToday_usesLivePriceNotDelayedHistory() {
        // Yahoo 日線還停在 101（延遲），即時價是 103，前收 100
        val bars = listOf(Bar(prev, 100.0), Bar(today, 101.0))
        assertAllEqual(3_000.0, all(listOf(buy(1, day(2026, 10, 1), 1000.0, 90.0)), bars, quote(103.0, 100.0)))
    }

    @Test
    fun buyToday_isCountedTheSameWay() {
        val trades = listOf(buy(1, day(2026, 10, 1), 1000.0, 90.0), buy(2, today, 500.0, 102.0, fee = 20.0))
        val bars = listOf(Bar(prev, 100.0), Bar(today, 101.0))
        // 1500 × 103 − 1000 × 100 − (500 × 102 + 20) = 3,480
        assertAllEqual(3_480.0, all(trades, bars, quote(103.0, 100.0)))
    }

    @Test
    fun sellToday_isCountedTheSameWay() {
        val trades = listOf(buy(1, day(2026, 10, 1), 1000.0, 90.0), sell(2, today, 400.0, 104.0, fee = 30.0, tax = 100.0))
        val bars = listOf(Bar(prev, 100.0), Bar(today, 101.0))
        // 600 × 103 − 1000 × 100 + (400 × 104 − 130) = 3,270
        assertAllEqual(3_270.0, all(trades, bars, quote(103.0, 100.0)))
    }

    @Test
    fun previousCloseComesFromLiveQuote() {
        // 日線的前一日收盤 99 與即時報價的前收 100 不同，以即時報價為準（與持股頁相同）
        val bars = listOf(Bar(prev, 99.0), Bar(today, 101.0))
        assertAllEqual(3_000.0, all(listOf(buy(1, day(2026, 10, 1), 1000.0, 90.0)), bars, quote(103.0, 100.0)))
    }

    @Test
    fun todayMissingFromHistory_stillShownFromLiveQuote() {
        // Yahoo 日線還沒有今天這一筆
        assertAllEqual(3_000.0, all(listOf(buy(1, day(2026, 10, 1), 1000.0, 90.0)), listOf(Bar(prev, 100.0)), quote(103.0, 100.0)))
    }

    @Test
    fun withoutLive_monthlyKeepsHistoryBehaviour_andSummaryFallsBack() {
        val trades = listOf(buy(1, day(2026, 10, 1), 1000.0, 90.0))
        val bars = listOf(Bar(prev, 100.0), Bar(today, 101.0))
        val monthly = DailyReturns.compute(trades, emptyList(), mapOf("A" to bars), prev - 10, today)
        assertEquals(1_000.0, monthly.last().pnl, 1e-9)
        assertNull(DailyReturns.today(trades, emptyList(), emptyMap()))
        // 沒有報價時持股頁彙總為 0，不報錯
        val view = Valuation.build(Portfolio.build(trades), emptyMap(), emptyMap(), settings, trades)
        assertEquals(0.0, view.summary(Market.TW)!!.dayPnl, 0.0)
    }
}
