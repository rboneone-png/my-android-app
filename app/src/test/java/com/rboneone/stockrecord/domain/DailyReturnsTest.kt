package com.rboneone.stockrecord.domain

import com.rboneone.stockrecord.data.Dividend
import com.rboneone.stockrecord.data.Market
import com.rboneone.stockrecord.data.Trade
import com.rboneone.stockrecord.data.TradeType
import org.junit.Assert.assertEquals
import org.junit.Test

class DailyReturnsTest {
    private fun trade(id: Long, type: TradeType, date: Long, shares: Double, price: Double, fee: Double = 0.0, tax: Double = 0.0) =
        Trade(id, Market.TW, "2330", "台積電", type, date, shares, price, fee, tax)

    @Test
    fun priceMovesOnly() {
        val trades = listOf(trade(1, TradeType.BUY, 1, 1000.0, 100.0))
        val bars = mapOf("2330" to listOf(Bar(1, 100.0), Bar(2, 110.0), Bar(3, 99.0)))
        val days = DailyReturns.compute(trades, emptyList(), bars, from = 2, to = 3)
        assertEquals(2, days.size)
        assertEquals(10_000.0, days[0].pnl, 1e-9)
        assertEquals(10.0, days[0].pct, 1e-9)
        assertEquals(-11_000.0, days[1].pnl, 1e-9)
        assertEquals(-10.0, days[1].pct, 1e-9)
        val total = DailyReturns.combine(days)
        assertEquals(-1_000.0, total.pnl, 1e-9)
        assertEquals((1.1 * 0.9 - 1) * 100, total.pct, 1e-9)
    }

    @Test
    fun buyingDuringTheDay_isNotCountedAsProfit() {
        val trades = listOf(
            trade(1, TradeType.BUY, 1, 1000.0, 100.0),
            trade(2, TradeType.BUY, 2, 1000.0, 105.0, fee = 20.0),
        )
        val bars = mapOf("2330" to listOf(Bar(1, 100.0), Bar(2, 106.0)))
        val day = DailyReturns.compute(trades, emptyList(), bars, from = 2, to = 2).single()
        // 舊持股 +6,000，新買的 +1,000 − 手續費 20
        assertEquals(6_980.0, day.pnl, 1e-9)
        assertEquals(6_980.0 / (100_000 + 105_020) * 100, day.pct, 1e-9)
    }

    @Test
    fun sellingAndDividends() {
        val trades = listOf(
            trade(1, TradeType.BUY, 1, 1000.0, 100.0),
            trade(2, TradeType.SELL, 2, 1000.0, 102.0, fee = 20.0, tax = 306.0),
        )
        val dividends = listOf(Dividend(1, Market.TW, "2330", "台積電", 2, 500.0, 0.0, 10.0, 0.0))
        val bars = mapOf("2330" to listOf(Bar(1, 100.0), Bar(2, 103.0)))
        val day = DailyReturns.compute(trades, dividends, bars, from = 2, to = 2).single()
        // 前一天市值 100,000 → 今天 0；賣出淨收 101,674；股利 490
        assertEquals(101_674.0 + 490 - 100_000, day.pnl, 1e-9)
    }

    @Test
    fun weekendTradeIsCountedOnNextTradingDay() {
        val trades = listOf(
            trade(1, TradeType.BUY, 1, 1000.0, 100.0),
            trade(2, TradeType.BUY, 4, 1000.0, 100.0), // 非交易日輸入
        )
        val bars = mapOf("2330" to listOf(Bar(1, 100.0), Bar(5, 100.0)))
        val day = DailyReturns.compute(trades, emptyList(), bars, from = 2, to = 5).single()
        assertEquals(5L, day.date)
        assertEquals(0.0, day.pnl, 1e-9)
    }
}
