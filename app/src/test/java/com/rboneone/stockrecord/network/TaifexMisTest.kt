package com.rboneone.stockrecord.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class TaifexMisTest {
    private fun list(vararg q: String) = """{"RtCode":"0","RtData":{"QuoteList":[${q.joinToString(",")}]}}"""
    private fun q(id: String, last: String, ref: String, date: String, time: String) =
        """{"SymbolID":"$id","DispCName":"臺指期116","CLastPrice":"$last","CRefPrice":"$ref","CDate":"$date","CTime":"$time"}"""

    private val spot = q("TXF-S", "44920.14", "45211.83", "20261102", "133325")

    // 2026-11-02 實際回應：日盤收 45256，夜盤交易中 45081
    private val day = list(spot, q("TXFK6-F", "45256.00", "45300.00", "20261102", "134459"),
        q("TXFL6-F", "45344.00", "45420.00", "20261102", "134456"))
    private val night = list(q("TXF-P", "", "44920.14", "20261102", ""),
        q("TXFK6-M", "45081.00", "45227.00", "20261102", "213218"),
        q("TXFL6-M", "45189.00", "45335.00", "20261102", "212646"))

    private fun epoch(date: String, h: Int, m: Int, s: Int) =
        LocalDateTime.of(date.substring(0, 4).toInt(), date.substring(4, 6).toInt(), date.substring(6).toInt(), h, m, s)
            .atZone(ZoneId.of("Asia/Taipei")).toEpochSecond()

    @Test
    fun nightSessionNewerThanDay() {
        val r = TaifexMis.parse(listOf(day, night))!!
        assertEquals(45081.0, r.price, 0.0)
        assertEquals(45227.0, r.previousClose!!, 0.0)
        assertEquals(epoch("20261102", 21, 32, 18), r.marketTime)
        assertEquals("台指期近月", r.name)
    }

    @Test
    fun dayOnly_picksNearMonthNotSpotOrFarMonth() {
        val r = TaifexMis.parse(listOf(day))!!
        assertEquals(45256.0, r.price, 0.0)
        assertEquals(45300.0, r.previousClose!!, 0.0)
    }

    @Test
    fun nightNotTradedYet_fallsBackToDay() {
        val emptyNight = list(q("TXFK6-M", "", "45300.00", "20261103", ""))
        assertEquals(45256.0, TaifexMis.parse(listOf(day, emptyNight))!!.price, 0.0)
    }

    @Test
    fun spreadAndGarbageIgnored() {
        assertNull(TaifexMis.parse(listOf(list(spot, q("TXFK6/TXFL6-F", "10", "10", "20261102", "100000")))))
        assertNull(TaifexMis.parse(listOf("<html>error</html>", """{"RtCode":"1"}""")))
        assertNull(TaifexMis.parse(emptyList()))
    }
}
