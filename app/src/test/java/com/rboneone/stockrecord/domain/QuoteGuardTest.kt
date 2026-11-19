package com.rboneone.stockrecord.domain

import com.rboneone.stockrecord.data.Market
import org.junit.Assert.assertEquals
import org.junit.Test

class QuoteGuardTest {
    @Test fun splitDay_previousCloseDividedByRatio() {
        // 1 分 5：前收還是 115.5，現價 23.1 → 前收 23.1
        assertEquals(23.1, QuoteGuard.previousClose(Market.TW, false, 23.2, 115.5), 1e-9)
    }

    @Test fun reverseSplit_previousCloseMultiplied() {
        assertEquals(50.0, QuoteGuard.previousClose(Market.TW, false, 51.0, 10.0), 1e-9)
    }

    @Test fun normalMoves_untouched() {
        assertEquals(100.0, QuoteGuard.previousClose(Market.TW, false, 90.0, 100.0), 0.0)
        assertEquals(100.0, QuoteGuard.previousClose(Market.TW, false, 110.0, 100.0), 0.0)
        // 差 2 倍但不是整數倍附近（2.3 倍）
        assertEquals(230.0, QuoteGuard.previousClose(Market.TW, false, 100.0, 230.0), 0.0)
        // 美股與指數不處理
        assertEquals(500.0, QuoteGuard.previousClose(Market.US, false, 100.0, 500.0), 0.0)
        assertEquals(500.0, QuoteGuard.previousClose(Market.TW, true, 100.0, 500.0), 0.0)
    }
}
