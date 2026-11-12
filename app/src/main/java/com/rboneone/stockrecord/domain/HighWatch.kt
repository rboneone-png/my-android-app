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

    /** 技術點：P1～P5 依序是從最高點下跌 10%～50% 的價位 */
    data class DropLevel(val label: String, val dropPct: Int, val price: Double)

    val DROP_PCTS = listOf(10, 20, 30, 40, 50)

    /** 股價 = 2 年最高點 × (1 − 下跌百分比)；還沒有最高價時回傳空清單 */
    fun dropLevels(high: Double?): List<DropLevel> =
        if (high == null || high <= 0) emptyList()
        else DROP_PCTS.mapIndexed { i, pct -> DropLevel("P${i + 1}", pct, high * (100 - pct) / 100.0) }
}
