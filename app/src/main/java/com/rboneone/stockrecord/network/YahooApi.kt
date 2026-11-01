package com.rboneone.stockrecord.network

import com.rboneone.stockrecord.data.Market
import com.rboneone.stockrecord.domain.Bar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URLEncoder

data class ChartQuote(
    val price: Double,
    val previousClose: Double?,
    /** epoch 秒 */
    val marketTime: Long,
    val name: String?,
)

data class SearchHit(val yahooSymbol: String, val name: String?)

/** Yahoo Finance 非官方 API：搜尋股票名稱、抓報價與匯率 */
object YahooApi {
    private const val CHART_URL = "https://query1.finance.yahoo.com/v8/finance/chart/"
    private const val SEARCH_URL = "https://query1.finance.yahoo.com/v1/finance/search"

    /** 台股不知道是上市（.TW）還是上櫃（.TWO），兩個都試 */
    fun candidates(market: Market, symbol: String): List<String> = when (market) {
        Market.TW -> listOf("$symbol.TW", "$symbol.TWO")
        Market.US -> listOf(symbol.replace('.', '-'))
    }

    suspend fun chart(yahooSymbol: String): ChartQuote? = withContext(Dispatchers.IO) {
        val url = CHART_URL + URLEncoder.encode(yahooSymbol, "UTF-8") + "?range=1d&interval=1d"
        Http.get(url)?.let { parseChart(it) }
    }

    /** 日線收盤價，from / to 為 LocalDate.toEpochDay() */
    suspend fun history(yahooSymbol: String, from: Long, to: Long): List<Bar>? = withContext(Dispatchers.IO) {
        val period1 = (from - 1) * 86_400
        val period2 = (to + 2) * 86_400
        val url = CHART_URL + URLEncoder.encode(yahooSymbol, "UTF-8") +
            "?period1=$period1&period2=$period2&interval=1d"
        Http.get(url)?.let { parseHistory(it) }
    }

    suspend fun search(market: Market, symbol: String): SearchHit? = withContext(Dispatchers.IO) {
        // 台股用繁體中文查詢才會回傳中文名稱
        val locale = when (market) {
            Market.TW -> "lang=zh-Hant-TW&region=TW"
            Market.US -> "lang=en-US&region=US"
        }
        val candidates = candidates(market, symbol)
        // 用 Yahoo 的代號格式搜尋，例如美股 BRK.B 要查 BRK-B
        val query = if (market == Market.US) candidates.first() else symbol
        val url = "$SEARCH_URL?q=${URLEncoder.encode(query, "UTF-8")}&$locale&quotesCount=10&newsCount=0"
        Http.get(url)?.let { parseSearch(it, candidates) }
    }

    fun parseChart(json: String): ChartQuote? {
        val result = JSONObject(json).optJSONObject("chart")
            ?.optJSONArray("result")
            ?.optJSONObject(0) ?: return null
        val meta = result.optJSONObject("meta") ?: return null
        if (meta.isNull("regularMarketPrice")) return null
        return ChartQuote(
            price = meta.getDouble("regularMarketPrice"),
            previousClose = meta.optDouble("chartPreviousClose").takeUnless { it.isNaN() },
            marketTime = meta.optLong("regularMarketTime"),
            name = meta.str("longName") ?: meta.str("shortName"),
        )
    }

    fun parseHistory(json: String): List<Bar>? {
        val result = JSONObject(json).optJSONObject("chart")
            ?.optJSONArray("result")
            ?.optJSONObject(0) ?: return null
        // 用交易所當地時區換算日期
        val offset = result.optJSONObject("meta")?.optLong("gmtoffset") ?: 0L
        val timestamps = result.optJSONArray("timestamp") ?: return emptyList()
        val quote = result.optJSONObject("indicators")
            ?.optJSONArray("quote")
            ?.optJSONObject(0) ?: return emptyList()
        val closes = quote.optJSONArray("close") ?: return emptyList()
        val volumes = quote.optJSONArray("volume")
        val bars = mutableListOf<Bar>()
        for (i in 0 until minOf(timestamps.length(), closes.length())) {
            if (closes.isNull(i)) continue
            // Yahoo 會在休市日補一筆成交量 0 的資料，不算交易日
            if (volumes != null && i < volumes.length() && !volumes.isNull(i) && volumes.getLong(i) == 0L) continue
            val date = Math.floorDiv(timestamps.getLong(i) + offset, 86_400L)
            bars += Bar(date, closes.getDouble(i))
        }
        // 同一天若有多筆（盤中即時資料），保留最後一筆
        val byDate = bars.associateBy { it.date }.toMutableMap()
        // Yahoo 有時最新一天的收盤價是 null（例如 ETF 收盤後），改用最新成交價補上
        val meta = result.optJSONObject("meta")
        if (meta != null && !meta.isNull("regularMarketPrice") && meta.optLong("regularMarketTime") > 0) {
            val lastDate = Math.floorDiv(meta.getLong("regularMarketTime") + offset, 86_400L)
            val inRange = timestamps.length() > 0 &&
                lastDate >= Math.floorDiv(timestamps.getLong(0) + offset, 86_400L)
            if (inRange && lastDate !in byDate) {
                byDate[lastDate] = Bar(lastDate, meta.getDouble("regularMarketPrice"))
            }
        }
        return byDate.values.sortedBy { it.date }
    }

    fun parseSearch(json: String, candidates: List<String>): SearchHit? {
        val quotes = JSONObject(json).optJSONArray("quotes") ?: return null
        for (i in 0 until quotes.length()) {
            val q = quotes.optJSONObject(i) ?: continue
            val symbol = q.str("symbol") ?: continue
            if (candidates.any { it.equals(symbol, ignoreCase = true) }) {
                return SearchHit(symbol, q.str("longname") ?: q.str("shortname"))
            }
        }
        return null
    }

    private fun JSONObject.str(key: String): String? =
        if (isNull(key)) null else optString(key).trim().takeIf { it.isNotEmpty() }
}
