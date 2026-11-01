package com.rboneone.stockrecord.domain

import com.rboneone.stockrecord.data.AssetCategory
import com.rboneone.stockrecord.data.Market

/** 一檔持股在資產配置中的狀態，市值已換算台幣 */
data class AllocationItem(
    val position: Position,
    val category: AssetCategory?,
    /** 台幣市值；美股沒有匯率時為 null */
    val valueTwd: Double?,
)

/**
 * 資產配置（皆為台幣）：
 * 總資產 = 原型 + 正二 + 類現金 + 現金 + 其他
 * 曝險 = 原型 + 2 × 正二
 */
data class AllocationView(
    val core: Double,
    val leveraged: Double,
    val cashLike: Double,
    val cash: Double,
    val other: Double,
    val items: List<AllocationItem>,
    /** 有美股持股但尚未取得匯率 */
    val missingFx: Boolean,
) {
    val total: Double get() = core + leveraged + cashLike + cash + other
    val exposure: Double get() = core + 2 * leveraged

    fun pct(value: Double): Double? = if (total > 0) value / total * 100 else null
    val exposurePct: Double? get() = pct(exposure)

    val uncategorized: List<AllocationItem> get() = items.filter { it.category == null }
}

object Allocation {
    fun build(
        view: PortfolioView,
        categories: Map<Pair<Market, String>, AssetCategory>,
        cash: Double,
    ): AllocationView {
        val fx = view.usdTwd?.price
        val items = view.positions
            .filter { it.holding.isOpen }
            .map { p ->
                val h = p.holding
                // 沒有報價時以成本計算市值，和持股總覽一致
                val value = p.marketValue ?: h.totalCost
                val twd = when (h.market) {
                    Market.TW -> value
                    Market.US -> fx?.let { value * it }
                }
                AllocationItem(p, categories[h.market to h.symbol], twd)
            }
        fun sum(category: AssetCategory) =
            items.filter { it.category == category }.sumOf { it.valueTwd ?: 0.0 }
        return AllocationView(
            core = sum(AssetCategory.CORE),
            leveraged = sum(AssetCategory.LEVERAGED_2X),
            cashLike = sum(AssetCategory.CASH_LIKE),
            cash = cash,
            other = sum(AssetCategory.OTHER),
            items = items,
            missingFx = items.any { it.valueTwd == null },
        )
    }
}
