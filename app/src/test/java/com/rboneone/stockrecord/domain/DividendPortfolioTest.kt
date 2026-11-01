package com.rboneone.stockrecord.domain

import com.rboneone.stockrecord.data.Dividend
import com.rboneone.stockrecord.data.Market
import com.rboneone.stockrecord.data.Quote
import com.rboneone.stockrecord.data.Trade
import com.rboneone.stockrecord.data.TradeType
import org.junit.Assert.assertEquals
import org.junit.Test

class DividendPortfolioTest {
    private fun buy(id: Long, date: Long, shares: Double, price: Double, fee: Double) =
        Trade(id, Market.TW, "0056", "元大高股息", TradeType.BUY, date, shares, price, fee, 0.0)

    private fun sell(id: Long, date: Long, shares: Double, price: Double, fee: Double, tax: Double) =
        Trade(id, Market.TW, "0056", "元大高股息", TradeType.SELL, date, shares, price, fee, tax)

    private fun div(id: Long, date: Long, cash: Double, tax: Double = 0.0, fee: Double = 0.0, stock: Double = 0.0) =
        Dividend(id, Market.TW, "0056", "元大高股息", date, cash, tax, fee, stock)

    @Test
    fun cashDividend_lowersAdjustedCost() {
        val h = Portfolio.build(
            listOf(buy(1, 1, 1000.0, 35.0, 20.0)),
            listOf(div(1, 10, 2_000.0, fee = 10.0)),
        ).single()
        assertEquals(35.02, h.avgCost, 1e-9)            // 買均 (35,000 + 20) / 1000
        assertEquals((35_020.0 - 1_990) / 1000, h.adjustedAvgCost, 1e-9) // 成本均
        assertEquals(1_990.0, h.dividendIncome, 1e-9)
    }

    @Test
    fun stockDividend_addsSharesAtZeroCost() {
        val h = Portfolio.build(
            listOf(buy(1, 1, 1000.0, 50.0, 20.0)),
            listOf(div(1, 10, 0.0, stock = 50.0)),
        ).single()
        assertEquals(1050.0, h.shares, 0.0)
        assertEquals(50_020.0, h.totalCost, 1e-9)
        assertEquals(50_020.0 / 1050, h.avgCost, 1e-9)
    }

    @Test
    fun dividendsSinceOpen_resetAfterSellingOut_butIncomeKept() {
        val h = Portfolio.build(
            listOf(
                buy(1, 1, 1000.0, 30.0, 20.0),
                sell(2, 20, 1000.0, 32.0, 20.0, 32.0),
                buy(3, 30, 1000.0, 33.0, 20.0),
            ),
            listOf(div(1, 10, 1_500.0)),
        ).single()
        assertEquals(0.0, h.dividendsSinceOpen, 0.0)
        assertEquals(1_500.0, h.dividendIncome, 0.0)
        assertEquals(h.avgCost, h.adjustedAvgCost, 1e-9)
    }

    @Test
    fun summary_cumulativeIncludesDividends() {
        val settings = FeeSettings()
        val holdings = Portfolio.build(
            listOf(buy(1, 1, 1000.0, 30.0, 20.0)),
            listOf(div(1, 10, 2_000.0)),
        )
        val quotes = listOf(Quote(Market.TW, "0056", 31.0, 30.5, 0, 0)).associateBy { it.market to it.symbol }
        val view = Valuation.build(holdings, quotes, emptyMap(), settings)
        val s = view.summary(Market.TW)!!
        // 市值 31,000；預估賣出：手續費 floor(12.369) → 最低 20，證交稅 ETF 31
        val unrealized = 31_000.0 - 20 - 31 - 30_020
        assertEquals(unrealized, s.unrealizedPnl, 1e-9)
        assertEquals(2_000.0, s.dividendIncome, 0.0)
        assertEquals(unrealized + 2_000, s.cumulativePnl, 1e-9)
        assertEquals(unrealized + 2_000, s.totalPnl, 1e-9)
        assertEquals(500.0, s.dayPnl, 1e-9)
    }

    @Test
    fun dividendTax_suggestions() {
        val settings = FeeSettings()
        assertEquals(0.0, FeeCalculator.dividendTax(Market.TW, 19_999.0, settings), 0.0)
        assertEquals(422.0, FeeCalculator.dividendTax(Market.TW, 20_000.0, settings), 0.0) // 2.11%
        assertEquals(30.0, FeeCalculator.dividendTax(Market.US, 100.0, settings), 1e-9)
    }
}
