package com.rboneone.stockrecord.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FxRatesTest {
    private val bars = listOf(Bar(100, 31.0), Bar(101, 31.2), Bar(104, 31.5))   // 102、103 是週末

    @Test fun exactDay() = assertEquals(31.2, FxRates.rateOn(bars, 101)!!, 0.0)

    @Test fun weekendUsesPreviousTradingDay() {
        assertEquals(31.2, FxRates.rateOn(bars, 102)!!, 0.0)
        assertEquals(31.2, FxRates.rateOn(bars, 103)!!, 0.0)
    }

    @Test fun afterLastBarUsesLastBar() = assertEquals(31.5, FxRates.rateOn(bars, 105)!!, 0.0)

    @Test fun slightlyBeforeFirstBarUsesFirstBar() = assertEquals(31.0, FxRates.rateOn(bars, 98)!!, 0.0)

    @Test fun farBeforeFirstBarIsNull() = assertNull(FxRates.rateOn(bars, 100 - FxRates.LOOKBACK_DAYS - 1))

    @Test fun staleDataIsNull() {
        // 最近一筆離查詢日太久（超過回看範圍），不拿來湊數
        assertNull(FxRates.rateOn(bars, 104 + FxRates.LOOKBACK_DAYS * 3 + 1))
    }

    @Test fun emptyBarsIsNull() = assertNull(FxRates.rateOn(emptyList(), 100))

    @Test fun unsortedInputIsHandled() = assertEquals(31.2, FxRates.rateOn(bars.reversed(), 102)!!, 0.0)
}
