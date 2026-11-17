package com.rboneone.stockrecord.domain

import com.rboneone.stockrecord.data.AlertType
import com.rboneone.stockrecord.data.Dividend
import com.rboneone.stockrecord.data.Market
import com.rboneone.stockrecord.data.Pledge
import com.rboneone.stockrecord.data.PriceAlert
import com.rboneone.stockrecord.data.StockSplit
import com.rboneone.stockrecord.data.Trade
import com.rboneone.stockrecord.data.TradeType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SplitMathTest {
    // 暫停 100、恢復 110、1 分 5
    private val split = StockSplit(1, Market.TW, "0050", "元大台灣50", 100, 110, 5)

    private fun trade(date: Long, shares: Double, price: Double, symbol: String = "0050", market: Market = Market.TW) =
        Trade(1, market, symbol, symbol, TradeType.BUY, date, shares, price, 20.0, 0.0)

    @Test
    fun validate_suspendMustBeBeforeResume() {
        assertNull(SplitMath.validate(100, 110, 5))
        assertEquals("設定錯誤：暫停交易日必須早於恢復交易日", SplitMath.validate(110, 110, 5))
        assertEquals("設定錯誤：暫停交易日必須早於恢復交易日", SplitMath.validate(120, 110, 5))
    }

    @Test
    fun validate_ratioMustBeIntegerAtLeastTwo() {
        listOf(null, 0, 1, -3, 1001).forEach { assertEquals("$it", true, SplitMath.validate(100, 110, it) != null) }
        assertNull(SplitMath.validate(100, 110, 2))
        assertNull(SplitMath.validate(100, 110, 1000))
    }

    @Test
    fun isDue_onResumeDateAndAfter_onlyOnce() {
        assertEquals(false, SplitMath.isDue(split, 109))
        assertEquals(true, SplitMath.isDue(split, 110))
        assertEquals(true, SplitMath.isDue(split, 200))
        assertEquals(false, SplitMath.isDue(split.copy(applied = true), 200))
    }

    @Test
    fun trade_sharesTimesRatio_priceDividedAndAmountUnchanged() {
        val t = trade(50, 2000.0, 150.0)
        val n = SplitMath.adjustTrade(t, split, forward = true)
        assertEquals(10_000.0, n.shares, 0.0)
        assertEquals(30.0, n.price, 1e-12)
        assertEquals(t.amount, n.amount, 1e-6)       // 300,000 不變
        assertEquals(t.fee, n.fee, 0.0)
    }

    @Test
    fun trade_onOrAfterResumeDateIsAlreadyPostSplit() {
        val onResume = trade(110, 1000.0, 30.0)
        val after = trade(120, 1000.0, 31.0)
        assertEquals(onResume, SplitMath.adjustTrade(onResume, split, true))
        assertEquals(after, SplitMath.adjustTrade(after, split, true))
        // 恢復交易日前一天還是分割前
        assertEquals(5000.0, SplitMath.adjustTrade(trade(109, 1000.0, 150.0), split, true).shares, 0.0)
    }

    @Test
    fun otherStocksAndMarketsAreUntouched() {
        val other = trade(50, 1000.0, 600.0, symbol = "2330")
        val us = trade(50, 10.0, 100.0, symbol = "0050", market = Market.US)
        assertEquals(other, SplitMath.adjustTrade(other, split, true))
        assertEquals(us, SplitMath.adjustTrade(us, split, true))
    }

    @Test
    fun forwardThenReverse_restoresOriginal_evenWithRepeatingDecimals() {
        val s3 = split.copy(ratio = 3)
        val t = trade(50, 1000.0, 100.0)
        val back = SplitMath.adjustTrade(SplitMath.adjustTrade(t, s3, true), s3, false)
        assertEquals(t.shares, back.shares, 0.0)
        assertEquals(t.price, back.price, 1e-9)
    }

    @Test
    fun dividend_stockSharesAndPerShareAdjusted_cashUnchanged() {
        val d = Dividend(1, Market.TW, "0050", "x", 50, 10_000.0, 0.0, 0.0, 100.0, "", 2.5)
        val n = SplitMath.adjustDividend(d, split, true)
        assertEquals(500.0, n.stockShares, 0.0)
        assertEquals(0.5, n.perShare!!, 1e-12)
        assertEquals(10_000.0, n.cashAmount, 0.0)
        // 沒有每股股利的舊紀錄維持 null
        assertNull(SplitMath.adjustDividend(d.copy(perShare = null), split, true).perShare)
        // 恢復交易日當天或之後發放的不動
        assertEquals(d.copy(date = 110), SplitMath.adjustDividend(d.copy(date = 110), split, true))
    }

    @Test
    fun pledge_sharesAdjusted_amountUnchanged_twOnly() {
        val p = Pledge(1, "0050", "x", 2000.0, 300_000.0, 2.0, 50)
        val n = SplitMath.adjustPledge(p, split, true)
        assertEquals(10_000.0, n.shares, 0.0)
        assertEquals(300_000.0, n.amount, 0.0)
        assertEquals(p.copy(date = 110), SplitMath.adjustPledge(p.copy(date = 110), split, true))
        assertEquals(p, SplitMath.adjustPledge(p, split.copy(market = Market.US), true))
    }

    @Test
    fun alerts_perShareThresholdsAdjusted_percentThresholdsNot() {
        fun alert(type: AlertType, th: Double) = PriceAlert(1, Market.TW, "0050", "x", type, th)
        assertEquals(30.0, SplitMath.adjustAlert(alert(AlertType.PRICE_ABOVE, 150.0), split, true).threshold, 1e-12)
        assertEquals(20.0, SplitMath.adjustAlert(alert(AlertType.PRICE_BELOW, 100.0), split, true).threshold, 1e-12)
        assertEquals(1.0, SplitMath.adjustAlert(alert(AlertType.RISE_AMOUNT, 5.0), split, true).threshold, 1e-12)
        assertEquals(3.0, SplitMath.adjustAlert(alert(AlertType.RISE_PCT, 3.0), split, true).threshold, 0.0)
        assertEquals(-3.0, SplitMath.adjustAlert(alert(AlertType.FALL_PCT, -3.0), split, true).threshold, 0.0)
        // 別檔股票不動
        val other = alert(AlertType.PRICE_ABOVE, 150.0).copy(symbol = "2330")
        assertEquals(other, SplitMath.adjustAlert(other, split, true))
    }

    @Test
    fun portfolio_afterSplit_sharesMultiplied_totalCostUnchanged_avgCostDivided() {
        val trades = listOf(trade(10, 1000.0, 150.0), trade(50, 1000.0, 170.0))
        val before = Portfolio.build(trades, emptyList()).single()
        val after = Portfolio.build(trades.map { SplitMath.adjustTrade(it, split, true) }, emptyList()).single()
        assertEquals(before.shares * 5, after.shares, 1e-9)
        assertEquals(before.totalCost, after.totalCost, 1e-6)
        assertEquals(before.avgCost / 5, after.avgCost, 1e-9)
    }

    @Test
    fun sellAfterSplit_isCheckedAgainstSplitAdjustedShares() {
        // 分割前買 1,000 股 → 分割後 5,000 股；恢復交易日後賣 3,000 股（分割後股數）不應該算股數不足
        val trades = listOf(
            SplitMath.adjustTrade(trade(10, 1000.0, 150.0), split, true),
            Trade(2, Market.TW, "0050", "0050", TradeType.SELL, 120, 3000.0, 31.0, 20.0, 0.0),
        )
        assertNull(Portfolio.firstShortage(trades, emptyList(), Market.TW, "0050"))
    }

    // ---- 歷史價格與分割的基準對齊（Yahoo 沒調整完整時，月報酬不能被放大成倍數）----
    // 1 分 5、恢復交易日 110。例（取自 00662 的真實走勢）：Yahoo 只調整了最近兩個交易日（105、106），之前（100、101）還是原價

    private val partial = listOf(
        Bar(100, 118.0), Bar(101, 113.35),            // 原價，沒調整
        Bar(105, 22.88), Bar(106, 23.56),             // 已調整（÷5）
        Bar(110, 23.11),                              // 分割後
    )

    @Test
    fun unadjustedBefore_partialAdjustment_findsTheDropDay() {
        assertEquals(105L, SplitMath.unadjustedBefore(partial, split))
    }

    @Test
    fun unadjustedBefore_notAdjustedAtAll_dropIsAtResume() {
        val bars = listOf(Bar(100, 118.0), Bar(106, 117.8), Bar(110, 23.11))
        assertEquals(110L, SplitMath.unadjustedBefore(bars, split))
    }

    @Test
    fun unadjustedBefore_fullyAdjusted_isNull() {
        val bars = listOf(Bar(100, 23.6), Bar(101, 22.67), Bar(105, 22.88), Bar(106, 23.56), Bar(110, 23.11))
        assertNull(SplitMath.unadjustedBefore(bars, split))
    }

    @Test
    fun unadjustedBefore_normalMovesAreNotMistakenForSplit() {
        // 跌停（−10%）、大跌 30% 都遠小於 5 倍的平方根
        val bars = listOf(Bar(100, 100.0), Bar(101, 90.0), Bar(102, 63.0), Bar(103, 66.0))
        assertNull(SplitMath.unadjustedBefore(bars, split))
    }

    @Test
    fun alignBars_dividesOnlyTheUnadjustedPart() {
        val aligned = SplitMath.alignBars(partial, 5, 105)
        assertEquals(listOf(23.6, 22.67, 22.88, 23.56, 23.11), aligned.map { Math.round(it.close * 100) / 100.0 })
    }

    @Test
    fun dailyReturns_partialYahooAdjustment_buyDayIsCorrectAfterAlign() {
        // 分割前（99）買 1,000 股 @118，105 又買 1,000 股 @114；兩筆都在恢復交易日（110）之前 → 股數 × 5、價格 ÷ 5
        val t1 = SplitMath.adjustTrade(trade(99, 1000.0, 118.0).copy(fee = 0.0), split, true)
        val t2 = SplitMath.adjustTrade(trade(105, 1000.0, 114.0).copy(fee = 0.0), split, true)
        val trades = listOf(t1, t2)
        val align = SplitMath.alignBars(partial, 5, SplitMath.unadjustedBefore(partial, split)!!)

        val wrong = DailyReturns.compute(trades, emptyList(), mapOf("0050" to partial), 105, 105).single()
        val right = DailyReturns.compute(trades, emptyList(), mapOf("0050" to align), 105, 105).single()

        // 真正的損益：前一天（101）5,000 股 × 22.67 = 113,350；105 持有 10,000 股 × 22.88 = 228,800；買進成本 5,000 × 22.8 = 114,000
        assertEquals(228_800.0 - 113_350.0 - 114_000.0, right.pnl, 1e-6)
        // 沒對齊時，前一天的市值被放大成 5 倍，買進當天損益嚴重失真
        assertEquals(228_800.0 - 566_750.0 - 114_000.0, wrong.pnl, 1e-6)
    }

    // ---- 還原備份時保留手機上的分割 ----

    private fun sp(id: Long, symbol: String = "0050", resume: Long = 110, ratio: Int = 5, applied: Boolean = false, suspend: Long = 100) =
        StockSplit(id, Market.TW, symbol, symbol, suspend, resume, ratio, applied)

    @Test
    fun carryOver_splitMissingFromBackup_isKeptAsNotApplied() {
        // 備份是分割設定之前做的（備份裡沒有）；手機上已執行（applied = true）→ 補回，並重新執行
        val device = listOf(sp(7, applied = true))
        val extra = SplitMath.splitsToCarryOver(device, emptyList())
        assertEquals(listOf(sp(0, applied = false)), extra)
    }

    @Test
    fun carryOver_splitAlreadyInBackup_isNotDuplicated() {
        val device = listOf(sp(7, applied = true))
        // 備份裡有同一筆（暫停日不同也算同一筆）：不補，沿用備份自己的 applied 狀態
        assertEquals(emptyList<StockSplit>(), SplitMath.splitsToCarryOver(device, listOf(sp(1, applied = false, suspend = 99))))
        assertEquals(emptyList<StockSplit>(), SplitMath.splitsToCarryOver(device, listOf(sp(1, applied = true))))
    }

    @Test
    fun carryOver_onlyTheMissingOnes() {
        val device = listOf(sp(1), sp(2, symbol = "0056"), sp(3, symbol = "2330", ratio = 2))
        val backup = listOf(sp(9, symbol = "0056"))
        assertEquals(listOf("0050", "2330"), SplitMath.splitsToCarryOver(device, backup).map { it.symbol })
    }
}
