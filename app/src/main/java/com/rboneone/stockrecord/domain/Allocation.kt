package com.rboneone.stockrecord.domain

import com.rboneone.stockrecord.data.AssetCategory
import com.rboneone.stockrecord.data.Market

/** 一檔持股在資產配置中的狀態，市值已換算台幣 */
data class AllocationItem(
    val position: Position,
    val category: AssetCategory?,
    /** 台幣市值；美股沒有匯率時為 null */
    val valueTwd: Double?,
    /** 台幣成本；美股沒有匯率時為 null */
    val costTwd: Double? = null,
)

/**
 * 資產配置（皆為台幣）：
 * 總資產 = 原型 + 正二 + 類現金 + 現金 + 個股 + 其他
 * 曝險 = 原型 + 2 × 正二
 */
data class AllocationView(
    val core: Double,
    val leveraged: Double,
    val cashLike: Double,
    val cash: Double,
    val stock: Double,
    val other: Double,
    val items: List<AllocationItem>,
    /** 有美股持股但尚未取得匯率 */
    val missingFx: Boolean,
) {
    /** 持股成本（台幣，A. 投入資金）：所有持股的成本合計 */
    val holdingCost: Double get() = items.sumOf { it.costTwd ?: 0.0 }

    /** 持股市值（台幣）：所有持股的市值合計，不含現金 */
    val holdingValue: Double get() = items.sumOf { it.valueTwd ?: 0.0 }

    val total: Double get() = core + leveraged + cashLike + cash + stock + other
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
                val cost = when (h.market) {
                    Market.TW -> h.totalCost
                    Market.US -> fx?.let { h.totalCost * it }
                }
                AllocationItem(p, categories[h.market to h.symbol], twd, cost)
            }
        fun sum(category: AssetCategory) =
            items.filter { it.category == category }.sumOf { it.valueTwd ?: 0.0 }
        return AllocationView(
            core = sum(AssetCategory.CORE),
            leveraged = sum(AssetCategory.LEVERAGED_2X),
            cashLike = sum(AssetCategory.CASH_LIKE),
            cash = cash,
            stock = sum(AssetCategory.STOCK),
            other = sum(AssetCategory.OTHER),
            items = items,
            missingFx = items.any { it.valueTwd == null },
        )
    }
}
