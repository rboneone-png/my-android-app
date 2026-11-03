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

    @Test
    fun months_groupedNewestFirst() {
        fun day(y: Int, m: Int, d: Int) = java.time.LocalDate.of(y, m, d).toEpochDay()
        val list = listOf(
            Pledge(1, "2330", "台積電", 1000.0, 1_000_000.0, 2.5, day(2026, 9, 5)),
            Pledge(2, "0050", "元大台灣50", 10_000.0, 300_000.0, 2.0, day(2026, 10, 3)),
            Pledge(3, "2330", "台積電", 500.0, 500_000.0, 2.5, day(2026, 10, 20)),
        )
        val months = PledgeCalculator.build(list, quotes, portfolio = null).months
        assertEquals(listOf(java.time.YearMonth.of(2026, 10), java.time.YearMonth.of(2026, 9)), months.map { it.month })
        assertEquals(listOf(3L, 2L), months[0].items.map { it.pledge.id })
        assertEquals(800_000.0, months[0].totalAmount, 1e-9)
        // 10 月：500 × 2400 + 10000 × 100 = 2,200,000 ÷ 800,000 = 275%
        assertEquals(275.0, months[0].ratio!!, 1e-9)
        assertEquals(240.0, months[1].ratio!!, 1e-9)
    }

    @Test
    fun monthRatio_nullWhenQuoteMissing() {
        val list = listOf(Pledge(1, "9999", "未知", 1000.0, 100_000.0, 2.0, 0))
        assertNull(PledgeCalculator.build(list, quotes, portfolio = null).months.single().ratio)
    }

    @Test
    fun pledgeableShares_oddLotsDroppedAndPledgedSubtracted() {
        assertEquals(23_000.0, PledgeCalculator.pledgeableShares(23_211.0, 0.0), 0.0)
        assertEquals(0.0, PledgeCalculator.pledgeableShares(999.0, 0.0), 0.0)
        assertEquals(1_000.0, PledgeCalculator.pledgeableShares(1_000.0, 0.0), 0.0)
        // 減除已質押：10,500 − 9,000 = 1,500 → 1,000；10,500 − 9,600 = 900 → 0
        assertEquals(1_000.0, PledgeCalculator.pledgeableShares(10_500.0, 9_000.0), 0.0)
        assertEquals(0.0, PledgeCalculator.pledgeableShares(10_500.0, 9_600.0), 0.0)
        assertEquals(0.0, PledgeCalculator.pledgeableShares(1_000.0, 2_000.0), 0.0)
    }

    private fun position(symbol: String, shares: Double, market: Market = Market.TW) =
        Valuation.position(
            Holding(market, symbol, symbol, shares, shares * 10, 0.0),
            quotes[market to symbol], symbol, FeeSettings(),
        )

    @Test
    fun candidates_filtersAndRounds() {
        val positions = listOf(
            position("2330", 23_211.0),     // → 23,000
            position("0050", 900.0),        // 不足 1 張，排除
            position("00631L", 5_000.0),    // 正二
            position("2884", 10_500.0),     // 已質押 9,600 → 900，排除
            position("AAPL", 100_000.0, Market.US),  // 美股不列入
        )
        val view = PortfolioView(positions, emptyList(), null)
        val pledged = listOf(Pledge(1, "2884", "玉山金", 9_600.0, 500_000.0, 2.0, 0))
        val cats = mapOf((Market.TW to "00631L") to AssetCategory.LEVERAGED_2X)
        val all = PledgeCalculator.candidates(pledged, quotes + mapOf((Market.TW to "00631L") to quote("00631L", 33.0)), view, cats)
        assertEquals(listOf("00631L", "2330"), all.map { it.symbol })
        val tsmc = all.single { it.symbol == "2330" }
        assertEquals(23_000.0, tsmc.shares, 0.0)
        assertEquals(2400.0, tsmc.price!!, 0.0)
        assertEquals(23_000 * 2400.0, tsmc.marketValue!!, 0.0)
        assertEquals(true, all.single { it.symbol == "00631L" }.leveraged)
        assertEquals(false, tsmc.leveraged)
    }

    @Test
    fun amountMustBeMultipleOfThousand() {
        assertNull(PledgeCalculator.amountError(5_000.0))
        assertNull(PledgeCalculator.amountError(1_234_000.0))
        listOf(5_100.0, 5_010.0, 5_001.0, 5_122.0, 500.0, 5_000.5).forEach {
            assertEquals("$it", true, PledgeCalculator.amountError(it) != null)
        }
        assertEquals(true, PledgeCalculator.amountError(null) != null)
        assertEquals(true, PledgeCalculator.amountError(0.0) != null)
        assertEquals(true, PledgeCalculator.amountError(-1_000.0) != null)
    }

    @Test
    fun ltv_maxSixtyPercent() {
        assertEquals(50.0, PledgeCalculator.ltvPct(1_000_000.0, 2_000_000.0)!!, 1e-9)
        assertNull(PledgeCalculator.ltvPct(1_000_000.0, null))
        assertNull(PledgeCalculator.ltvPct(1_000_000.0, 0.0))
        assertEquals(false, PledgeCalculator.exceedsMaxLtv(60.0))
        assertEquals(false, PledgeCalculator.exceedsMaxLtv(59.99))
        assertEquals(true, PledgeCalculator.exceedsMaxLtv(60.01))
        // 顯示為 60.00% 的不算超過
        assertEquals(false, PledgeCalculator.exceedsMaxLtv(60.004))
        // 可質押市值 1,000,000 → 最高可借 600,000；601,000 超過
        assertEquals(false, PledgeCalculator.exceedsMaxLtv(PledgeCalculator.ltvPct(600_000.0, 1_000_000.0)!!))
        assertEquals(true, PledgeCalculator.exceedsMaxLtv(PledgeCalculator.ltvPct(601_000.0, 1_000_000.0)!!))
    }
}
