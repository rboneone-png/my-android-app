package com.rboneone.stockrecord.network

import org.junit.Assert.assertEquals
import org.junit.Test

class YahooCandlesTest {
    @Test fun parseCandles_skipsHolidaysAndNulls() {
        val json = """
            {"chart":{"result":[{"meta":{"gmtoffset":28800},
            "timestamp":[1792716000,1792802400,1792888800,1793235600],
            "indicators":{"quote":[{"open":[10.0,11.0,12.0,null],"high":[11.0,12.0,13.0,14.0],
            "low":[9.0,10.0,11.0,12.0],"close":[10.5,11.5,12.5,13.5],"volume":[100,200,0,300]}]}}]}}
        """.trimIndent()
        val c = YahooApi.parseCandles(json)!!
        assertEquals(2, c.size)
        assertEquals(11.0, c[0].high, 0.0)
        assertEquals(200L, c[1].volume)
    }
}
