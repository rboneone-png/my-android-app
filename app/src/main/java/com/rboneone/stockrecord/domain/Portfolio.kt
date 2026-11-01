package com.rboneone.stockrecord.domain

import com.rboneone.stockrecord.data.Dividend
import com.rboneone.stockrecord.data.Market
import com.rboneone.stockrecord.data.Trade
import com.rboneone.stockrecord.data.TradeType

data class Holding(
    val market: Market,
    val symbol: String,
    val name: String,
    val shares: Double,
    /** 目前持股的總成本（含買進手續費） */
    val totalCost: Double,
    /** 已實現損益（扣除手續費與交易稅） */
    val realizedPnl: Double,
    /** 這次持有期間（上次出清之後）領到的現金股利，用來計算成本均 */
    val dividendsSinceOpen: Double = 0.0,
    /** 這檔股票歷來所有現金股利（實際入帳） */
    val dividendIncome: Double = 0.0,
) {
    /** 買均：每股買進成本（含手續費） */
    val avgCost: Double get() = if (shares > 0) totalCost / shares else 0.0
    /** 成本均：扣除持有期間股息後的每股成本 */
    val adjustedAvgCost: Double get() = if (shares > 0) (totalCost - dividendsSinceOpen) / shares else 0.0
    val isOpen: Boolean get() = shares > 0
}

/** 以平均成本法計算持股與已實現損益 */
object Portfolio {
    private const val EPSILON = 1e-9

    private sealed interface Event {
        val date: Long
        val order: Int
        val id: Long
    }

    private data class TradeEvent(val trade: Trade) : Event {
        override val date get() = trade.date
        override val order get() = 1
        override val id get() = trade.id
    }

    // 同一天先算股利再算買賣：發放日當天出清，股利仍屬於這次持有
    private data class DividendEvent(val dividend: Dividend) : Event {
        override val date get() = dividend.date
        override val order get() = 0
        override val id get() = dividend.id
    }

    fun build(trades: List<Trade>, dividends: List<Dividend> = emptyList()): List<Holding> {
        val keys = (trades.map { it.market to it.symbol } + dividends.map { it.market to it.symbol }).distinct()
        return keys.map { (market, symbol) ->
            buildOne(
                market,
                symbol,
                trades.filter { it.market == market && it.symbol == symbol },
                dividends.filter { it.market == market && it.symbol == symbol },
            )
        }.sortedWith(compareBy({ it.market }, { it.symbol }))
    }

    /** 列出每一筆賣出實現的損益（平均成本法），依日期排序 */
    fun realizedEvents(trades: List<Trade>, dividends: List<Dividend> = emptyList()): List<RealizedEvent> {
        val events = mutableListOf<RealizedEvent>()
        val keys = trades.map { it.market to it.symbol }.distinct()
        for ((market, symbol) in keys) {
            buildOne(
                market,
                symbol,
                trades.filter { it.market == market && it.symbol == symbol },
                dividends.filter { it.market == market && it.symbol == symbol },
                events,
            )
        }
        return events.sortedWith(compareBy({ it.date }, { it.symbol }))
    }

    private fun buildOne(
        market: Market,
        symbol: String,
        trades: List<Trade>,
        dividends: List<Dividend>,
        realizedOut: MutableList<RealizedEvent>? = null,
    ): Holding {
        var shares = 0.0
        var cost = 0.0
        var realized = 0.0
        var dividendsSinceOpen = 0.0
        var dividendIncome = 0.0
        val events = (trades.map { TradeEvent(it) } + dividends.map { DividendEvent(it) })
            .sortedWith(compareBy({ it.date }, { it.order }, { it.id }))
        for (e in events) {
            when (e) {
                is DividendEvent -> {
                    val d = e.dividend
                    dividendIncome += d.netCash
                    if (shares > EPSILON) dividendsSinceOpen += d.netCash
                    // 股票股利：股數增加、成本不變
                    shares += d.stockShares
                }
                is TradeEvent -> {
                    val t = e.trade
                    when (t.type) {
                        TradeType.BUY -> {
                            shares += t.shares
                            cost += t.amount + t.fee
                        }
                        TradeType.SELL -> {
                            val avg = if (shares > EPSILON) cost / shares else 0.0
                            val costOut = avg * t.shares
                            val pnl = t.amount - t.fee - t.tax - costOut
                            realized += pnl
                            realizedOut?.add(RealizedEvent(t.date, market, symbol, t.name, t.shares, t.price, costOut, pnl))
                            shares -= t.shares
                            cost -= costOut
                            if (shares < EPSILON) {
                                shares = 0.0
                                cost = 0.0
                                dividendsSinceOpen = 0.0
                            }
                        }
                    }
                }
            }
        }
        val name = (trades.map { it.date to it.name } + dividends.map { it.date to it.name })
            .sortedBy { it.first }
            .lastOrNull { it.second.isNotBlank() }?.second ?: symbol
        return Holding(market, symbol, name, shares, cost, realized, dividendsSinceOpen, dividendIncome)
    }

    /** 計算某檔股票目前持有股數（含配股），可排除正在編輯的那筆交易 */
    fun sharesHeld(
        trades: List<Trade>,
        market: Market,
        symbol: String,
        excludeId: Long = 0,
        dividends: List<Dividend> = emptyList(),
    ): Double =
        trades.filter { it.market == market && it.symbol == symbol && it.id != excludeId }
            .sumOf { if (it.type == TradeType.BUY) it.shares else -it.shares } +
            dividends.filter { it.market == market && it.symbol == symbol }.sumOf { it.stockShares }

    /** 某一天收盤後持有的股數 */
    fun sharesOn(trades: List<Trade>, dividends: List<Dividend>, date: Long): Double =
        trades.filter { it.date <= date }.sumOf { if (it.type == TradeType.BUY) it.shares else -it.shares } +
            dividends.filter { it.date <= date }.sumOf { it.stockShares }
}

/** 一筆賣出的已實現損益 */
data class RealizedEvent(
    val date: Long,
    val market: Market,
    val symbol: String,
    val name: String,
    val shares: Double,
    val price: Double,
    /** 賣出部分的成本（平均成本 × 股數） */
    val cost: Double,
    /** 已實現損益（扣除手續費與交易稅） */
    val pnl: Double,
)
