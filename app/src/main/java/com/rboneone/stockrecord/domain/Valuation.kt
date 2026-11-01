package com.rboneone.stockrecord.domain

import com.rboneone.stockrecord.data.Market
import com.rboneone.stockrecord.data.Quote
import com.rboneone.stockrecord.data.TaxType

/** 單一持股加上即時報價後的狀態 */
data class Position(
    val holding: Holding,
    val displayName: String,
    val quote: Quote?,
    /** 沒有報價時為 null */
    val marketValue: Double?,
    /** 市值扣除預估賣出手續費、交易稅後，再減去成本 */
    val unrealizedPnl: Double?,
) {
    /** 總損益：未實現損益加上持有期間領到的股息（預扣賣出費用） */
    val totalPnl: Double?
        get() = unrealizedPnl?.let { it + holding.dividendsSinceOpen }
    val totalPnlPct: Double?
        get() = totalPnl?.takeIf { holding.totalCost > 0 }?.let { it / holding.totalCost * 100 }

    val dayChange: Double?
        get() = quote?.let { it.price - it.previousClose }
    val dayChangePct: Double?
        get() = quote?.takeIf { it.previousClose > 0 }?.let { (it.price - it.previousClose) / it.previousClose * 100 }

    /** 今日損益：股數 × 今日漲跌 */
    val dayPnl: Double?
        get() = dayChange?.takeIf { holding.isOpen }?.let { it * holding.shares }
}

data class MarketSummary(
    val market: Market,
    val cost: Double,
    /** 沒有報價的持股以成本計入 */
    val marketValue: Double,
    val unrealizedPnl: Double,
    val realizedPnl: Double,
    val dividendIncome: Double,
    val dayPnl: Double,
    /** 庫存股總損益合計 */
    val totalPnl: Double,
    val missingQuotes: Int,
) {
    /** 累積損益：未實現 + 已實現 + 股息收入 */
    val cumulativePnl: Double get() = unrealizedPnl + realizedPnl + dividendIncome
    val cumulativePct: Double? get() = if (cost > 0) cumulativePnl / cost * 100 else null
}

data class PortfolioView(
    val positions: List<Position>,
    val summaries: List<MarketSummary>,
    /** 美元兌台幣匯率，美股換算台幣用 */
    val usdTwd: Quote?,
) {
    fun summary(market: Market): MarketSummary? = summaries.firstOrNull { it.market == market }
}

/** 依使用者自訂順序排列；不在清單中的（例如新買的）放在最後，依代號排序 */
fun List<Position>.inCustomOrder(order: List<String>): List<Position> {
    val index = order.withIndex().associate { it.value to it.index }
    return sortedWith(compareBy({ index[it.holding.symbol] ?: Int.MAX_VALUE }, { it.holding.symbol }))
}

object Valuation {
    /** 台股 00 開頭為 ETF，賣出適用 0.1% 證交稅 */
    fun defaultTaxType(market: Market, symbol: String): TaxType =
        if (market == Market.TW && symbol.startsWith("00")) TaxType.ETF else TaxType.STOCK

    fun estimatedSellCost(market: Market, symbol: String, marketValue: Double, settings: FeeSettings): Double =
        when (market) {
            Market.TW -> FeeCalculator.twFee(marketValue, settings) +
                FeeCalculator.twTax(marketValue, defaultTaxType(market, symbol))
            Market.US -> settings.usCommission
        }

    fun position(holding: Holding, quote: Quote?, name: String?, settings: FeeSettings): Position {
        val displayName = if (holding.name != holding.symbol) holding.name else name ?: holding.symbol
        if (!holding.isOpen || quote == null) {
            return Position(holding, displayName, quote, marketValue = null, unrealizedPnl = null)
        }
        val mv = quote.price * holding.shares
        val sellCost = estimatedSellCost(holding.market, holding.symbol, mv, settings)
        return Position(holding, displayName, quote, mv, mv - sellCost - holding.totalCost)
    }

    fun build(
        holdings: List<Holding>,
        quotes: Map<Pair<Market, String>, Quote>,
        names: Map<Pair<Market, String>, String>,
        settings: FeeSettings,
    ): PortfolioView {
        val positions = holdings.map {
            val key = it.market to it.symbol
            position(it, quotes[key], names[key], settings)
        }
        val summaries = Market.entries.mapNotNull { market ->
            val inMarket = positions.filter { it.holding.market == market }
            if (inMarket.isEmpty()) return@mapNotNull null
            val open = inMarket.filter { it.holding.isOpen }
            MarketSummary(
                market = market,
                cost = open.sumOf { it.holding.totalCost },
                marketValue = open.sumOf { it.marketValue ?: it.holding.totalCost },
                unrealizedPnl = open.sumOf { it.unrealizedPnl ?: 0.0 },
                realizedPnl = inMarket.sumOf { it.holding.realizedPnl },
                dividendIncome = inMarket.sumOf { it.holding.dividendIncome },
                dayPnl = open.sumOf { it.dayPnl ?: 0.0 },
                totalPnl = open.sumOf { it.totalPnl ?: 0.0 },
                missingQuotes = open.count { it.quote == null },
            )
        }
        return PortfolioView(positions, summaries, quotes[Market.US to Quote.USD_TWD])
    }
}
