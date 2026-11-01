package com.rboneone.stockrecord.data

enum class Market(val label: String) {
    TW("台股"),
    US("美股"),
}

enum class TradeType(val label: String) {
    BUY("買進"),
    SELL("賣出"),
}

/** 台股證交稅類別，只在賣出時使用 */
enum class TaxType(val label: String, val rate: Double) {
    STOCK("一般股票 0.3%", 0.003),
    ETF("ETF 0.1%", 0.001),
    DAY_TRADE("當沖 0.15%", 0.0015),
}
