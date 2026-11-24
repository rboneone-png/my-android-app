package com.rboneone.stockrecord.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IndicatorsTest {
    @Test fun sma_needsNValues() {
        val m = Indicators.sma(listOf(1.0, 2.0, 3.0, 4.0, 5.0), 3)
        assertNull(m[0]); assertNull(m[1])
        assertEquals(2.0, m[2]!!, 1e-9)
        assertEquals(3.0, m[3]!!, 1e-9)
        assertEquals(4.0, m[4]!!, 1e-9)
    }

    @Test fun rsi_extremesAndWarmup() {
        val up = (1..30).map { it.toDouble() }
        val r = Indicators.rsi(up, 14)
        assertNull(r[13]); assertEquals(100.0, r[14]!!, 1e-9); assertEquals(100.0, r[29]!!, 1e-9)
        val down = (30 downTo 1).map { it.toDouble() }
        assertEquals(0.0, Indicators.rsi(down, 14)[20]!!, 1e-9)
        assertEquals(50.0, Indicators.rsi(List(20) { 5.0 }, 14)[19]!!, 1e-9)
    }

    @Test fun rsi_mixedIsBetween() {
        val closes = listOf(10.0, 11.0, 10.5, 11.5, 11.0, 12.0, 11.5, 12.5, 12.0, 13.0, 12.5, 13.5, 13.0, 14.0, 13.5, 14.5)
        val v = Indicators.rsi(closes, 14).last()!!
        assert(v > 50 && v < 100)
    }

    @Test fun splitAdjusted_fixesUnadjustedHistory() {
        val c = listOf(
            Candle(1, 100.0, 105.0, 98.0, 100.0, 1_000), Candle(2, 100.0, 104.0, 97.0, 102.0, 1_100),
            Candle(3, 20.5, 21.0, 19.8, 20.4, 5_200), Candle(4, 20.4, 21.2, 20.0, 21.0, 5_000),
        )
        val a = Indicators.splitAdjusted(c)
        assertEquals(20.0, a[0].close, 1e-9)
        assertEquals(21.0, a[0].high, 1e-9)
        assertEquals(5_000L, a[0].volume)
        assertEquals(c[2], a[2])
    }
}
