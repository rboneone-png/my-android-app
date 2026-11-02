package com.rboneone.stockrecord.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * 臺灣證券交易所「基本市況報導」即時報價（mis.twse.com.tw），上市（tse）與上櫃（otc）都有。
 * 用於 Google 財經沒有的上櫃股票。
 */
object TwseMis {
    private const val URL = "https://mis.twse.com.tw/stock/api/getStockInfo.jsp"

    /** board：上市 "tse"、上櫃 "otc" */
    suspend fun quote(symbol: String, board: String): ChartQuote? = withContext(Dispatchers.IO) {
        val url = "$URL?ex_ch=${board}_${symbol.lowercase()}.tw&json=1&delay=0"
        Http.get(url)?.let { parse(it) }
    }

    /**
     * msgArray[0]：z 最新成交價（剛好沒有成交時為 "-"）、y 昨收、tlong 時間（毫秒）、
     * b／a 五檔買賣價（以 _ 分隔）、o 開盤價、n 名稱
     */
    fun parse(json: String): ChartQuote? {
        val root = runCatching { JSONObject(json) }.getOrNull() ?: return null
        val a = root.optJSONArray("msgArray")?.optJSONObject(0) ?: return null
        fun num(key: String) = a.optString(key).toDoubleOrNull()?.takeIf { it > 0 }
        fun first(key: String) = a.optString(key).split('_').firstNotNullOfOrNull { it.toDoubleOrNull()?.takeIf { v -> v > 0 } }
        val bid = first("b")
        val ask = first("a")
        val price = num("z")
            ?: num("pz")
            ?: if (bid != null && ask != null) (bid + ask) / 2 else null
            ?: bid ?: ask ?: num("o")
            ?: return null
        return ChartQuote(
            price = price,
            previousClose = num("y"),
            marketTime = (a.optString("tlong").toLongOrNull() ?: 0L) / 1000,
            name = a.optString("n").takeIf { it.isNotBlank() },
        )
    }
}
