package com.rboneone.stockrecord.domain

/** 美元兌台幣匯率的歷史查詢（日線收盤價），給美股買賣與股利補上「當時的匯率」用 */
object FxRates {
    /** 往前最多找幾天（遇到週末、假日沒有報價時，用前一個有報價的日子） */
    const val LOOKBACK_DAYS = 10L

    /**
     * date 當天的匯率：取「當天或之前最近一個交易日」的收盤價。
     * 若 date 比資料最早的一筆還早，但相差不超過 [LOOKBACK_DAYS]，用最早那一筆；其餘回傳 null。
     */
    fun rateOn(bars: List<Bar>, date: Long): Double? {
        if (bars.isEmpty()) return null
        val sorted = bars.sortedBy { it.date }
        val onOrBefore = sorted.lastOrNull { it.date <= date }
        if (onOrBefore != null) return onOrBefore.close.takeIf { it > 0 && date - onOrBefore.date <= LOOKBACK_DAYS * 3 }
        val first = sorted.first()
        return first.close.takeIf { it > 0 && first.date - date <= LOOKBACK_DAYS }
    }
}
