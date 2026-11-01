package com.rboneone.stockrecord.alerts

import com.rboneone.stockrecord.data.AlertType
import com.rboneone.stockrecord.data.Market
import com.rboneone.stockrecord.data.PriceAlert
import com.rboneone.stockrecord.data.Quote
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class PriceAlertTest {
    private fun alert(type: AlertType, threshold: Double, market: Market = Market.TW) =
        PriceAlert(1, market, "2330", "台積電", type, threshold)

    @Test
    fun riseAmount_greaterOrEqual() {
        val a = alert(AlertType.RISE_AMOUNT, 50.0)
        assertTrue(a.isTriggered(2050.0, 2000.0))   // 剛好 +50
        assertTrue(a.isTriggered(2080.0, 2000.0))
        assertFalse(a.isTriggered(2049.0, 2000.0))
        assertFalse(a.isTriggered(1900.0, 2000.0))
    }

    @Test
    fun fallAmount_greaterOrEqual() {
        // 設定值輸入 50 或 -50 意思相同：下跌 50 元（含）以上
        for (t in listOf(50.0, -50.0)) {
            val a = alert(AlertType.FALL_AMOUNT, t)
            assertTrue(a.isTriggered(1950.0, 2000.0))  // 剛好跌 50
            assertTrue(a.isTriggered(1900.0, 2000.0))
            assertFalse(a.isTriggered(1951.0, 2000.0))
            assertFalse(a.isTriggered(2100.0, 2000.0))
        }
    }

    @Test
    fun risePct_greaterOrEqual() {
        val a = alert(AlertType.RISE_PCT, 3.0)
        assertTrue(a.isTriggered(103.0, 100.0))
        assertTrue(a.isTriggered(110.0, 100.0))
        assertFalse(a.isTriggered(102.99, 100.0))
    }

    @Test
    fun fallPct_greaterOrEqual() {
        for (t in listOf(3.0, -3.0)) {
            val a = alert(AlertType.FALL_PCT, t)
            assertTrue(a.isTriggered(97.0, 100.0))   // 剛好跌 3%
            assertTrue(a.isTriggered(90.0, 100.0))
            assertFalse(a.isTriggered(97.01, 100.0))
            assertFalse(a.isTriggered(105.0, 100.0))
        }
    }

    @Test
    fun priceAbove_greaterOrEqual() {
        val a = alert(AlertType.PRICE_ABOVE, 2500.0)
        assertTrue(a.isTriggered(2500.0, 2400.0))   // 剛好 2500
        assertTrue(a.isTriggered(2600.0, 2700.0))   // 今天下跌但仍高於 2500
        assertFalse(a.isTriggered(2499.0, 2400.0))
        assertTrue(a.isTriggered(2500.0, 0.0))      // 不需要昨日收盤
    }

    @Test
    fun priceBelow_lessOrEqual() {
        val a = alert(AlertType.PRICE_BELOW, 2000.0)
        assertTrue(a.isTriggered(2000.0, 2100.0))   // 剛好 2000
        assertTrue(a.isTriggered(1900.0, 1800.0))   // 今天上漲但仍低於 2000
        assertFalse(a.isTriggered(2001.0, 2100.0))
        assertFalse(a.isTriggered(0.0, 2100.0))     // 沒有報價不算
    }

    @Test
    fun noPreviousClose_neverTriggers() {
        assertFalse(alert(AlertType.RISE_PCT, 1.0).isTriggered(100.0, 0.0))
    }

    @Test
    fun conditionText() {
        assertEquals("上漲達 50 元", alert(AlertType.RISE_AMOUNT, 50.0).conditionText())
        assertEquals("下跌達 2.50 美元", alert(AlertType.FALL_AMOUNT, 2.5, Market.US).conditionText())
        assertEquals("上漲達 3%", alert(AlertType.RISE_PCT, 3.0).conditionText())
        assertEquals("下跌達 3%", alert(AlertType.FALL_PCT, -3.0).conditionText())
        assertEquals("上漲至 2500 元", alert(AlertType.PRICE_ABOVE, 2500.0).conditionText())
        assertEquals("下跌至 180.50 美元", alert(AlertType.PRICE_BELOW, 180.5, Market.US).conditionText())
    }

    @Test
    fun tradingDay_usesExchangeTimezone() {
        // 2026-10-29 16:30 紐約時間（收盤後）= 2026-10-30 04:30 台北時間；美股仍算 10/29
        val ts = 1793305800L
        assertEquals(LocalDate.of(2026, 10, 29).toEpochDay(), AlertChecker.tradingDay(Quote(Market.US, "AAPL", 1.0, 1.0, ts, 0)))
        assertEquals(LocalDate.of(2026, 10, 30).toEpochDay(), AlertChecker.tradingDay(Quote(Market.TW, "2330", 1.0, 1.0, ts, 0)))
    }
}
