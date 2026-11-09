package com.rboneone.stockrecord.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class FormatTest {
    @Test
    fun wan() {
        assertEquals("+34.8萬", formatWan(348_000.0))
        assertEquals("-4.86萬", formatWan(-48_600.0))
        assertEquals("-133萬", formatWan(-1_330_000.0))
        assertEquals("+5,070", formatWan(5_070.0))
        assertEquals("0", formatWan(0.0))
    }

    @Test
    fun stepShares_addsOrSubtractsOneThousand() {
        assertEquals(1_000.0, stepShares(null, 1), 0.0)       // 空白視為 0
        assertEquals(2_000.0, stepShares(1_000.0, 1), 0.0)
        assertEquals(3_000.0, stepShares(2_000.0, 1), 0.0)    // 連按累加
        assertEquals(1_000.0, stepShares(2_000.0, -1), 0.0)
        assertEquals(1_234.0 + 1_000.0, stepShares(1_234.0, 1), 0.0)   // 非整張也是直接加 1000
        assertEquals(0.0, stepShares(1_000.0, -1), 0.0)
        assertEquals(0.0, stepShares(500.0, -1), 0.0)          // 不會變成負數
        assertEquals(0.0, stepShares(null, -1), 0.0)
        assertEquals(1_000.5 + 1_000.0, stepShares(1_000.5, 1), 1e-9)   // 美股零股
    }

    @Test
    fun roundHalfAway_roundsHalfUpForBothSigns() {
        assertEquals(3L, roundHalfAway(2.5)); assertEquals(2L, roundHalfAway(2.49))
        assertEquals(-3L, roundHalfAway(-2.5)); assertEquals(-2L, roundHalfAway(-2.49))
        assertEquals(0L, roundHalfAway(0.4)); assertEquals(0L, roundHalfAway(-0.4))
        // 67,000 股 × 2.66 = 178,220；67,001 股 × 0.125 = 8,375.125
        assertEquals(178_220L, roundHalfAway(67_000 * 2.66))
        assertEquals(8_375L, roundHalfAway(67_001 * 0.125))
    }

    @Test
    fun formatSignedWhole_addsPlusOnlyForPositive() {
        assertEquals("+178,220", formatSignedWhole(178_220))
        assertEquals("-1,234", formatSignedWhole(-1_234))
        assertEquals("0", formatSignedWhole(0))
    }
}
