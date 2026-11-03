package com.rboneone.stockrecord.domain

import com.rboneone.stockrecord.data.CashFlow
import com.rboneone.stockrecord.data.CashFlowType
import com.rboneone.stockrecord.data.Market
import com.rboneone.stockrecord.data.Pledge
import com.rboneone.stockrecord.data.Trade
import com.rboneone.stockrecord.data.TradeType

/**
 * 帳務管理：單一台幣帳戶，某一天的帳務。所有金額都已四捨五入到整數（元）。
 *
 * - B 當日賣出金額、C 當日買進金額：台股 + 美股（以當日匯率換成台幣）
 * - D 當日帳務 = B − C
 * - 當日質押金額：今天新增的質押金額
 * - A 當日可用餘額 = 存入現金 − 領出現金 + D + 當日質押金額（存入、領出為截至當天的累計）
 */
data class DayAccount(
    /** 截至當天累計存入的現金 */
    val deposits: Long,
    /** 截至當天累計領出的現金 */
    val withdrawals: Long,
    /** B：當日所有賣出股票的金額總和 */
    val sells: Long,
    /** C：當日所有買進股票的金額總和 */
    val buys: Long,
    /** 當日質押金額 */
    val pledged: Long,
    /** 換算美股用的美元兌台幣匯率；沒有匯率時為 null */
    val usdTwd: Double?,
    /** 今天有美股買賣，但沒有匯率而無法換算（未計入） */
    val usMissingRate: Boolean,
) {
    /** D：當日帳務 = B − C */
    val dayNet: Long get() = sells - buys

    /** A：當日可用餘額 = 存入現金 − 領出現金 + D + 當日質押金額 */
    val available: Long get() = deposits - withdrawals + dayNet + pledged
}

object Accounting {
    /** 四捨五入到整數 */
    fun round(value: Double): Long = Math.round(value)

    fun dayAccount(
        cashFlows: List<CashFlow>,
        trades: List<Trade>,
        pledges: List<Pledge>,
        usdTwd: Double?,
        date: Long,
    ): DayAccount {
        val flows = cashFlows.filter { it.date <= date }
        val today = trades.filter { it.date == date }
        val rate = usdTwd?.takeIf { it > 0 }
        var usMissing = false

        /** 一筆買賣換成台幣金額（各自四捨五入）；美股沒有匯率時略過 */
        fun twd(t: Trade): Long = when (t.market) {
            Market.TW -> round(t.amount)
            Market.US -> if (rate != null) round(t.amount * rate) else { usMissing = true; 0L }
        }

        val sells = today.filter { it.type == TradeType.SELL }.sumOf { twd(it) }
        val buys = today.filter { it.type == TradeType.BUY }.sumOf { twd(it) }
        return DayAccount(
            deposits = round(flows.filter { it.type == CashFlowType.DEPOSIT }.sumOf { it.amount }),
            withdrawals = round(flows.filter { it.type == CashFlowType.WITHDRAW }.sumOf { it.amount }),
            sells = sells,
            buys = buys,
            pledged = round(pledges.filter { it.date == date }.sumOf { it.amount }),
            usdTwd = rate,
            usMissingRate = usMissing,
        )
    }
}
