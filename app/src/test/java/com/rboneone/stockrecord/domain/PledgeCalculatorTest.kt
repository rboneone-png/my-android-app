package com.rboneone.stockrecord.domain

import com.rboneone.stockrecord.data.AssetCategory
import com.rboneone.stockrecord.data.Market
import com.rboneone.stockrecord.data.Pledge
import com.rboneone.stockrecord.data.Quote
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PledgeCalculatorTest {
    private fun quote(symbol: String, price: Double) = Quote(Market.TW, symbol, price, price, 0, 0)

    private val pledges = listOf(
        Pledge(1, "2330", "台積電", 1000.0, 1_000_000.0, 2.5, 0),  // 市值 2,400,000 → 240%
        Pledge(2, "0050", "元大台灣50", 10_000.0, 300_000.0, 2.0, 0), // 市值 1,000,000 → 333.3%
    )
    private val quotes = listOf(quote("2330", 2400.0), quote("0050", 100.0)).associateBy { it.market to it.symbol }

    @Test
    fun formulas() {
        val v = PledgeCalculator.build(pledges, quotes, portfolio = null)
        // 利息支出 = E × D
        assertEquals(25_000.0, v.items[0].pledge.interest, 1e-9)
        assertEquals(6_000.0, v.items[1].pledge.interest, 1e-9)
        assertEquals(31_000.0, v.totalInterest, 1e-9)
        // 維持率 = 質押股數 × 股價 ÷ 質押金額 × 100%
        assertEquals(240.0, v.items[0].ratio!!, 1e-9)
        assertEquals(1_000_000.0 / 300_000 * 100, v.items[1].ratio!!, 1e-9)
        // 質押維持率 = 質押總市值 ÷ 質押總金額 × 100%
        assertEquals(3_400_000.0, v.totalMarketValue, 1e-9)
        assertEquals(1_300_000.0, v.totalAmount, 1e-9)
        assertEquals(3_400_000.0 / 1_300_000 * 100, v.ratio!!, 1e-9)
    }

    @Test
    fun levels_boundaries() {
        assertEquals(PledgeLevel.SAFE, PledgeCalculator.level(250.0))
        assertEquals(PledgeLevel.SAFE, PledgeCalculator.level(400.0))
        assertEquals(PledgeLevel.WARNING, PledgeCalculator.level(249.94))  // 顯示 249.9%
        assertEquals(PledgeLevel.WARNING, PledgeCalculator.level(175.0))
        assertEquals(PledgeLevel.DANGER, PledgeCalculator.level(174.94))   // 顯示 174.9%
    }

    @Test
    fun level_matchesDisplayedValue() {
        // 249.98% 畫面顯示 250.0%，顏色要一致為綠色
        assertEquals(PledgeLevel.SAFE, PledgeCalculator.level(249.98))
        // 174.96% 顯示 175.0%，屬黃色而非紅色
        assertEquals(PledgeLevel.WARNING, PledgeCalculator.level(174.96))
    }

    @Test
    fun missingQuote_noTotalRatio() {
        val v = PledgeCalculator.build(pledges, quotes - (Market.TW to "0050"), portfolio = null)
        assertNull(v.items[1].ratio)
        assertNull(v.ratio)
        assertEquals(1, v.missingQuotes)
    }

    @Test
    fun pledgeableValue_isTwHoldingsMarketValue() {
        val holdings = listOf(
            Holding(Market.TW, "2330", "台積電", 2000.0, 3_000_000.0, 0.0),
            Holding(Market.US, "AAPL", "Apple", 10.0, 2_000.0, 0.0),
        )
        val portfolio = Valuation.build(holdings, quotes, emptyMap(), FeeSettings())
        val v = PledgeCalculator.build(pledges, quotes, portfolio)
        assertEquals(4_800_000.0, v.pledgeableValue, 1e-9)
    }

    @Test
    fun pledgeableValue_canExcludeLeveraged() {
        val holdings = listOf(
            Holding(Market.TW, "2330", "台積電", 2000.0, 3_000_000.0, 0.0),        // 市值 4,800,000
            Holding(Market.TW, "0050", "元大台灣50", 10_000.0, 900_000.0, 0.0),   // 市值 1,000,000（正二分類測試用）
        )
        val portfolio = Valuation.build(holdings, quotes, emptyMap(), FeeSettings())
        val categories = mapOf((Market.TW to "0050") to AssetCategory.LEVERAGED_2X)
        assertEquals(5_800_000.0, PledgeCalculator.build(pledges, quotes, portfolio, categories, true).pledgeableValue, 1e-9)
        assertEquals(4_800_000.0, PledgeCalculator.build(pledges, quotes, portfolio, categories, false).pledgeableValue, 1e-9)
        // 排除正二不影響質押本身的維持率
        assertEquals(
            PledgeCalculator.build(pledges, quotes, portfolio, categories, true).ratio,
            PledgeCalculator.build(pledges, quotes, portfolio, categories, false).ratio,
        )
    }
}
