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
}
