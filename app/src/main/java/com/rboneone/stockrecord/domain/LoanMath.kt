package com.rboneone.stockrecord.domain

import com.rboneone.stockrecord.data.Loan
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.pow

/**
 * 貸款計算：本息平均攤還，每月繳款固定（四捨五入到整數元）。
 *
 * 每月繳款 = 貸款金額 × 月利率 ÷ (1 − (1 + 月利率)^−期數)；月利率 = 年利率 ÷ 12；利率為 0 時 = 貸款金額 ÷ 期數
 */
object LoanMath {
    const val MAX_PERIODS = 600

    fun monthlyPayment(principal: Double, annualRatePct: Double, periods: Int): Long {
        if (principal <= 0 || periods <= 0) return 0
        val r = annualRatePct / 100 / 12
        val payment = if (r == 0.0) principal / periods else principal * r / (1 - (1 + r).pow(-periods))
        return Math.round(payment)
    }

    fun monthlyPayment(loan: Loan): Long = monthlyPayment(loan.principal, loan.annualRatePct, loan.totalPeriods)

    /** 建立日之後還沒繳的期數 */
    fun remainingPeriods(loan: Loan): Int = (loan.totalPeriods - loan.paidPeriods).coerceAtLeast(0)

    /** 自動扣款的日期（由近到遠排序）：建立日「之後」的每個繳款日，共 [remainingPeriods] 期 */
    fun dueDates(loan: Loan): List<Long> {
        val result = mutableListOf<Long>()
        var month = YearMonth.from(LocalDate.ofEpochDay(loan.startDate))
        val need = remainingPeriods(loan)
        while (result.size < need) {
            val day = minOf(loan.payDay.coerceIn(1, 31), month.lengthOfMonth())
            val date = month.atDay(day).toEpochDay()
            if (date > loan.startDate) result += date
            month = month.plusMonths(1)
        }
        return result
    }

    /** 截至 [date]（含）自動扣款的次數 */
    fun autoPaidCount(loan: Loan, date: Long): Int = dueDates(loan).count { it <= date }

    /** 截至 [date]（含）累計已繳期數 = 建立時已繳 + 自動扣款期數 */
    fun paidPeriods(loan: Loan, date: Long): Int = (loan.paidPeriods + autoPaidCount(loan, date)).coerceAtMost(loan.totalPeriods)

    /** 當天要扣的金額（不是繳款日為 0） */
    fun dueOn(loan: Loan, date: Long): Long = if (date in dueDates(loan)) monthlyPayment(loan) else 0L

    /** 截至 [date]（含）累計自動扣款的金額 */
    fun deductedUpTo(loan: Loan, date: Long): Long = autoPaidCount(loan, date) * monthlyPayment(loan)

    /** 下一個繳款日；已繳完回傳 null */
    fun nextDueDate(loan: Loan, date: Long): Long? = dueDates(loan).firstOrNull { it > date }
}
