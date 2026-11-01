package com.rboneone.stockrecord.domain

import com.rboneone.stockrecord.data.Market
import com.rboneone.stockrecord.data.Quote
import com.rboneone.stockrecord.data.TaxType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ValuationTest {
    private val settings = FeeSettings(twDiscount = 0.28, twMinFee = 20.0, usCommission = 0.0)

    private fun quote(market: Market, symbol: String, price: Double, prev: Double = price) =
        Quote(market, symbol, price, prev, 0, 0)

    @Test
    fun twPosition_deductsEstimatedSellCosts() {
        val h = Holding(Market.TW, "2330", "台積電", 1000.0, 600_239.0, 0.0)
        val p = Valuation.position(h, quote(Market.TW, "2330", 700.0, 690.0), null, settings)
        // 市值 700,000；手續費 floor(700000 × 0.001425 × 0.28) = 279；證交稅 2,100
        assertEquals(700_000.0, p.marketValue!!, 0.0)
        assertEquals(700_000.0 - 279 - 2_100 - 600_239, p.unrealizedPnl!!, 1e-6)
        assertEquals(10.0, p.dayChange!!, 1e-9)
        assertEquals(10.0 / 690 * 100, p.dayChangePct!!, 1e-9)
    }

    @Test
    fun etfUsesLowerTax() {
        assertEquals(TaxType.ETF, Valuation.defaultTaxType(Market.TW, "0050"))
        assertEquals(TaxType.STOCK, Valuation.defaultTaxType(Market.TW, "2330"))
        assertEquals(TaxType.STOCK, Valuation.defaultTaxType(Market.US, "0050"))
        // 100,000 × 0.1% = 100；手續費 floor(39.9) = 39
        assertEquals(139.0, Valuation.estimatedSellCost(Market.TW, "0050", 100_000.0, settings), 0.0)
    }

    @Test
    fun noQuote_leavesValueEmpty() {
        val h = Holding(Market.TW, "2330", "2330", 1000.0, 600_000.0, 0.0)
        val p = Valuation.position(h, null, "台積電", settings)
        assertNull(p.marketValue)
        assertNull(p.unrealizedPnl)
        assertEquals("台積電", p.displayName)
    }

    @Test
    fun summaries_perMarketInOwnCurrency() {
        val holdings = listOf(
            Holding(Market.TW, "2330", "台積電", 1000.0, 600_000.0, 5_000.0),
            Holding(Market.US, "AAPL", "Apple", 10.0, 2_000.0, 100.0),
        )
        val quotes = listOf(
            quote(Market.TW, "2330", 600.0),
            quote(Market.US, "AAPL", 250.0),
            quote(Market.US, Quote.USD_TWD, 32.0),
        ).associateBy { it.market to it.symbol }
        val view = Valuation.build(holdings, quotes, emptyMap(), settings)
        val tw = view.summary(Market.TW)!!
        val us = view.summary(Market.US)!!
        assertEquals(600_000.0, tw.marketValue, 1e-6)
        assertEquals(-(239.0 + 1_800.0), tw.unrealizedPnl, 1e-6) // 預估賣出手續費與證交稅
        assertEquals(5_000.0, tw.realizedPnl, 1e-6)
        assertEquals(2_500.0, us.marketValue, 1e-6)                // 美股以美元計
        assertEquals(500.0, us.unrealizedPnl, 1e-6)
        assertEquals(32.0, view.usdTwd!!.price, 0.0)
    }

    @Test
    fun missingQuote_countsCostAsMarketValue() {
        val holdings = listOf(Holding(Market.US, "AAPL", "Apple", 10.0, 2_000.0, 0.0))
        val view = Valuation.build(holdings, emptyMap(), emptyMap(), settings)
        assertNull(view.usdTwd)
        assertEquals(2_000.0, view.summaries.single().marketValue, 0.0)
        assertEquals(1, view.summaries.single().missingQuotes)
    }

    @Test
    fun customOrder_newHoldingsGoLast() {
        val positions = listOf("2330", "0050", "2884", "0056").map {
            Valuation.position(Holding(Market.TW, it, it, 100.0, 1000.0, 0.0), null, null, settings)
        }
        // 使用者排過 2884、2330；0050、0056 是之後新買的，依代號排在最後
        val ordered = positions.inCustomOrder(listOf("2884", "9999", "2330"))
        assertEquals(listOf("2884", "2330", "0050", "0056"), ordered.map { it.holding.symbol })
        // 沒有自訂順序時依代號
        assertEquals(listOf("0050", "0056", "2330", "2884"), positions.inCustomOrder(emptyList()).map { it.holding.symbol })
    }
}
