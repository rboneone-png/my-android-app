package com.rboneone.stockrecord.network

import com.rboneone.stockrecord.data.Market
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GoogleFinanceTest {
    // 2026-11-02 09:24 台北時間實際回應
    private val tsmc = ")]}'\n\n[[\"wrb.fr\",\"gCvqoe\",\"[[[[\\\"/m/0cl2hvm\\\",[\\\"2330\\\",\\\"TPE\\\"],\\\"台積電\\\",0,\\\"TWD\\\"," +
        "[2335,-35,-1.4767933,2,2,2],null,2370,\\\"#e40313\\\",\\\"TW\\\",\\\"/m/02clqq\\\",[1793582660],\\\"Asia/Taipei\\\",28800]]]]\"," +
        "null,null,null,\"generic\"],[\"di\",102],[\"af.httprm\",102,\"1528498405156649612\",0]]"

    @Test
    fun parse_stock() {
        val q = GoogleFinance.parse(tsmc)!!
        assertEquals(2335.0, q.price, 0.0)
        assertEquals(2370.0, q.previousClose!!, 0.0)
        assertEquals(1793582660L, q.marketTime)
        assertEquals("台積電", q.name)
    }

    @Test
    fun parse_currency() {
        val body = ")]}'\n\n[[\"wrb.fr\",\"gCvqoe\",\"[[[[\\\"/g/11bvvyhtlb\\\",null,\\\"USD / TWD\\\",3,null," +
            "[32.625,0.0036,0.011,4,4,3],null,32.6214,null,null,null,[1793582640]]]]]\",null,null,null,\"generic\"]]"
        val q = GoogleFinance.parse(body)!!
        assertEquals(32.625, q.price, 0.0)
        assertEquals(32.6214, q.previousClose!!, 0.0)
    }

    @Test
    fun parse_notFound() {
        assertNull(GoogleFinance.parse(")]}'\n\n[[\"wrb.fr\",\"gCvqoe\",\"[[[null,[5]]]]\",null,null,null,\"generic\"]]"))
        assertNull(GoogleFinance.parse(")]}'\n\n[[\"wrb.fr\",\"gCvqoe\",null,null,null,[13],\"generic\"]]"))
        assertNull(GoogleFinance.parse("<html>error</html>"))
    }

    @Test
    fun usSymbolUsesDot() {
        assertEquals("BRK.B", GoogleFinance.googleSymbol(Market.US, "brk-b"))
        assertEquals("00631L", GoogleFinance.googleSymbol(Market.TW, "00631l"))
    }
}

class TwseMisTest {
    @Test
    fun parse_lastTrade() {
        val json = """{"msgArray":[{"c":"6488","n":"環球晶","z":"392.5000","y":"398.0000","tlong":"1793582700000",
            "b":"392.0000_391.5000_","a":"392.5000_393.0000_","o":"395.0000"}],"rtmessage":"OK"}"""
        val q = TwseMis.parse(json)!!
        assertEquals(392.5, q.price, 0.0)
        assertEquals(398.0, q.previousClose!!, 0.0)
        assertEquals(1793582700L, q.marketTime)
        assertEquals("環球晶", q.name)
    }

    @Test
    fun parse_noTradeYet_usesBidAskMid() {
        val json = """{"msgArray":[{"z":"-","y":"398.0000","tlong":"0","b":"392.0000_","a":"393.0000_"}]}"""
        assertEquals(392.5, TwseMis.parse(json)!!.price, 1e-9)
    }

    @Test
    fun parse_empty() {
        assertNull(TwseMis.parse("""{"msgArray":[],"rtmessage":"Empty Query."}"""))
        assertNull(TwseMis.parse("<html>因為安全性考量</html>"))
    }
}
