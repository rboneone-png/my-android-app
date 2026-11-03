package com.rboneone.stockrecord.domain

import com.rboneone.stockrecord.data.CashFlow
import com.rboneone.stockrecord.data.CashFlowType
import com.rboneone.stockrecord.data.Market
import com.rboneone.stockrecord.data.Trade
import com.rboneone.stockrecord.data.TradeType

/**
 * 帳務管理：某一天的帳務。
 * 當日帳務 D = 當日賣出金額 B − 當日買進金額 C；
 * 當日可用餘額 A = 存入現金 − 領出現金 + D（存入、領出為截至當天的累計）。
 */
data class DayAccount(
    /** 截至當天累計存入的現金 */
    val deposits: Double,
    /** 截至當天累計領出的現金 */
    val withdrawals: Double,
    /** B：當日所有賣出股票的金額總和（股數 × 成交價） */
    val sells: Double,
    /** C：當日所有買進股票的金額總和（股數 × 成交價） */
    val buys: Double,
) {
    /** D：當日帳務 = B − C */
    val dayNet: Double get() = sells - buys

    /** A：當日可用餘額 = 存入現金 − 領出現金 + D */
    val available: Double get() = deposits - withdrawals + dayNet
}

object Accounting {
    fun dayAccount(cashFlows: List<CashFlow>, trades: List<Trade>, market: Market, date: Long): DayAccount {
        val flows = cashFlows.filter { it.market == market && it.date <= date }
        val today = trades.filter { it.market == market && it.date == date }
        return DayAccount(
            deposits = flows.filter { it.type == CashFlowType.DEPOSIT }.sumOf { it.amount },
            withdrawals = flows.filter { it.type == CashFlowType.WITHDRAW }.sumOf { it.amount },
            sells = today.filter { it.type == TradeType.SELL }.sumOf { it.amount },
            buys = today.filter { it.type == TradeType.BUY }.sumOf { it.amount },
        )
    }
}
