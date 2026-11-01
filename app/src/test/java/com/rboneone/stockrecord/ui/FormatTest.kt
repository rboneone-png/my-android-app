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
}
