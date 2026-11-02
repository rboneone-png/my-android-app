package com.rboneone.stockrecord.network

import com.rboneone.stockrecord.data.Market
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject.NULL

/**
 * Google 財經即時報價（google.com/finance 網頁本身使用的資料介面，非官方 API）。
 * 台股上市（TPE）與美股為即時報價；Google 財經沒有台股上櫃股票。
 */
object GoogleFinance {
    private const val URL =
        "https://www.google.com/finance/_/GoogleFinanceUi/data/batchexecute?rpcids=gCvqoe&hl=zh-TW"

    /** 美股依序嘗試的交易所，找到後由 MarketRepository 記住 */
    val US_EXCHANGES = listOf("NASDAQ", "NYSE", "NYSEARCA", "NYSEAMERICAN", "BATS")

    /** 台股上市 = TPE */
    const val TW_EXCHANGE = "TPE"

    /** Google 的美股代號用「.」，例如 BRK.B */
    fun googleSymbol(market: Market, symbol: String): String = when (market) {
        Market.TW -> symbol.uppercase()
        Market.US -> symbol.uppercase().replace('-', '.')
    }

    /** 股票報價，參數格式 [[[null,["2330","TPE"]]],1] */
    suspend fun stock(symbol: String, exchange: String): ChartQuote? =
        request(args(JSONArray().put(NULL).put(JSONArray().put(symbol).put(exchange))))

    /** 匯率，例如 currency("USD", "TWD") */
    suspend fun currency(from: String, to: String): ChartQuote? =
        request(args(JSONArray().put(NULL).put(NULL).put(JSONArray().put(from).put(to))))

    private fun args(id: JSONArray): String = JSONArray().put(JSONArray().put(id)).put(1).toString()

    private suspend fun request(args: String): ChartQuote? = withContext(Dispatchers.IO) {
        val fReq = JSONArray().put(
            JSONArray().put(JSONArray().put("gCvqoe").put(args).put(NULL).put("generic"))
        ).toString()
        Http.postForm(URL, mapOf("f.req" to fReq))?.let { parse(it) }
    }

    /**
     * 回應格式：開頭為 )]}' 防護字串，之後一行 JSON：
     * [["wrb.fr","gCvqoe","<內層 JSON 字串>",...],...]
     * 內層：[[[[id,[代號,交易所],名稱,類型,幣別,[現價,漲跌,漲跌%,...],null,昨收,顏色,國家,id,[時間戳記],...]]]]
     * 查不到時內層為 [[[null,[5]]]]，參數錯誤時內層為 null。
     */
    fun parse(body: String): ChartQuote? {
        val line = body.lineSequence().firstOrNull { it.startsWith("[[") } ?: return null
        val outer = JSONArray(line)
        for (i in 0 until outer.length()) {
            val entry = outer.optJSONArray(i) ?: continue
            if (entry.optString(0) != "wrb.fr" || entry.isNull(2)) continue
            val inner = JSONArray(entry.getString(2))
            val q = inner.optJSONArray(0)?.optJSONArray(0)?.optJSONArray(0) ?: return null
            val prices = q.optJSONArray(5) ?: return null
            val price = prices.optDouble(0)
            if (price.isNaN() || price <= 0) return null
            val prev = q.optDouble(7).takeUnless { it.isNaN() || it <= 0 }
            val time = q.optJSONArray(11)?.optLong(0) ?: 0L
            return ChartQuote(
                price = price,
                previousClose = prev,
                marketTime = time,
                name = q.optString(2).takeIf { it.isNotBlank() && !q.isNull(2) },
            )
        }
        return null
    }
}
