package com.rboneone.stockrecord.domain

import com.rboneone.stockrecord.data.CashFlow
import com.rboneone.stockrecord.data.CashFlowType
import com.rboneone.stockrecord.data.Market
import com.rboneone.stockrecord.data.Trade
import com.rboneone.stockrecord.data.TradeType
import org.junit.Assert.assertEquals
import org.junit.Test

class AccountingTest {
    private val today = 20_000L

    private fun flow(id: Long, type: CashFlowType, date: Long, amount: Double, market: Market = Market.TW) =
        CashFlow(id, market, type, date, amount)

    private fun trade(id: Long, type: TradeType, date: Long, shares: Double, price: Double, market: Market = Market.TW) =
        Trade(id, market, "A", "A", type, date, shares, price, 20.0, 0.0)

    @Test
    fun formulas() {
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
        val a = Accounting.dayAccount(flows, trades, Market.TW, today)
        assertEquals(1_200_000.0, a.deposits, 0.0)
        assertEquals(150_000.0, a.withdrawals, 0.0)
        assertEquals(150_000.0, a.sells, 0.0)     // B
        assertEquals(200_000.0, a.buys, 0.0)      // C
        assertEquals(-50_000.0, a.dayNet, 0.0)    // D = B − C
        // A = 存入 − 領出 + D = 1,200,000 − 150,000 − 50,000
        assertEquals(1_000_000.0, a.available, 0.0)
    }

    @Test
    fun futureCashFlowsAndOtherMarketIgnored() {
        val flows = listOf(
            flow(1, CashFlowType.DEPOSIT, today + 1, 500.0),                 // 還沒到
            flow(2, CashFlowType.DEPOSIT, today, 700.0, Market.US),          // 美股
            flow(3, CashFlowType.DEPOSIT, today, 300.0),
        )
        val trades = listOf(trade(1, TradeType.SELL, today, 10.0, 10.0, Market.US))
        val tw = Accounting.dayAccount(flows, trades, Market.TW, today)
        assertEquals(300.0, tw.available, 0.0)
        val us = Accounting.dayAccount(flows, trades, Market.US, today)
        assertEquals(700.0 + 100.0, us.available, 0.0)
    }

    @Test
    fun empty() {
        val a = Accounting.dayAccount(emptyList(), emptyList(), Market.TW, today)
        assertEquals(0.0, a.available, 0.0)
        assertEquals(0.0, a.dayNet, 0.0)
    }

    @Test
    fun signedAmount() {
        assertEquals(100.0, flow(1, CashFlowType.DEPOSIT, today, 100.0).signedAmount, 0.0)
        assertEquals(-100.0, flow(1, CashFlowType.WITHDRAW, today, 100.0).signedAmount, 0.0)
    }
}
