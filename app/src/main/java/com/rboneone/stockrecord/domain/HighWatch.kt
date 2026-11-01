package com.rboneone.stockrecord.domain

/** 期間最高價與出現日期（LocalDate.toEpochDay()） */
data class PeriodHigh(val price: Double, val date: Long)

object HighWatch {
    /**
     * 顯示的最高價：網路抓到的期間最高價；盤中現價若更高（創新高）就用現價。
     * 還沒抓到最高價時回傳 null。
     */
    fun effectiveHigh(storedHigh: Double?, price: Double?): Double? =
        storedHigh?.let { if (price != null && price > it) price else it }

    /** 漲跌幅 = 現價 ÷ 最高價 × 100% */
    fun ratio(price: Double?, high: Double?): Double? =
        if (price != null && high != null && high > 0) price / high * 100 else null
}
