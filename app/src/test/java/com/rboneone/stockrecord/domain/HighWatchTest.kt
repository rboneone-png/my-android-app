package com.rboneone.stockrecord.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HighWatchTest {
    @Test
    fun ratio_isPriceOverHigh() {
        assertEquals(80.0, HighWatch.ratio(160.0, 200.0)!!, 1e-9)
        assertNull(HighWatch.ratio(null, 200.0))
        assertNull(HighWatch.ratio(160.0, null))
        assertNull(HighWatch.ratio(160.0, 0.0))
    }

    @Test
    fun effectiveHigh_usesPriceWhenNewHigh() {
        assertEquals(200.0, HighWatch.effectiveHigh(200.0, 150.0)!!, 0.0)
        assertEquals(210.0, HighWatch.effectiveHigh(200.0, 210.0)!!, 0.0)
        assertEquals(200.0, HighWatch.effectiveHigh(200.0, null)!!, 0.0)
        // 還沒抓到最高價時不用現價代替
        assertNull(HighWatch.effectiveHigh(null, 150.0))
        assertEquals(100.0, HighWatch.ratio(210.0, HighWatch.effectiveHigh(200.0, 210.0))!!, 1e-9)
    }

    @Test fun dropLevelsAreHighTimesOneMinusDrop() {
        val levels = HighWatch.dropLevels(200.0)
        assertEquals(listOf("P1", "P2", "P3", "P4", "P5"), levels.map { it.label })
        assertEquals(listOf(10, 20, 30, 40, 50), levels.map { it.dropPct })
        assertEquals(listOf(180.0, 160.0, 140.0, 120.0, 100.0), levels.map { it.price })
    }

    @Test fun dropLevelsUseEffectiveHighWhenPriceMakesNewHigh() {
        // 現價 250 創新高，技術點以 250 計
        val levels = HighWatch.dropLevels(HighWatch.effectiveHigh(200.0, 250.0))
        assertEquals(225.0, levels.first().price, 1e-9)
        assertEquals(125.0, levels.last().price, 1e-9)
    }

    @Test fun dropLevelsEmptyWithoutHigh() {
        assertEquals(emptyList<HighWatch.DropLevel>(), HighWatch.dropLevels(null))
        assertEquals(emptyList<HighWatch.DropLevel>(), HighWatch.dropLevels(0.0))
    }

    @Test fun splitAdjustedHigh_dividesUnadjustedBars() {
        // 1:5 分割，Yahoo 只調整了 11/9 之後；之前的最高價 120 還是分割前的價格
        val bars = listOf(
            HighBar(1, 118.0, 115.0, 1_000), HighBar(2, 120.0, 118.0, 1_200), HighBar(3, 119.0, 116.0, 900),
            HighBar(4, 24.0, 23.0, 5_000), HighBar(5, 24.4, 23.5, 5_200),
        )
        val h = HighWatch.splitAdjustedHigh(bars)!!
        assertEquals(24.4, h.price, 1e-9)
        assertEquals(5L, h.date)
    }

    @Test fun splitAdjustedHigh_keepsRealCrashAndAdjustedData() {
        // 一天跌 50% 但成交量沒有跟著放大成倍數 → 不是分割
        val crash = listOf(HighBar(1, 100.0, 98.0, 1_000), HighBar(2, 50.0, 49.0, 1_100))
        assertEquals(100.0, HighWatch.splitAdjustedHigh(crash)!!.price, 1e-9)
        // 整份都已調整：沒有斷層
        val ok = listOf(HighBar(1, 24.0, 23.0, 5_000), HighBar(2, 25.0, 24.0, 5_100))
        assertEquals(25.0, HighWatch.splitAdjustedHigh(ok)!!.price, 1e-9)
        assertNull(HighWatch.splitAdjustedHigh(emptyList()))
    }

    @Test fun splitAdjustedHigh_multipleAndReverse() {
        // 兩次分割（2 倍、5 倍）：最早的價格要 ÷ 10
        val two = listOf(
            HighBar(1, 500.0, 490.0, 100), HighBar(2, 250.0, 245.0, 200), HighBar(3, 50.0, 49.0, 1_000),
        )
        assertEquals(50.0, HighWatch.splitAdjustedHigh(two)!!.price, 1e-9)
        // 反向分割 5:1，之前的價格 × 5
        val rev = listOf(HighBar(1, 10.0, 9.8, 5_000), HighBar(2, 50.0, 49.0, 1_000))
        assertEquals(50.0, HighWatch.splitAdjustedHigh(rev)!!.price, 1e-9)
    }
}
