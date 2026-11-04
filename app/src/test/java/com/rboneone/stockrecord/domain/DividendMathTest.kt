package com.rboneone.stockrecord.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DividendMathTest {
    @Test
    fun cashTotal_isPerShareTimesHeld_withoutFloatNoise() {
        assertEquals(16_100.0, DividendMath.cashTotal(0.7, 23_000.0), 0.0)     // 0.7 × 23,000 在浮點數是 16099.999999999998
        assertEquals(25_000.0, DividendMath.cashTotal(0.5, 50_000.0), 0.0)
        assertEquals(0.0, DividendMath.cashTotal(0.5, 0.0), 0.0)
        assertEquals(0.0, DividendMath.cashTotal(0.0, 1_000.0), 0.0)
    }

    @Test
    fun cashTotal_roundsToCents() {
        assertEquals(431.9, DividendMath.cashTotal(0.35, 1_234.0), 1e-9)       // 431.9
        assertEquals(1.67, DividendMath.cashTotal(0.3333, 5.0), 1e-9)          // 1.6665 → 1.67
        assertEquals(52.05, DividendMath.cashTotal(0.5205, 100.0), 1e-9)
    }

    @Test
    fun perShareFrom_legacyRecord_isCloseButNotExact() {
        // 舊紀錄：現金股利 25,000，當天持有 33,333 股，沒有每股股利 → 約 0.750008
        val p = DividendMath.perShareFrom(25_000.0, 33_333.0)!!
        assertEquals(0.750008, p, 1e-9)
        // 乘回去會差幾分錢，所以編輯畫面在沒改動時要沿用原本的現金股利金額
        assertEquals(25_000.02, DividendMath.cashTotal(p, 33_333.0), 1e-9)
        // 剛好整除的就沒有誤差
        assertEquals(10_000.0, DividendMath.cashTotal(DividendMath.perShareFrom(10_000.0, 20_000.0)!!, 20_000.0), 0.0)
        assertNull(DividendMath.perShareFrom(25_000.0, 0.0))
        assertNull(DividendMath.perShareFrom(0.0, 1_000.0))
    }
}
