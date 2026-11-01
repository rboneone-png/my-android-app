package com.rboneone.stockrecord.domain

import com.rboneone.stockrecord.data.Market
import com.rboneone.stockrecord.data.TaxType
import com.rboneone.stockrecord.data.TradeType
import org.junit.Assert.assertEquals
import org.junit.Test

class FeeCalculatorTest {
    private val settings = FeeSettings(twDiscount = 0.28, twMinFee = 20.0, usCommission = 0.0)

    @Test
    fun twBuy_appliesDiscountAndFloors() {
        // 1000 股 × 600 = 600,000；× 0.1425% = 855；× 0.28 = 239.4 → 239
        val r = FeeCalculator.calculate(Market.TW, TradeType.BUY, 1000.0, 600.0, TaxType.STOCK, settings)
        assertEquals(239.0, r.fee, 0.0)
        assertEquals(0.0, r.tax, 0.0)
    }

    @Test
    fun twFee_usesMinimum() {
        // 100 股 × 50 = 5,000；手續費 1.995 → 最低 20
        val r = FeeCalculator.calculate(Market.TW, TradeType.BUY, 100.0, 50.0, TaxType.STOCK, settings)
        assertEquals(20.0, r.fee, 0.0)
    }

    @Test
    fun twSell_taxRates() {
        val stock = FeeCalculator.calculate(Market.TW, TradeType.SELL, 1000.0, 600.0, TaxType.STOCK, settings)
        assertEquals(1800.0, stock.tax, 0.0)
        val etf = FeeCalculator.calculate(Market.TW, TradeType.SELL, 1000.0, 150.55, TaxType.ETF, settings)
        assertEquals(150.0, etf.tax, 0.0) // 150,550 × 0.1% = 150.55 → 150
        val dayTrade = FeeCalculator.calculate(Market.TW, TradeType.SELL, 1000.0, 600.0, TaxType.DAY_TRADE, settings)
        assertEquals(900.0, dayTrade.tax, 0.0)
    }

    @Test
    fun us_usesFixedCommissionAndNoTax() {
        val r = FeeCalculator.calculate(
            Market.US, TradeType.SELL, 10.0, 190.5, TaxType.STOCK, settings.copy(usCommission = 1.5)
        )
        assertEquals(1.5, r.fee, 0.0)
        assertEquals(0.0, r.tax, 0.0)
    }
}
