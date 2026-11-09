package com.rboneone.stockrecord.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ClecTest {
    private fun view(core: Double, leveraged: Double, cashLike: Double, cash: Double, other: Double = 0.0) =
        AllocationView(core, leveraged, cashLike, cash, other, emptyList(), false)

    @Test
    fun betaIsFirstDigitPlusTwiceSecondDigit() {
        assertEquals(0.7, Clec.plan("703").beta, 1e-9)   // 0.7 + 2 × 0
        assertEquals(0.8, Clec.plan("613").beta, 1e-9)   // 0.6 + 2 × 0.1
        assertEquals(1.0, Clec.plan("433").beta, 1e-9)   // 0.4 + 2 × 0.3
        assertEquals(1.4, Clec.plan("073").beta, 1e-9)   // 0 + 2 × 0.7
    }

    @Test
    fun mixText_readsThreeDigitsAsTenths() {
        assertEquals("原型 70%／正二 0%／類現金 30%", Clec.plan("703").mixText)
        assertEquals("原型 0%／正二 70%／類現金 30%", Clec.plan("073").mixText)
    }

    @Test
    fun everyPlanSumsToOneHundredPercent() {
        Clec.PLANS.forEach { assertEquals(it.code, 1.0, it.corePct + it.leveragedPct + it.cashLikePct, 1e-9) }
    }

    @Test
    fun years_roundsSecondDecimalHalfUp() {
        assertEquals(25.0, Clec.years(2_495_000.0, 100_000.0)!!, 0.0)   // 24.95 → 25.0
        assertEquals(24.9, Clec.years(2_494_000.0, 100_000.0)!!, 0.0)   // 24.94 → 24.9
        assertEquals(0.1, Clec.years(5_000.0, 100_000.0)!!, 0.0)        // 0.05 → 0.1
    }

    @Test
    fun years_isNullWithoutLivingCost() {
        assertNull(Clec.years(1_000_000.0, 0.0))
        assertNull(Clec.years(1_000_000.0, -5.0))
    }

    @Test
    fun suggest_mapsEachRange() {
        assertEquals("703", Clec.suggest(50.1).code)
        assertEquals("613", Clec.suggest(50.0).code)
        assertEquals("613", Clec.suggest(40.0).code)
        assertEquals("523", Clec.suggest(39.9).code)
        assertEquals("523", Clec.suggest(30.0).code)
        assertEquals("433", Clec.suggest(29.9).code)
        assertEquals("433", Clec.suggest(20.0).code)
        assertEquals("343", Clec.suggest(19.9).code)
        assertEquals("343", Clec.suggest(16.0).code)
        assertEquals("253", Clec.suggest(15.9).code)
        assertEquals("253", Clec.suggest(13.0).code)
        assertEquals("163", Clec.suggest(12.9).code)
        assertEquals("163", Clec.suggest(10.0).code)
        assertEquals("073", Clec.suggest(9.9).code)
        assertEquals("073", Clec.suggest(0.0).code)
    }

    @Test
    fun compute_usesAllocationFigures() {
        // 原型 600 萬、正二 200 萬、類現金 100 萬、現金 50 萬、其他 80 萬（不計入金融資產）
        val v = view(6_000_000.0, 2_000_000.0, 1_000_000.0, 500_000.0, other = 800_000.0)
        val r = Clec.compute(v, 400_000.0, "433")
        assertEquals(6_000_000.0, r.indexEtf, 0.0)
        assertEquals(2_000_000.0, r.leveragedEtf, 0.0)
        assertEquals(1_500_000.0, r.cashLike, 0.0)               // 類現金 + 現金
        assertEquals(9_500_000.0, r.financialAssets, 0.0)        // F + G + H
        assertEquals(10_000_000.0, r.exposure, 0.0)              // 原型 + 2 × 正二
        assertEquals(23.8, r.years!!, 0.0)                       // 9,500,000 ÷ 400,000 = 23.75 → 23.8
        assertEquals("433", r.suggested!!.code)
        assertEquals(1.0, r.beta, 1e-9)
    }

    @Test
    fun compute_withoutLivingCostHasNoSuggestion() {
        val r = Clec.compute(view(1.0, 1.0, 1.0, 1.0), 0.0, "703")
        assertNull(r.years); assertNull(r.suggested)
    }

    @Test
    fun unknownMethodFallsBackToDefault() = assertEquals("433", Clec.plan("999").code)

    @Test
    fun overrides_replaceAutoValuesAndFeedDAndE() {
        val v = view(6_000_000.0, 2_000_000.0, 1_000_000.0, 500_000.0)
        val auto = Clec.compute(v, 400_000.0, "433")
        assertEquals(false, auto.indexEtfManual)
        assertEquals(9_500_000.0, auto.financialAssets, 0.0)
        assertEquals(10_000_000.0, auto.exposure, 0.0)

        // 只改 F（指數ETF）：D、E 跟著變，G、H 仍自動
        val f = Clec.compute(v, 400_000.0, "433", Clec.Overrides(indexEtf = 8_000_000.0))
        assertEquals(true, f.indexEtfManual); assertEquals(false, f.leveragedEtfManual); assertEquals(false, f.cashLikeManual)
        assertEquals(8_000_000.0, f.indexEtf, 0.0)
        assertEquals(2_000_000.0, f.leveragedEtf, 0.0)
        assertEquals(1_500_000.0, f.cashLike, 0.0)
        assertEquals(11_500_000.0, f.financialAssets, 0.0)
        assertEquals(12_000_000.0, f.exposure, 0.0)      // 8,000,000 + 2 × 2,000,000
        assertEquals(28.8, f.years!!, 0.0)               // 11,500,000 ÷ 400,000 = 28.75 → 28.8

        // 三項都手動，0 也是有效的手動值
        val all = Clec.compute(v, 400_000.0, "433", Clec.Overrides(1_000_000.0, 0.0, 2_000_000.0))
        assertEquals(3_000_000.0, all.financialAssets, 0.0)
        assertEquals(1_000_000.0, all.exposure, 0.0)
        assertEquals(true, all.leveragedEtfManual)
        assertEquals(7.5, all.years!!, 0.0)
        assertEquals("073", all.suggested!!.code)
    }
}
