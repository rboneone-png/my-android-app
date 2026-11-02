package com.rboneone.stockrecord.data

import com.rboneone.stockrecord.domain.Bar
import com.rboneone.stockrecord.domain.PeriodHigh
import com.rboneone.stockrecord.network.ChartQuote
import com.rboneone.stockrecord.network.GoogleFinance
import com.rboneone.stockrecord.network.TwseMis
import com.rboneone.stockrecord.network.YahooApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

sealed interface LookupResult {
    data class Found(val info: StockInfo) : LookupResult
    data object NotFound : LookupResult
    data object NetworkError : LookupResult
}

/** 更新報價的結果：失敗的股票代號，以及是否完全連不上網路 */
data class RefreshResult(val failed: List<String>, val offline: Boolean)

class MarketRepository(private val dao: MarketDao) {
    fun observeQuotes(): Flow<List<Quote>> = dao.observeQuotes()
    fun observeInfo(): Flow<List<StockInfo>> = dao.observeInfo()

    suspend fun lookup(market: Market, symbol: String): LookupResult {
        dao.getInfo(market, symbol)?.let { return LookupResult.Found(it) }
        return try {
            resolve(market, symbol)?.let { LookupResult.Found(it) } ?: LookupResult.NotFound
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            LookupResult.NetworkError
        } catch (e: Exception) {
            // Yahoo 回傳格式異常時當作查不到
            LookupResult.NotFound
        }
    }

    private suspend fun resolve(market: Market, symbol: String): StockInfo? {
        val now = System.currentTimeMillis()
        val hit = YahooApi.search(market, symbol)
        val info = if (hit != null) {
            StockInfo(market, symbol, hit.name ?: symbol, hit.yahooSymbol, now)
        } else {
            // 搜尋不到時直接試抓報價，確認代號是否存在
            YahooApi.candidates(market, symbol).firstNotNullOfOrNull { ys ->
                YahooApi.chart(ys)?.let { StockInfo(market, symbol, it.name ?: symbol, ys, now) }
            }
        } ?: return null
        dao.upsertInfo(info)
        return info
    }

    suspend fun refreshQuotes(stocks: List<Pair<Market, String>>): RefreshResult = coroutineScope {
        val jobs = stocks.map { (market, symbol) ->
            async { symbol to runCatching { refreshOne(market, symbol) } }
        }
        val fx = async { runCatching { refreshFx() } }
        val results = jobs.awaitAll()
        val fxResult = fx.await()
        val all = results.map { it.second } + fxResult
        RefreshResult(
            failed = results.filter { it.second.getOrDefault(false).not() }.map { it.first } +
                (if (fxResult.getOrDefault(false)) emptyList() else listOf("美元匯率")),
            offline = all.isNotEmpty() && all.all { it.exceptionOrNull() is IOException },
        )
    }

    /** 美股代號 → Google 財經的交易所（找到一次就記住） */
    private val usExchange = ConcurrentHashMap<String, String>()

    /**
     * 即時報價來源：
     * - 台股上市：Google 財經（TPE），失敗時用證交所即時報價
     * - 台股上櫃：證交所即時報價（Google 財經沒有上櫃股票）
     * - 美股：Google 財經
     * 都失敗時回傳 null，改用 Yahoo（台股約延遲 20 分鐘）
     */
    private suspend fun realtimeQuote(market: Market, symbol: String, info: StockInfo): ChartQuote? = try {
        val g = GoogleFinance.googleSymbol(market, symbol)
        when {
            market == Market.TW && info.yahooSymbol.endsWith(".TWO", ignoreCase = true) ->
                TwseMis.quote(symbol, "otc")
            market == Market.TW ->
                GoogleFinance.stock(g, GoogleFinance.TW_EXCHANGE) ?: TwseMis.quote(symbol, "tse")
            else -> {
                val known = usExchange[g]
                known?.let { GoogleFinance.stock(g, it) }
                    ?: GoogleFinance.US_EXCHANGES.filter { it != known }.firstNotNullOfOrNull { ex ->
                        GoogleFinance.stock(g, ex)?.also { usExchange[g] = ex }
                    }
            }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

    private suspend fun refreshOne(market: Market, symbol: String): Boolean {
        val info = (lookup(market, symbol) as? LookupResult.Found)?.info ?: return false
        val chart = realtimeQuote(market, symbol, info) ?: YahooApi.chart(info.yahooSymbol) ?: return false
        dao.upsertQuote(
            Quote(
                market = market,
                symbol = symbol,
                price = chart.price,
                previousClose = chart.previousClose ?: chart.price,
                marketTime = chart.marketTime,
                updatedAt = System.currentTimeMillis(),
            )
        )
        return true
    }

    private val historyCache = mutableMapOf<String, List<Bar>>()

    /** 抓日線收盤價；已經結束的期間會快取起來，不重複下載 */
    suspend fun history(market: Market, symbol: String, from: Long, to: Long, cacheable: Boolean): List<Bar>? {
        val key = "$market/$symbol/$from/$to"
        if (cacheable) synchronized(historyCache) { historyCache[key] }?.let { return it }
        val info = (lookup(market, symbol) as? LookupResult.Found)?.info ?: return null
        val bars = YahooApi.history(info.yahooSymbol, from, to) ?: return null
        if (cacheable) synchronized(historyCache) { historyCache[key] = bars }
        return bars
    }

    /** 近 years 年的最高價；查不到或連不上回傳 null */
    suspend fun periodHigh(market: Market, symbol: String, years: Int): PeriodHigh? {
        val info = (lookup(market, symbol) as? LookupResult.Found)?.info ?: return null
        return try {
            YahooApi.periodHigh(info.yahooSymbol, years)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun refreshFx(): Boolean {
        val google = try {
            GoogleFinance.currency("USD", "TWD")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
        val chart = google ?: YahooApi.chart(Quote.USD_TWD) ?: return false
        dao.upsertQuote(
            Quote(
                market = Market.US,
                symbol = Quote.USD_TWD,
                price = chart.price,
                previousClose = chart.previousClose ?: chart.price,
                marketTime = chart.marketTime,
                updatedAt = System.currentTimeMillis(),
            )
        )
        return true
    }
}
