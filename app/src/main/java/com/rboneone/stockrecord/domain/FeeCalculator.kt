package com.rboneone.stockrecord.domain

import com.rboneone.stockrecord.data.Market
import com.rboneone.stockrecord.data.TaxType
import com.rboneone.stockrecord.data.TradeType
import kotlin.math.floor
import kotlin.math.max

data class FeeSettings(
    /** 台股手續費折扣，例如 2.8 折填 0.28 */
    val twDiscount: Double = 0.28,
    /** 台股單筆最低手續費（元） */
    val twMinFee: Double = 20.0,
    /** 美股每筆固定手續費（美元） */
    val usCommission: Double = 0.0,
    /** 美股股利預扣稅率 */
    val usDividendTaxRate: Double = 0.30,
)

data class FeeResult(val fee: Double, val tax: Double)

object FeeCalculator {
    const val TW_FEE_RATE = 0.001425

    // 避免浮點誤差讓 239.99999 被捨去成 239
    private fun floorSafe(value: Double) = floor(value + 1e-6)

    /** 台股手續費：成交金額 × 0.1425% × 折扣，無條件捨去，不足最低手續費以最低計 */
    fun twFee(amount: Double, settings: FeeSettings): Double {
        if (amount <= 0) return 0.0
        return max(floorSafe(amount * TW_FEE_RATE * settings.twDiscount), settings.twMinFee)
    }

    /** 台股證交稅：賣出成交金額 × 稅率，無條件捨去 */
    fun twTax(amount: Double, taxType: TaxType): Double {
        if (amount <= 0) return 0.0
        return floorSafe(amount * taxType.rate)
    }

    fun calculate(
        market: Market,
        type: TradeType,
        shares: Double,
        price: Double,
        taxType: TaxType,
        settings: FeeSettings,
    ): FeeResult {
        val amount = shares * price
        if (amount <= 0) return FeeResult(0.0, 0.0)
        return when (market) {
            Market.TW -> FeeResult(
                fee = twFee(amount, settings),
                tax = if (type == TradeType.SELL) twTax(amount, taxType) else 0.0,
            )
            Market.US -> FeeResult(fee = settings.usCommission, tax = 0.0)
        }
    }

    /** 二代健保補充保費：單次股利達 2 萬元時扣 2.11% */
    const val TW_HEALTH_RATE = 0.0211
    const val TW_HEALTH_THRESHOLD = 20_000.0

    /** 股利代扣稅款的建議值 */
    fun dividendTax(market: Market, cashAmount: Double, settings: FeeSettings): Double = when (market) {
        Market.TW -> if (cashAmount >= TW_HEALTH_THRESHOLD) floorSafe(cashAmount * TW_HEALTH_RATE) else 0.0
        Market.US -> Math.round(cashAmount * settings.usDividendTaxRate * 100) / 100.0
    }
}
