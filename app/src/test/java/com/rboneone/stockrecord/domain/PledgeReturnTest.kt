package com.rboneone.stockrecord.domain

import com.rboneone.stockrecord.data.AssetCategory
import com.rboneone.stockrecord.data.CashFlow
import com.rboneone.stockrecord.data.CashFlowType
import com.rboneone.stockrecord.data.Market
import com.rboneone.stockrecord.data.Pledge
import com.rboneone.stockrecord.data.Quote
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PledgeReturnTest {
    private fun pledge(
        id: Long = 1, shares: Double = 1000.0, amount: Double = 100_000.0, rate: Double = 3.65, date: Long = 10, returnDate: Long? = null,
    ) = Pledge(id, "2330", "台積電", shares, amount, rate, date, "", returnDate)

    @Test
    fun interest_isAmountTimesRateTimesDaysOver365() {
        // 100,000 × 3.65% = 3,650 / 年 = 10 / 天
        val p = pledge()
        assertEquals(300, p.interestForDays(30))
        assertEquals(3_650, p.interestForDays(365))
        // 1,000,000 × 2.5% × 30 ÷ 365 = 2,054.79 → 2,055
        assertEquals(2_055, pledge(amount = 1_000_000.0, rate = 2.5).interestForDays(30))
    }

    @Test
    fun days_fromPledgeDateToReturnDate_neverNegative() {
        val p = pledge(date = 100)
        assertEquals(0, p.daysTo(100))       // 質押當天歸還
        assertEquals(1, p.daysTo(101))
        assertEquals(30, p.daysTo(130))
        assertEquals(0, p.daysTo(90))        // 早於質押日不會出現負數
    }

    @Test
    fun activeAndReturnedState() {
        assertEquals(true, pledge().isActive)
        assertNull(pledge().returnInterest)
        assertNull(pledge().returnDays)
        val r = pledge(returnDate = 40)
        assertEquals(false, r.isActive)
        assertEquals(30L, r.returnDays)
        assertEquals(300L, r.returnInterest)
    }

    @Test
    fun accounting_deductsPrincipalAndInterestOnReturnDate() {
        val flows = listOf(CashFlow(1, Market.TW, CashFlowType.DEPOSIT, 1, 500_000.0))
        val p = pledge(date = 10, returnDate = 40)    // 借 100,000，質押 30 天，利息 300
        fun at(d: Long) = Accounting.dayAccount(flows, emptyList(), listOf(p), null, d)

        // 質押當天：借進來的錢加到餘額
        assertEquals(600_000, at(10).available)
        assertEquals(0, at(10).pledgeReturnToday)
        // 歸還前一天：還在質押中
        assertEquals(600_000, at(39).available)
        // 歸還當天：扣掉本金與利息
        val ret = at(40)
        assertEquals(100_000, ret.pledgeReturnToday)
        assertEquals(300, ret.pledgeInterestToday)
        assertEquals(499_700, ret.available)
        // 隔天：當日歸還歸零，但累計扣款留在餘額裡
        val next = at(41)
        assertEquals(0, next.pledgeReturnToday); assertEquals(0, next.pledgeInterestToday)
        assertEquals(100_000, next.pledgeReturnTotal); assertEquals(300, next.pledgeInterestTotal)
        assertEquals(499_700, next.available)
    }

    @Test
    fun accounting_activePledgesDoNotDeductAnything() {
        val a = Accounting.dayAccount(emptyList(), emptyList(), listOf(pledge()), null, 50)
        assertEquals(0, a.pledgeReturnTotal); assertEquals(0, a.pledgeInterestTotal)
        assertEquals(100_000, a.available)
    }

    private val quotes = mapOf(
        (Market.TW to "2330") to Quote(Market.TW, "2330", 600.0, 600.0, 0, 0),
    )

    @Test
    fun build_excludesReturnedFromTotalsAndKeepsThemInReturnedList() {
        val active = pledge(id = 1, shares = 1000.0, amount = 100_000.0)
        val returned = pledge(id = 2, shares = 2000.0, amount = 300_000.0, date = 5, returnDate = 35)
        val v = PledgeCalculator.build(listOf(active, returned), quotes, portfolio = null)
        assertEquals(listOf(1L), v.items.map { it.pledge.id })
        assertEquals(100_000.0, v.totalAmount, 0.0)                 // 質借總金額不含已歸還
        assertEquals(600.0 * 1000 / 100_000 * 100, v.ratio!!, 1e-9) // 維持率只算質押中的
        assertEquals(listOf(2L), v.returned.map { it.id })
        assertEquals(30L * 300_000 * 3.65 / 100 / 365, v.returnedInterest.toDouble(), 0.5)
    }

    @Test
    fun candidates_getReturnedSharesBack() {
        val pos = Valuation.position(
            Holding(Market.TW, "2330", "台積電", 5_000.0, 50_000.0, 0.0), quotes[Market.TW to "2330"], "台積電", FeeSettings(),
        )
        val view = PortfolioView(listOf(pos), emptyList(), null)
        val cats = emptyMap<Pair<Market, String>, AssetCategory>()
        val pledging = pledge(shares = 3_000.0)
        assertEquals(2_000.0, PledgeCalculator.candidates(listOf(pledging), quotes, view, cats).single().shares, 0.0)
        // 歸還後，3,000 股回到可質押股數
        val back = pledging.copy(returnDate = 40)
        assertEquals(5_000.0, PledgeCalculator.candidates(listOf(back), quotes, view, cats).single().shares, 0.0)
    }
}
