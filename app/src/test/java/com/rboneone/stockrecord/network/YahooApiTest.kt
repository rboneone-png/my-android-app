package com.rboneone.stockrecord.network

import com.rboneone.stockrecord.data.Market
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class YahooApiTest {
    @Test
    fun parseChart_readsMeta() {
        val json = """
            {"chart":{"result":[{"meta":{"currency":"TWD","symbol":"0050.TW","regularMarketTime":1793332638,
            "regularMarketPrice":104.95,"longName":"Yuanta/P-shares Taiwan Top 50 ETF",
            "shortName":"YUANTA SECURITIES INV TRUST CO ","chartPreviousClose":107.25}}],"error":null}}
        """.trimIndent()
        val q = YahooApi.parseChart(json)!!
        assertEquals(104.95, q.price, 0.0)
        assertEquals(107.25, q.previousClose!!, 0.0)
        assertEquals(1793332638L, q.marketTime)
        assertEquals("Yuanta/P-shares Taiwan Top 50 ETF", q.name)
    }

    @Test
    fun parseChart_unknownSymbol() {
        val json = """{"chart":{"result":null,"error":{"code":"Not Found","description":"No data found"}}}"""
        assertNull(YahooApi.parseChart(json))
    }

    @Test
    fun parseSearch_picksMatchingTaiwanSymbol() {
        val json = """
            {"quotes":[
              {"exchange":"TWO","shortname":"GLOBALWAFERS","symbol":"6488.TWO","longname":"環球晶"},
              {"exchange":"HKG","shortname":"X","symbol":"64880.HK","longname":"BI_HSI"},
              {"exchange":"JPX","symbol":"6488.T","longname":"Yoshitake Inc."}
            ]}
        """.trimIndent()
        val hit = YahooApi.parseSearch(json, YahooApi.candidates(Market.TW, "6488"))!!
        assertEquals("6488.TWO", hit.yahooSymbol)
        assertEquals("環球晶", hit.name)
    }

    @Test
    fun parseSearch_fallsBackToShortName() {
        val json = """{"quotes":[{"symbol":"BRK-B","shortname":"Berkshire Hathaway Inc. New","longname":null}]}"""
        val hit = YahooApi.parseSearch(json, YahooApi.candidates(Market.US, "BRK.B"))!!
        assertEquals("BRK-B", hit.yahooSymbol)
        assertEquals("Berkshire Hathaway Inc. New", hit.name)
    }

    @Test
    fun parseSearch_noMatch() {
        val json = """{"quotes":[{"symbol":"2330.HK","longname":"中國上城"}]}"""
        assertNull(YahooApi.parseSearch(json, YahooApi.candidates(Market.TW, "9999")))
    }
}

class YahooHistoryTest {
    @Test
    fun parseHistory_usesExchangeTimezoneAndSkipsNulls() {
        // 1793235600 = 2026-10-29 09:00 台北（UTC+8），UTC 仍是 10/29 01:00
        val json = """
            {"chart":{"result":[{"meta":{"gmtoffset":28800},
            "timestamp":[1793235600,1793322000,1793332638],
            "indicators":{"quote":[{"close":[2400.0,null,2375.0],"volume":[100,null,200]}]}}],"error":null}}
        """.trimIndent()
        val bars = YahooApi.parseHistory(json)!!
        org.junit.Assert.assertEquals(2, bars.size)
        org.junit.Assert.assertEquals(java.time.LocalDate.of(2026, 10, 29).toEpochDay(), bars[0].date)
        org.junit.Assert.assertEquals(java.time.LocalDate.of(2026, 10, 30).toEpochDay(), bars[1].date)
        org.junit.Assert.assertEquals(2375.0, bars[1].close, 0.0)
    }

    @Test
    fun parseHistory_skipsZeroVolumeHoliday() {
        val json = """
            {"chart":{"result":[{"meta":{"gmtoffset":28800},
            "timestamp":[1792716000,1792802400,1792888800],
            "indicators":{"quote":[{"close":[2420.0,2385.0,2385.0],"volume":[22321809,22930116,0]}]}}]}}
        """.trimIndent()
        org.junit.Assert.assertEquals(2, YahooApi.parseHistory(json)!!.size)
    }

    @Test
    fun parseHistory_fillsMissingLatestCloseFromMarketPrice() {
        // 實際案例：0056 在 10/30 的收盤價為 null，但 regularMarketPrice 有值
        val json = """
            {"chart":{"result":[{"meta":{"gmtoffset":28800,"regularMarketPrice":55.85,"regularMarketTime":1793341808},
            "timestamp":[1793235600,1793322000],
            "indicators":{"quote":[{"close":[56.9,null],"volume":[24222969,83802079]}]}}]}}
        """.trimIndent()
        val bars = YahooApi.parseHistory(json)!!
        org.junit.Assert.assertEquals(2, bars.size)
        org.junit.Assert.assertEquals(java.time.LocalDate.of(2026, 10, 30).toEpochDay(), bars[1].date)
        org.junit.Assert.assertEquals(55.85, bars[1].close, 0.0)
    }

    @Test
    fun parseHistory_keepsExistingCloseOverMarketPrice() {
        val json = """
            {"chart":{"result":[{"meta":{"gmtoffset":28800,"regularMarketPrice":2371.0,"regularMarketTime":1793341808},
            "timestamp":[1793322000],
            "indicators":{"quote":[{"close":[2370.0],"volume":[29664858]}]}}]}}
        """.trimIndent()
        org.junit.Assert.assertEquals(2370.0, YahooApi.parseHistory(json)!!.single().close, 0.0)
    }
}
