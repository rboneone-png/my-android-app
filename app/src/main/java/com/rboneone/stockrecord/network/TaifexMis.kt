package com.rboneone.stockrecord.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 臺灣期貨交易所即時行情（mis.taifex.com.tw），用於台指期近月。
 * 日盤（MarketType 0）與夜盤（MarketType 1）都查，取最新成交的那一個。
 */
object TaifexMis {
    private const val URL = "https://mis.taifex.com.tw/futures/api/getQuoteList"
    private val ZONE = ZoneId.of("Asia/Taipei")
    private val TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")

    // 單一個月份合約，例如 TXFK6-F（日盤）、TXFK6-M（夜盤）；價差單含「/」不算
    private val CONTRACT = Regex("^TXF[A-L]\\d-[FM]$")

    /** 台指期近月（近一）報價；現價為近月合約最新成交價，昨收為參考價（結算價） */
    suspend fun nearMonthTxf(): ChartQuote? = coroutineScope {
        val day = async { fetch("0") }
        val night = async { fetch("1") }
        parse(listOfNotNull(day.await(), night.await()))
    }

    private suspend fun fetch(marketType: String): String? = withContext(Dispatchers.IO) {
        val body = """{"MarketType":"$marketType","SymbolType":"F","KindID":"1","CID":"TXF","ExpireMonth":"","RowSize":"全部","PageNo":"","SortColumn":"","AscDesc":"A"}"""
        Http.postJson(URL, body)
    }

    /** 從日盤、夜盤的回應中，取各自第一個（最近月）合約，再選最後成交的那一個 */
    fun parse(responses: List<String>): ChartQuote? =
        responses.mapNotNull { parseOne(it) }.maxByOrNull { it.marketTime }

    private fun parseOne(json: String): ChartQuote? {
        val list = runCatching { JSONObject(json).getJSONObject("RtData").getJSONArray("QuoteList") }.getOrNull()
            ?: return null
        for (i in 0 until list.length()) {
            val q = list.getJSONObject(i)
            if (!CONTRACT.matches(q.optString("SymbolID"))) continue
            // 第一個單月合約就是近月；它還沒有成交時，這個盤別沒有資料
            val last = q.optString("CLastPrice").toDoubleOrNull()?.takeIf { it > 0 } ?: return null
            val ref = q.optString("CRefPrice").toDoubleOrNull()?.takeIf { it > 0 }
            val time = runCatching {
                LocalDateTime.parse(q.optString("CDate") + q.optString("CTime").padStart(6, '0'), TIME)
                    .atZone(ZONE).toEpochSecond()
            }.getOrDefault(0L)
            return ChartQuote(price = last, previousClose = ref, marketTime = time, name = "台指期近月")
        }
        return null
    }
}
