package com.rboneone.stockrecord.domain

import com.rboneone.stockrecord.data.AssetCategory
import com.rboneone.stockrecord.data.Market
import com.rboneone.stockrecord.data.Quote
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AllocationTest {
    private val settings = FeeSettings()

    private fun quote(market: Market, symbol: String, price: Double) = Quote(market, symbol, price, price, 0, 0)

    private val holdings = listOf(
        Holding(Market.TW, "0050", "元大台灣50", 1000.0, 90_000.0, 0.0),      // 原型 市值 100,000
        Holding(Market.TW, "00631L", "元大台灣50正2", 500.0, 40_000.0, 0.0),  // 正二 市值 50,000
        Holding(Market.TW, "00865B", "國泰US短期公債", 2000.0, 90_000.0, 0.0), // 類現金 市值 90,000
        Holding(Market.US, "VOO", "Vanguard S&P 500", 10.0, 5_000.0, 0.0),    // 原型 市值 6,000 美元
        Holding(Market.TW, "2330", "台積電", 100.0, 200_000.0, 0.0),          // 未分類
        Holding(Market.TW, "2884", "玉山金", 1000.0, 30_000.0, 0.0),          // 其他 市值 35,000
        Holding(Market.TW, "2412", "中華電", 0.0, 0.0, 1_000.0),              // 已出清，不列入
    )
    private val quotes = listOf(
        quote(Market.TW, "0050", 100.0),
        quote(Market.TW, "00631L", 100.0),
        quote(Market.TW, "00865B", 45.0),
        quote(Market.US, "VOO", 600.0),
        quote(Market.TW, "2330", 2400.0),
        quote(Market.TW, "2884", 35.0),
        quote(Market.US, Quote.USD_TWD, 32.0),
    ).associateBy { it.market to it.symbol }
    private val categories = mapOf(
        (Market.TW to "0050") to AssetCategory.CORE,
        (Market.TW to "00631L") to AssetCategory.LEVERAGED_2X,
        (Market.TW to "00865B") to AssetCategory.CASH_LIKE,
        (Market.US to "VOO") to AssetCategory.CORE,
        (Market.TW to "2884") to AssetCategory.OTHER,
    )

    @Test
    fun formulas() {
        val view = Valuation.build(holdings, quotes, emptyMap(), settings)
        val a = Allocation.build(view, categories, cash = 60_000.0)
        val core = 100_000.0 + 6_000 * 32 // 美股以匯率換算台幣
        assertEquals(core, a.core, 1e-6)
        assertEquals(50_000.0, a.leveraged, 1e-6)
        assertEquals(90_000.0, a.cashLike, 1e-6)
        assertEquals(60_000.0, a.cash, 0.0)
        assertEquals(35_000.0, a.other, 1e-6)
        assertEquals(core + 50_000 + 90_000 + 60_000 + 35_000, a.total, 1e-6) // 總資產 = A + B + C + D + E
        assertEquals(core + 2 * 50_000, a.exposure, 1e-6)                      // 曝險 = A + 2B，不含其他
        assertEquals((core + 100_000) / a.total * 100, a.exposurePct!!, 1e-9)
        assertEquals(listOf("2330"), a.uncategorized.map { it.position.holding.symbol })
        assertEquals(6, a.items.size)
        assertFalse(a.missingFx)
    }

    @Test
    fun cashOnly_startsAtZero() {
        val a = Allocation.build(Valuation.build(emptyList(), emptyMap(), emptyMap(), settings), emptyMap(), 0.0)
        assertEquals(0.0, a.total, 0.0)
        assertEquals(null, a.exposurePct)
    }

    @Test
    fun usWithoutFx_isExcludedAndFlagged() {
        val noFx = quotes - (Market.US to Quote.USD_TWD)
        val a = Allocation.build(Valuation.build(holdings, noFx, emptyMap(), settings), categories, 0.0)
        assertEquals(100_000.0, a.core, 1e-6)
        assertTrue(a.missingFx)
    }
}
