package com.rboneone.stockrecord.domain

import com.rboneone.stockrecord.data.CashFlow
import com.rboneone.stockrecord.data.CashFlowType
import com.rboneone.stockrecord.data.Loan
import com.rboneone.stockrecord.data.LoanType
import com.rboneone.stockrecord.data.Market
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class LoanMathTest {
    private fun d(y: Int, m: Int, day: Int) = LocalDate.of(y, m, day).toEpochDay()

    private fun loan(
        principal: Double = 1_000_000.0, rate: Double = 2.0, periods: Int = 240, paid: Int = 0,
        payDay: Int = 5, start: Long = d(2026, 11, 8),
    ) = Loan(1, LoanType.HOUSING, principal, rate, periods, paid, payDay, start)

    @Test
    fun monthlyPayment_amortization() {
        assertEquals(5059, LoanMath.monthlyPayment(1_000_000.0, 2.0, 240))
        assertEquals(6580, LoanMath.monthlyPayment(500_000.0, 2.88, 84))
    }

    @Test
    fun monthlyPayment_zeroRateSplitsEvenly() {
        assertEquals(10_000, LoanMath.monthlyPayment(120_000.0, 0.0, 12))
    }

    @Test
    fun monthlyPayment_invalidInputIsZero() {
        assertEquals(0, LoanMath.monthlyPayment(0.0, 2.0, 12))
        assertEquals(0, LoanMath.monthlyPayment(100.0, 2.0, 0))
    }

    @Test
    fun firstDueDate_isAfterCreationDay() {
        // 11/8 建立、繳款日 5 號 → 12/5 才第一次扣；繳款日 15 號 → 11/15
        assertEquals(d(2026, 12, 5), LoanMath.dueDates(loan(payDay = 5)).first())
        assertEquals(d(2026, 11, 15), LoanMath.dueDates(loan(payDay = 15)).first())
        // 建立日剛好是繳款日：當天不扣，下個月才扣
        assertEquals(d(2026, 12, 8), LoanMath.dueDates(loan(payDay = 8)).first())
    }

    @Test
    fun day31_usesLastDayOfShortMonths() {
        val dues = LoanMath.dueDates(loan(payDay = 31, start = d(2027, 1, 10), periods = 3))
        assertEquals(listOf(d(2027, 1, 31), d(2027, 2, 28), d(2027, 3, 31)), dues)
    }

    @Test
    fun dueDates_stopAtTotalPeriods_andRespectPaidPeriods() {
        assertEquals(12, LoanMath.dueDates(loan(periods = 12)).size)
        assertEquals(2, LoanMath.dueDates(loan(periods = 12, paid = 10)).size)
        assertEquals(0, LoanMath.dueDates(loan(periods = 12, paid = 12)).size)
    }

    @Test
    fun paidPeriodsAndDeduction_accumulate() {
        val l = loan(principal = 120_000.0, rate = 0.0, periods = 12, paid = 3, payDay = 5)
        assertEquals(3, LoanMath.paidPeriods(l, d(2026, 12, 4)))      // 還沒到第一個繳款日
        assertEquals(4, LoanMath.paidPeriods(l, d(2026, 12, 5)))
        assertEquals(10_000, LoanMath.dueOn(l, d(2026, 12, 5)))
        assertEquals(0, LoanMath.dueOn(l, d(2026, 12, 6)))
        assertEquals(20_000, LoanMath.deductedUpTo(l, d(2027, 1, 20)))
        // 繳滿 12 期後不再增加
        assertEquals(12, LoanMath.paidPeriods(l, d(2040, 1, 1)))
        assertEquals(90_000, LoanMath.deductedUpTo(l, d(2040, 1, 1)))
        assertNull(LoanMath.nextDueDate(l, d(2040, 1, 1)))
    }

    @Test
    fun accounting_deductsLoanOnDueDateAndKeepsItAfter() {
        val l = loan(principal = 120_000.0, rate = 0.0, periods = 12, payDay = 5, start = d(2026, 11, 8))
        val flows = listOf(CashFlow(1, Market.TW, CashFlowType.DEPOSIT, d(2026, 11, 1), 100_000.0))
        fun at(date: Long) = Accounting.dayAccount(flows, emptyList(), emptyList(), null, date, loans = listOf(l))
        val before = at(d(2026, 12, 4))
        assertEquals(0, before.loanToday); assertEquals(0, before.loanTotal); assertEquals(100_000, before.available)
        val due = at(d(2026, 12, 5))
        assertEquals(10_000, due.loanToday); assertEquals(10_000, due.loanTotal); assertEquals(90_000, due.available)
        // 隔天：當日貸款金額歸零，但累計扣款還在
        val next = at(d(2026, 12, 6))
        assertEquals(0, next.loanToday); assertEquals(10_000, next.loanTotal); assertEquals(90_000, next.available)
        assertEquals(80_000, at(d(2027, 1, 5)).available)
    }
}
