package com.rboneone.stockrecord.domain

import com.rboneone.stockrecord.data.Dividend
import com.rboneone.stockrecord.data.Market
import com.rboneone.stockrecord.data.Trade
import com.rboneone.stockrecord.data.TradeType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 美股買賣與股利：每筆用自己當時的匯率換算台幣，所以目前匯率變動不會改動帳務餘額 */
class AccountingFxTest {
    private val today = 20_000L

    private fun us(id: Long, type: TradeType, shares: Double, price: Double, fx: Double?) =
        Trade(id, Market.US, "AAPL", "Apple", type, today - 5, shares, price, 0.0, 0.0, fxRate = fx)

    private fun account(trades: List<Trade>, current: Double?, dividends: List<Dividend> = emptyList()) =
        Accounting.dayAccount(emptyList(), trades, emptyList(), current, today, dividends)

    @Test fun eachTradeUsesItsOwnRate_notTheCurrentRate() {
        val trades = listOf(
            us(1, TradeType.BUY, 10.0, 100.0, 30.0),    // 1,000 美元 × 30 = 30,000
            us(2, TradeType.BUY, 10.0, 100.0, 32.0),    // 1,000 美元 × 32 = 32,000
        )
        // 目前匯率不管是 28 還是 35，結果都一樣
        assertEquals(-62_000L, account(trades, 28.0).accountTotal)
        assertEquals(-62_000L, account(trades, 35.0).accountTotal)
        assertEquals(-62_000L, account(trades, null).accountTotal)
        assertFalse(account(trades, null).usMissingRate)
    }

    @Test fun balanceDoesNotMoveWhenCurrentRateMoves() {
        val trades = listOf(us(1, TradeType.BUY, 10.0, 100.0, 31.0), us(2, TradeType.SELL, 4.0, 110.0, 31.5))
        val a = account(trades, 30.0).let { it.deposits - it.withdrawals + it.accountTotal }
        val b = account(trades, 33.3).let { it.deposits - it.withdrawals + it.accountTotal }
        assertEquals(a, b)
    }

    @Test fun oldRecordWithoutRateFallsBackToCurrentRate() {
        val trades = listOf(us(1, TradeType.BUY, 10.0, 100.0, null))
        assertEquals(-30_000L, account(trades, 30.0).accountTotal)
        assertEquals(-35_000L, account(trades, 35.0).accountTotal)
    }

    @Test fun noRateAnywhereIsMissing() {
        val a = account(listOf(us(1, TradeType.BUY, 10.0, 100.0, null)), null)
        assertTrue(a.usMissingRate)
        assertEquals(0L, a.accountTotal)
    }

    @Test fun usDividendUsesItsOwnRate() {
        val d = Dividend(1, Market.US, "AAPL", "Apple", today - 1, 10.0, 3.0, 0.0, 0.0, "", fxRate = 32.0)   // 實收 7 美元
        assertEquals(224L, account(emptyList(), 28.0, listOf(d)).dividendsTotal)
        assertEquals(224L, account(emptyList(), 40.0, listOf(d)).dividendsTotal)
    }

    @Test fun twTradesUnaffectedByRate() {
        val tw = Trade(1, Market.TW, "0050", "x", TradeType.BUY, today, 1000.0, 100.0, 0.0, 0.0)
        assertEquals(-100_000L, account(listOf(tw), 28.0).accountTotal)
        assertEquals(-100_000L, account(listOf(tw), null).accountTotal)
    }
}
