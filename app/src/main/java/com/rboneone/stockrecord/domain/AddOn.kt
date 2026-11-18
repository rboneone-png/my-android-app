package com.rboneone.stockrecord.domain

/**
 * 加減碼策略。
 *
 * - A 投入資金 = 持股成本；B 持股市值 = 持股市值（皆台幣，不含現金）
 * - C 配置方式：與 CLEC 相同的選項（原型／正二／類現金 三碼）
 * - D 預留生活費：使用者輸入
 * - 策略 VS 實際：策略佔比＝配置方式的各碼；策略金額＝策略佔比 × A（類現金與現金合併成一列，共用「類現金%」）；
 *   實際＝資產配置頁各項佔總資產的比例
 * - 加碼金 = A × 0.3 − D；每次加碼金額 = 加碼金 ÷ 加碼等份
 * - 減碼：加碼每漲回 10%，賣出加碼金的 10% 放回類現金，直到全部取回（共 10 次，每次 = 加碼金 × 10%）
 */
object AddOn {
    const val ADD_RATIO = 0.3
    const val REDUCE_STEP = 0.1

    data class Result(
        val invested: Double,
        val marketValue: Double,
        val method: Clec.Plan,
        val reserve: Double,
        val tranches: Int?,
        val coreAmount: Double,
        val leveragedAmount: Double,
        val cashLikeAmount: Double,
        /** 加碼金總額 = A × 0.3 − D（可能 ≤ 0，代表預留生活費已超過可加碼的額度） */
        val addCapital: Double,
        /** 每次加碼金額；加碼金 ≤ 0 或等份未填時為 null */
        val perTranche: Double?,
        /** 每次減碼（賣出）金額 = 加碼金 × 10%；加碼金 ≤ 0 時為 null */
        val perReduce: Double?,
    )

    fun compute(view: AllocationView, methodCode: String, reserve: Double, tranches: Double): Result {
        val a = view.holdingCost
        val plan = Clec.plan(methodCode)
        val cap = a * ADD_RATIO - reserve
        val n = tranches.toInt().takeIf { it > 0 && tranches == it.toDouble() }
        return Result(
            a, view.holdingValue, plan, reserve, n,
            a * plan.corePct, a * plan.leveragedPct, a * plan.cashLikePct,
            cap,
            if (cap > 0 && n != null) cap / n else null,
            if (cap > 0) cap * REDUCE_STEP else null,
        )
    }
}
