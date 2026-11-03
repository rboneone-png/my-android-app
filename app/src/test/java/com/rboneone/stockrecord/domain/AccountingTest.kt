package com.rboneone.stockrecord.domain

import com.rboneone.stockrecord.data.CashFlow
import com.rboneone.stockrecord.data.CashFlowType
import com.rboneone.stockrecord.data.Market
import com.rboneone.stockrecord.data.Pledge
import com.rboneone.stockrecord.data.Trade
import com.rboneone.stockrecord.data.TradeType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountingTest {
    private val today = 20_000L

    private fun flow(id: Long, type: CashFlowType, date: Long, amount: Double) =
        CashFlow(id, Market.TW, type, date, amount)

    private fun trade(id: Long, type: TradeType, date: Long, shares: Double, price: Double, market: Market = Market.TW) =
        Trade(id, market, "A", "A", type, date, shares, price, 20.0, 0.0)

    private fun pledge(id: Long, date: Long, amount: Double) = Pledge(id, "2330", "台積電", 1000.0, amount, 2.0, date)

    @Test
    fun formulas_includingPledge() {
        val flows = listOf(
            flow(1, CashFlowType.DEPOSIT, today - 10, 1_000_000.0),
            flow(2, CashFlowType.DEPOSIT, today, 200_000.0),
            flow(3, CashFlowType.WITHDRAW, today - 3, 150_000.0),
        )
        val trades = listOf(
            trade(1, TradeType.BUY, today, 1000.0, 100.0),    // 買進 100,000
            trade(2, TradeType.BUY, today, 2000.0, 50.0),     // 買進 100,000
            trade(3, TradeType.SELL, today, 500.0, 300.0),    // 賣出 150,000
            trade(4, TradeType.BUY, today - 1, 9999.0, 999.0), // 不是今天
        )
        val pledges = listOf(pledge(1, today, 400_000.0), pledge(2, today - 1, 999_000.0))
        val a = Accounting.dayAccount(flows, trades, pledges, null, today)
        assertEquals(1_200_000L, a.deposits)
        assertEquals(150_000L, a.withdrawals)
        assertEquals(150_000L, a.sells)         // B
        assertEquals(200_000L, a.buys)          // C
        assertEquals(-50_000L, a.dayNet)        // D = B − C
        assertEquals(400_000L, a.pledged)       // 只算今天的質押
        // A = 存入 − 領出 + D + 當日質押 = 1,200,000 − 150,000 − 50,000 + 400,000
        assertEquals(1_400_000L, a.available)
        assertFalse(a.usMissingRate)
    }

    @Test
    fun usTradesConvertedAtTodaysRate_roundedPerTrade() {
        val trades = listOf(
            trade(1, TradeType.BUY, today, 10.0, 250.55, Market.US),    // 2,505.5 美元 × 32.6241 = 81,739.68 → 81,740
            trade(2, TradeType.SELL, today, 3.0, 100.0, Market.US),     // 300 × 32.6241 = 9,787.23 → 9,787
            trade(3, TradeType.SELL, today, 1000.0, 36.4, Market.TW),   // 36,400
        )
        val a = Accounting.dayAccount(emptyList(), trades, emptyList(), 32.6241, today)
        assertEquals(81_740L, a.buys)
        assertEquals(9_787L + 36_400L, a.sells)
        assertEquals(9_787L + 36_400L - 81_740L, a.dayNet)
        assertEquals(32.6241, a.usdTwd!!, 0.0)
        assertFalse(a.usMissingRate)
    }

    @Test
    fun usTradesWithoutRate_excludedAndFlagged() {
        val trades = listOf(
            trade(1, TradeType.BUY, today, 10.0, 100.0, Market.US),
            trade(2, TradeType.BUY, today, 1000.0, 36.0, Market.TW),
        )
        val a = Accounting.dayAccount(emptyList(), trades, emptyList(), null, today)
        assertEquals(36_000L, a.buys)
        assertTrue(a.usMissingRate)
        // 今天沒有美股買賣就不用提醒
        assertFalse(Accounting.dayAccount(emptyList(), listOf(trades[1]), emptyList(), null, today).usMissingRate)
    }

    @Test
    fun roundsHalfUp_andFutureCashFlowsIgnored() {
        val flows = listOf(
            flow(1, CashFlowType.DEPOSIT, today, 1_000.5),          // → 1,001
            flow(2, CashFlowType.DEPOSIT, today + 1, 500.0),        // 還沒到
            flow(3, CashFlowType.WITHDRAW, today, 200.4),           // → 200
        )
        val trades = listOf(trade(1, TradeType.SELL, today, 333.0, 36.05))  // 12,004.65 → 12,005
        val a = Accounting.dayAccount(flows, trades, listOf(pledge(1, today, 10_000.4)), null, today)
        assertEquals(1_001L, a.deposits)
        assertEquals(200L, a.withdrawals)
        assertEquals(12_005L, a.sells)
        assertEquals(10_000L, a.pledged)
        assertEquals(1_001L - 200L + 12_005L + 10_000L, a.available)
    }

    @Test
    fun empty() {
        val a = Accounting.dayAccount(emptyList(), emptyList(), emptyList(), null, today)
        assertEquals(0L, a.available)
        assertEquals(0L, a.dayNet)
    }
}
