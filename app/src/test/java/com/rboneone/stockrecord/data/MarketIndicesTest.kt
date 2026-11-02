package com.rboneone.stockrecord.data

import com.rboneone.stockrecord.ui.screens.watchSubtitle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MarketIndicesTest {
    @Test
    fun find() {
        assertEquals("台灣加權指數", MarketIndices.find(Market.TW, "^TWII")!!.name)
        assertEquals("S&P 500", MarketIndices.find(Market.US, "^gspc")!!.name)
        assertNull(MarketIndices.find(Market.TW, "^GSPC"))   // 市場不同
        assertNull(MarketIndices.find(Market.TW, "2330"))
    }

    @Test
    fun subtitle() {
        assertEquals("指數・美股", watchSubtitle(Market.US, "^DJI"))
        assertEquals("2330・台股", watchSubtitle(Market.TW, "2330"))
    }

    @Test
    fun symbolsUniqueAndMarked() {
        assertEquals(MarketIndices.ALL.size, MarketIndices.ALL.map { it.symbol }.toSet().size)
        MarketIndices.ALL.forEach { assertTrue(MarketIndices.isIndex(it.symbol)) }
    }
}
