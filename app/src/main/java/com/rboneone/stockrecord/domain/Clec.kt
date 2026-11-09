package com.rboneone.stockrecord.domain

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * CLEC 退休投資策略。
 *
 * 配置方式是三位數字：第一碼＝原型指數、第二碼＝正二槓桿、第三碼＝類現金，各碼是「幾成」。
 * 例如 703 → 70% 原型指數、0% 正二槓桿、30% 類現金。
 *
 * - C 曝險（Beta）= 第一碼數值 + 2 × 第二碼數值（703 → 0.7 + 2 × 0.0 = 0.7）
 * - D 金融資產 = F + G + H（F 指數ETF＝原型市值、G 正二ETF＝正二市值、H 類現金＝類現金市值＋現金）
 * - I 幾倍年生活費 = D ÷ A（A＝一年生活費），小數一位、第二位四捨五入
 * - J 現況建議配置 = 依 I 對照 [PLANS] 的 N 年生活費區間
 */
object Clec {
    /** 一列配置：代碼、N 年生活費區間說明、區間下限（含）。依年數由大到小排列 */
    data class Plan(val code: String, val rangeLabel: String, val minYears: Double?) {
        /** 原型指數、正二槓桿、類現金的比例（0～1） */
        val corePct: Double get() = (code[0] - '0') / 10.0
        val leveragedPct: Double get() = (code[1] - '0') / 10.0
        val cashLikePct: Double get() = (code[2] - '0') / 10.0

        /** 曝險（Beta）= 第一碼數值 + 2 × 第二碼數值 */
        val beta: Double get() = corePct + 2 * leveragedPct

        /** 例如「原型 70%／正二 0%／類現金 30%」 */
        val mixText: String
            get() = "原型 ${(code[0] - '0') * 10}%／正二 ${(code[1] - '0') * 10}%／類現金 ${(code[2] - '0') * 10}%"
    }

    val PLANS = listOf(
        Plan("703", ">50 年生活費", null),   // > 50
        Plan("613", "40～50 年生活費", 40.0),
        Plan("523", "30～40 年生活費", 30.0),
        Plan("433", "20～30 年生活費", 20.0),
        Plan("343", "16～20 年生活費", 16.0),
        Plan("253", "13～16 年生活費", 13.0),
        Plan("163", "10～13 年生活費", 10.0),
        Plan("073", "<10 年生活費", 0.0),
    )

    const val DEFAULT_CODE = "433"

    fun plan(code: String): Plan = PLANS.firstOrNull { it.code == code } ?: PLANS.first { it.code == DEFAULT_CODE }

    /** I = D ÷ A，小數一位、第二位四捨五入；生活費沒填（≤ 0）時為 null */
    fun years(financialAssets: Double, livingCost: Double): Double? {
        if (livingCost <= 0) return null
        return BigDecimal.valueOf(financialAssets / livingCost).setScale(1, RoundingMode.HALF_UP).toDouble()
    }

    /**
     * 依 I 值找對應的配置。邊界歸屬：> 50 → 703；剛好 50 屬於 40～50；
     * 其餘區間含下限不含上限（例如 30 屬於 30～40、10 屬於 10～13）；< 10 → 073。
     */
    fun suggest(years: Double): Plan = when {
        years > 50 -> PLANS[0]
        years >= 40 -> PLANS[1]
        years >= 30 -> PLANS[2]
        years >= 20 -> PLANS[3]
        years >= 16 -> PLANS[4]
        years >= 13 -> PLANS[5]
        years >= 10 -> PLANS[6]
        else -> PLANS[7]
    }

    data class Result(
        /** A 一年生活費 */
        val livingCost: Double,
        /** B 配置方式 */
        val method: Plan,
        /** C 曝險（Beta） */
        val beta: Double,
        /** D 金融資產 = F + G + H */
        val financialAssets: Double,
        /** E 曝險金額（資產配置頁的曝險 = 原型 + 2 × 正二） */
        val exposure: Double,
        /** F 指數ETF */
        val indexEtf: Double,
        /** G 正二ETF */
        val leveragedEtf: Double,
        /** H 類現金 = 類現金市值 + 現金 */
        val cashLike: Double,
        /** I 幾倍年生活費；生活費沒填時為 null */
        val years: Double?,
        /** J 現況建議配置；I 算不出來時為 null */
        val suggested: Plan?,
    )

    fun compute(view: AllocationView, livingCost: Double, methodCode: String): Result {
        val f = view.core
        val g = view.leveraged
        val h = view.cashLike + view.cash
        val d = f + g + h
        val i = years(d, livingCost)
        val method = plan(methodCode)
        return Result(livingCost, method, method.beta, d, view.exposure, f, g, h, i, i?.let { suggest(it) })
    }
}
