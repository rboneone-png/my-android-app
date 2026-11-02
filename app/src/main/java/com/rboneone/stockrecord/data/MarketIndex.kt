package com.rboneone.stockrecord.data

/**
 * 可加入觀察的大盤指數。symbol 用 Yahoo 的指數代號（^ 開頭），存進觀察清單；
 * 即時報價依序用 Google 財經 → 證交所 → Yahoo。
 */
data class MarketIndex(
    val market: Market,
    /** Yahoo 指數代號，例如 ^TWII */
    val symbol: String,
    val name: String,
    /** Google 財經代號與交易所；Google 沒有時為 null */
    val google: Pair<String, String>? = null,
    /** 證交所即時報價的頻道，例如 otc_o00.tw */
    val misChannel: String? = null,
)

object MarketIndices {
    val ALL = listOf(
        MarketIndex(Market.TW, "^TWII", "台灣加權指數", google = "IX0001" to "TPE", misChannel = "tse_t00.tw"),
        MarketIndex(Market.US, "^GSPC", "S&P 500", google = ".INX" to "INDEXSP"),
        MarketIndex(Market.US, "^DJI", "道瓊工業指數", google = ".DJI" to "INDEXDJX"),
        MarketIndex(Market.US, "^IXIC", "那斯達克綜合指數", google = ".IXIC" to "INDEXNASDAQ"),
        MarketIndex(Market.US, "^NDX", "那斯達克 100 指數", google = "NDX" to "INDEXNASDAQ"),
        MarketIndex(Market.US, "^SOX", "費城半導體指數", google = "SOX" to "INDEXNASDAQ"),
    )

    fun find(market: Market, symbol: String): MarketIndex? =
        ALL.firstOrNull { it.market == market && it.symbol.equals(symbol, ignoreCase = true) }

    fun isIndex(symbol: String) = symbol.startsWith("^")
}
