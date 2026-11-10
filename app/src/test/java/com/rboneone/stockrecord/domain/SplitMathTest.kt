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
}
