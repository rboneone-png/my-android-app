package com.rboneone.stockrecord.network

import com.rboneone.stockrecord.data.Market
import com.rboneone.stockrecord.domain.Bar
import com.rboneone.stockrecord.domain.PeriodHigh
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
        val json = Http.get(url) ?: return@withContext null
        val bars = parseHistory(json) ?: return@withContext null
        // Yahoo 的日線偶爾整天都是 null（ETF 常見，例如 0050 在 2026/11/3），但盤中資料還在：用當天最後一筆補上
        val have = bars.map { it.date }.toSet()
        val gaps = parseHistoryGaps(json).filter { it !in have }
        if (gaps.isEmpty()) return@withContext bars
        val today = java.time.LocalDate.now().toEpochDay()
        val usable = gaps.filter { today - it <= INTRADAY_DAYS }
        if (usable.isEmpty()) return@withContext bars
        val intraUrl = CHART_URL + URLEncoder.encode(yahooSymbol, "UTF-8") +
            "?period1=${(usable.min() - 1) * 86_400}&period2=${(usable.max() + 2) * 86_400}&interval=5m"
        val closes = Http.get(intraUrl)?.let { parseIntradayCloses(it) }.orEmpty()
        val filled = usable.mapNotNull { d -> closes[d]?.let { Bar(d, it) } }
        (bars + filled).sortedBy { it.date }
    }

    /** 5 分鐘線最多只能查近 60 天 */
    private const val INTRADAY_DAYS = 55L

    /** 日線中「有時間點但收盤價是 null」的日期（Yahoo 漏資料的日子，不含成交量 0 的休市補列） */
    fun parseHistoryGaps(json: String): List<Long> {
        val result = JSONObject(json).optJSONObject("chart")?.optJSONArray("result")?.optJSONObject(0) ?: return emptyList()
        val offset = result.optJSONObject("meta")?.optLong("gmtoffset") ?: 0L
        val timestamps = result.optJSONArray("timestamp") ?: return emptyList()
        val closes = result.optJSONObject("indicators")?.optJSONArray("quote")?.optJSONObject(0)?.optJSONArray("close")
            ?: return emptyList()
        return (0 until minOf(timestamps.length(), closes.length()))
            .filter { closes.isNull(it) }
            .map { Math.floorDiv(timestamps.getLong(it) + offset, 86_400L) }
            .distinct()
    }

    /** 盤中資料每天最後一筆有效價格（當作該日收盤價） */
    fun parseIntradayCloses(json: String): Map<Long, Double> {
        val result = JSONObject(json).optJSONObject("chart")?.optJSONArray("result")?.optJSONObject(0) ?: return emptyMap()
        val offset = result.optJSONObject("meta")?.optLong("gmtoffset") ?: 0L
        val timestamps = result.optJSONArray("timestamp") ?: return emptyMap()
        val closes = result.optJSONObject("indicators")?.optJSONArray("quote")?.optJSONObject(0)?.optJSONArray("close")
            ?: return emptyMap()
        val out = sortedMapOf<Long, Double>()
        val order = (0 until minOf(timestamps.length(), closes.length())).sortedBy { timestamps.getLong(it) }
        for (i in order) {
            if (closes.isNull(i)) continue
            out[Math.floorDiv(timestamps.getLong(i) + offset, 86_400L)] = closes.getDouble(i)
        }
        return out
    }

    /** 近 years 年的最高價（日線最高價） */
    suspend fun periodHigh(yahooSymbol: String, years: Int): PeriodHigh? = withContext(Dispatchers.IO) {
        val url = CHART_URL + URLEncoder.encode(yahooSymbol, "UTF-8") + "?range=${years}y&interval=1d"
        Http.get(url)?.let { parseHigh(it) }
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

    fun parseHigh(json: String): PeriodHigh? {
        val result = JSONObject(json).optJSONObject("chart")
            ?.optJSONArray("result")
            ?.optJSONObject(0) ?: return null
        val meta = result.optJSONObject("meta")
        val offset = meta?.optLong("gmtoffset") ?: 0L
        var best: PeriodHigh? = null
        val timestamps = result.optJSONArray("timestamp")
        val quote = result.optJSONObject("indicators")?.optJSONArray("quote")?.optJSONObject(0)
        val highs = quote?.optJSONArray("high")
        val volumes = quote?.optJSONArray("volume")
        if (timestamps != null && highs != null) {
            for (i in 0 until minOf(timestamps.length(), highs.length())) {
                if (highs.isNull(i)) continue
                // 休市日補的資料（成交量 0）不算
                if (volumes != null && i < volumes.length() && !volumes.isNull(i) && volumes.getLong(i) == 0L) continue
                val high = highs.getDouble(i)
                if (high.isNaN() || high <= 0) continue
                if (best == null || high > best.price) {
                    best = PeriodHigh(high, Math.floorDiv(timestamps.getLong(i) + offset, 86_400L))
                }
            }
        }
        // 今天的最高價可能還沒進日線
        if (meta != null && !meta.isNull("regularMarketDayHigh") && meta.optLong("regularMarketTime") > 0) {
            val dayHigh = meta.optDouble("regularMarketDayHigh")
            if (!dayHigh.isNaN() && dayHigh > 0 && (best == null || dayHigh > best.price)) {
                best = PeriodHigh(dayHigh, Math.floorDiv(meta.getLong("regularMarketTime") + offset, 86_400L))
            }
        }
        return best
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
