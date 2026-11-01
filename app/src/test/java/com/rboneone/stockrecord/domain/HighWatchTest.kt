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
}
