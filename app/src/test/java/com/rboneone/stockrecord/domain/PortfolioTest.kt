package com.rboneone.stockrecord.domain

import com.rboneone.stockrecord.data.Market
import com.rboneone.stockrecord.data.Trade
import com.rboneone.stockrecord.data.TradeType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class PortfolioTest {
    private fun trade(id: Long, type: TradeType, date: Long, shares: Double, price: Double, fee: Double, tax: Double = 0.0) =
        Trade(id, Market.TW, "2330", "台積電", type, date, shares, price, fee, tax)

    @Test
    fun averageCost_andRealizedPnl() {
        val trades = listOf(
            trade(1, TradeType.BUY, 1, 1000.0, 500.0, 200.0),  // 成本 500,200
            trade(2, TradeType.BUY, 2, 1000.0, 600.0, 239.0),  // 成本 600,239，合計 1,100,439
            trade(3, TradeType.SELL, 3, 1000.0, 700.0, 279.0, 2100.0),
        )
        val h = Portfolio.build(trades).single()
        assertEquals(1000.0, h.shares, 0.0)
        assertEquals(550.2195, h.avgCost, 1e-6)
        // 700,000 - 279 - 2,100 - 550,219.5 = 147,401.5
        assertEquals(147_401.5, h.realizedPnl, 1e-6)
        assertEquals(550_219.5, h.totalCost, 1e-6)
    }

    @Test
    fun sellingEverything_closesPosition() {
        val trades = listOf(
            trade(1, TradeType.BUY, 1, 1000.0, 100.0, 20.0),
            trade(2, TradeType.SELL, 2, 1000.0, 90.0, 20.0, 270.0),
        )
        val h = Portfolio.build(trades).single()
        assertFalse(h.isOpen)
        assertEquals(0.0, h.totalCost, 0.0)
        assertEquals(90_000.0 - 20 - 270 - 100_020, h.realizedPnl, 1e-6)
    }

    @Test
    fun sharesHeld_excludesEditedTrade() {
        val trades = listOf(
            trade(1, TradeType.BUY, 1, 1000.0, 100.0, 20.0),
            trade(2, TradeType.SELL, 2, 400.0, 110.0, 20.0, 132.0),
        )
        assertEquals(600.0, Portfolio.sharesHeld(trades, Market.TW, "2330"), 0.0)
        assertEquals(1000.0, Portfolio.sharesHeld(trades, Market.TW, "2330", excludeId = 2), 0.0)
    }
}
