package com.rboneone.stockrecord.domain

import com.rboneone.stockrecord.data.CashFlow
import com.rboneone.stockrecord.data.CashFlowType
import com.rboneone.stockrecord.data.Dividend
import com.rboneone.stockrecord.data.Loan
import com.rboneone.stockrecord.data.Market
import com.rboneone.stockrecord.data.Pledge
import com.rboneone.stockrecord.data.Trade
import com.rboneone.stockrecord.data.TradeType

/**
 * 帳務管理：單一台幣帳戶，截至某一天的帳務。所有金額都已四捨五入到整數（元）。
 *
 * 當日（今天）：
 * - B 當日賣出金額、C 當日買進金額：台股 + 美股（以匯率換成台幣）
 * - D 當日帳務 = B − C；當日質借金額：今天新增的質借金額
 * - 當日貸款金額：今天是貸款繳款日時，自動扣款的每月繳款總和
 * - 當日股利：今天入帳的現金股利（扣除代扣稅與匯費後的實際入帳金額）
 *
 * 累計（歷來到今天，隔天不會消失）：
 * - 帳務累計 = 歷來每一天（賣出 − 買進）的總和，今天的就是 D
 * - 質借金額累計 = 歷來質借金額的總和
 * - 股利累計 = 歷來入帳的現金股利總和
 * - 貸款累計 = 建立貸款之後，歷來每個繳款日自動扣款的金額總和
 * - 存入現金、領出現金：截至今天的累計
 *
 * A 當日可用餘額 = 存入現金 − 領出現金 + 帳務累計 + 質借金額累計 + 股利累計 − 貸款累計
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
    /** 當日質借金額 */
    val pledged: Long,
    /** 歷來（到今天）每一天賣出 − 買進的總和 */
    val accountTotal: Long,
    /** 歷來（到今天）質借金額的總和 */
    val pledgedTotal: Long,
    /** 當日入帳的現金股利 */
    val dividendsToday: Long,
    /** 歷來（到今天）入帳的現金股利總和 */
    val dividendsTotal: Long,
    /** 換算美股用的美元兌台幣匯率；沒有匯率時為 null */
    val usdTwd: Double?,
    /** 今天有美股買賣，但沒有匯率而無法換算（未計入） */
    val usMissingRate: Boolean,
    /** 當日貸款金額：今天自動扣款的貸款繳款 */
    val loanToday: Long = 0,
    /** 歷來（到今天）貸款自動扣款的總和 */
    val loanTotal: Long = 0,
) {
    /** D：當日帳務 = B − C */
    val dayNet: Long get() = sells - buys

    /** A：當日可用餘額 = 存入現金 − 領出現金 + 帳務累計 + 質借金額累計 + 股利累計 − 貸款累計 */
    val available: Long get() = deposits - withdrawals + accountTotal + pledgedTotal + dividendsTotal - loanTotal
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
        dividends: List<Dividend> = emptyList(),
        loans: List<Loan> = emptyList(),
    ): DayAccount {
        val flows = cashFlows.filter { it.date <= date }
        val upToToday = trades.filter { it.date <= date }
        val today = upToToday.filter { it.date == date }
        val rate = usdTwd?.takeIf { it > 0 }
        var usMissing = false

        /** 一筆買賣換成台幣金額（各自四捨五入）；美股沒有匯率時略過 */
        fun twd(t: Trade): Long = when (t.market) {
            Market.TW -> round(t.amount)
            Market.US -> if (rate != null) round(t.amount * rate) else { usMissing = true; 0L }
        }

        /** 一筆股利實際入帳的現金換成台幣（各自四捨五入） */
        fun twd(d: Dividend): Long = when (d.market) {
            Market.TW -> round(d.netCash)
            Market.US -> if (rate != null) round(d.netCash * rate) else { usMissing = true; 0L }
        }
        val dividendsUpToToday = dividends.filter { it.date <= date }.associateWith { twd(it) }

        // 每筆各自換算、四捨五入；今天與歷來用同一份換算結果，加總才會對得上
        val converted = upToToday.associateWith { twd(it) }
        fun sum(list: List<Trade>, type: TradeType) = list.filter { it.type == type }.sumOf { converted.getValue(it) }
        val sells = sum(today, TradeType.SELL)
        val buys = sum(today, TradeType.BUY)
        return DayAccount(
            deposits = round(flows.filter { it.type == CashFlowType.DEPOSIT }.sumOf { it.amount }),
            withdrawals = round(flows.filter { it.type == CashFlowType.WITHDRAW }.sumOf { it.amount }),
            sells = sells,
            buys = buys,
            pledged = round(pledges.filter { it.date == date }.sumOf { it.amount }),
            accountTotal = sum(upToToday, TradeType.SELL) - sum(upToToday, TradeType.BUY),
            pledgedTotal = round(pledges.filter { it.date <= date }.sumOf { it.amount }),
            dividendsToday = dividendsUpToToday.filterKeys { it.date == date }.values.sum(),
            dividendsTotal = dividendsUpToToday.values.sum(),
            usdTwd = rate,
            usMissingRate = usMissing,
            loanToday = loans.sumOf { LoanMath.dueOn(it, date) },
            loanTotal = loans.sumOf { LoanMath.deductedUpTo(it, date) },
        )
    }
}
