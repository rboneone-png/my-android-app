package com.rboneone.stockrecord.domain

/** 股利計算 */
object DividendMath {
    /**
     * 現金股利總額 = 每股股利 × 當天持有股數，四捨五入到小數兩位
     * （避免 0.7 × 23,000 = 16,099.999999999998 這類浮點數誤差）
     */
    fun cashTotal(perShare: Double, heldShares: Double): Double =
        Math.round(perShare * heldShares * 100) / 100.0

    /** 舊紀錄沒有每股股利時，由現金股利總額反推（只給編輯時顯示），保留 6 位小數，乘回去可能差幾分錢 */
    fun perShareFrom(cashAmount: Double, heldShares: Double): Double? =
        if (heldShares > 0 && cashAmount > 0) Math.round(cashAmount / heldShares * 1_000_000) / 1_000_000.0 else null
}
