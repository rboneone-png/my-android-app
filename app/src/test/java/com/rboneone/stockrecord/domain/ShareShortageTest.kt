package com.rboneone.stockrecord.domain

import com.rboneone.stockrecord.data.Dividend
import com.rboneone.stockrecord.data.Market
import com.rboneone.stockrecord.data.Trade
import com.rboneone.stockrecord.data.TradeType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ShareShortageTest {
    private fun buy(id: Long, date: Long, shares: Double, symbol: String = "A", market: Market = Market.TW) =
        Trade(id, market, symbol, symbol, TradeType.BUY, date, shares, 10.0, 0.0, 0.0)

    private fun sell(id: Long, date: Long, shares: Double, symbol: String = "A", market: Market = Market.TW) =
        Trade(id, market, symbol, symbol, TradeType.SELL, date, shares, 10.0, 0.0, 0.0)

    private fun check(trades: List<Trade>, dividends: List<Dividend> = emptyList()) =
        Portfolio.firstShortage(trades, dividends, Market.TW, "A")

    @Test
    fun enoughShares_ok() {
        assertNull(check(listOf(buy(1, 10, 1000.0), sell(2, 20, 600.0), sell(3, 30, 400.0))))
        assertNull(check(emptyList()))
    }

    @Test
    fun sellMoreThanHeld_flagged() {
        val s = check(listOf(buy(1, 10, 1000.0), sell(2, 20, 1500.0)))!!
        assertEquals(20L, s.date)
        assertEquals(1000.0, s.available, 0.0)
        assertEquals(1500.0, s.sold, 0.0)
    }

    @Test
    fun neverHeld_flagged() {
        val s = check(listOf(sell(1, 20, 100.0)))!!
        assertEquals(0.0, s.available, 0.0)
    }

    @Test
    fun backDatedSell_beforeSharesWereBought_flagged() {
        // 總持股 2,000 夠賣 1,500，但 2/1（第 20 天）當天只有 1,000 股
        val trades = listOf(buy(1, 10, 1000.0), buy(2, 30, 1000.0), sell(3, 20, 1500.0))
        val s = check(trades)!!
        assertEquals(20L, s.date)
        assertEquals(1000.0, s.available, 0.0)
    }

    @Test
    fun laterSellsBecomeShort_flagged() {
        // 新增 20 日賣 500 後，30 日原本賣的 600 就不夠（只剩 500）
        val trades = listOf(buy(1, 10, 1000.0), sell(2, 30, 600.0), sell(3, 20, 500.0))
        val s = check(trades)!!
        assertEquals(30L, s.date)
        assertEquals(500.0, s.available, 0.0)
        assertEquals(600.0, s.sold, 0.0)
    }

    @Test
    fun sameDayBuyThenSell_ok() {
        // 同一天先買再賣（當沖），不管輸入順序
        assertNull(check(listOf(sell(1, 10, 500.0), buy(2, 10, 500.0))))
        assertEquals(500.0, check(listOf(buy(1, 10, 500.0), sell(2, 10, 800.0)))!!.available, 0.0)
    }

    @Test
    fun stockDividendCountsAsShares() {
        val div = Dividend(1, Market.TW, "A", "A", 15, 0.0, 0.0, 0.0, 100.0)
        assertNull(check(listOf(buy(1, 10, 1000.0), sell(2, 20, 1100.0)), listOf(div)))
        // 配股在賣出之後才發，賣出時還沒有那 100 股
        assertEquals(1000.0, check(listOf(buy(1, 10, 1000.0), sell(2, 12, 1100.0)), listOf(div))!!.available, 0.0)
    }

    @Test
    fun otherSymbolOrMarketIgnored() {
        val trades = listOf(
            buy(1, 10, 100.0),
            sell(2, 20, 5000.0, symbol = "B"),                   // 別檔股票
            sell(3, 20, 5000.0, market = Market.US),            // 美股同代號
        )
        assertNull(check(trades))
    }

    @Test
    fun deletingBuyThatLaterSellsRelyOn_flagged() {
        val trades = listOf(buy(1, 10, 1000.0), sell(2, 20, 600.0))
        assertNull(check(trades))
        assertEquals(20L, check(trades.filter { it.id != 1L })!!.date)
    }

    @Test
    fun portfolioSameDaySellBeforeBuyById_stillNonNegative() {
        // 賣出的 id 比買進小，仍然先處理買進，持股與成本不會變成負數
        val h = Portfolio.build(listOf(sell(1, 10, 500.0), buy(2, 10, 1000.0))).single()
        assertEquals(500.0, h.shares, 0.0)
        assertEquals(5_000.0, h.totalCost, 1e-9)    // 剩 500 股 × 平均成本 10
    }
}
