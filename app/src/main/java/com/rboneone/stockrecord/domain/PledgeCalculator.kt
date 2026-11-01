package com.rboneone.stockrecord.domain

import com.rboneone.stockrecord.data.AssetCategory
import com.rboneone.stockrecord.data.Market
import com.rboneone.stockrecord.data.Pledge
import com.rboneone.stockrecord.data.Quote
import java.time.LocalDate
import java.time.YearMonth

/** 維持率的警示等級 */
enum class PledgeLevel { SAFE, WARNING, DANGER }

data class PledgeItem(
    val pledge: Pledge,
    val price: Double?,
) {
    /** 質押市值 = 質押股數 × 股價 */
    val marketValue: Double? get() = price?.let { it * pledge.shares }

    /** 維持率 = 質押股數 × 股價 ÷ 質押金額 × 100% */
    val ratio: Double? get() = marketValue?.takeIf { pledge.amount > 0 }?.let { it / pledge.amount * 100 }
}

/** 同一個質押月份的質押 */
data class PledgeMonth(val month: YearMonth, val items: List<PledgeItem>) {
    val totalMarketValue: Double get() = items.sumOf { it.marketValue ?: 0.0 }
    val totalAmount: Double get() = items.sumOf { it.pledge.amount }
    val totalInterest: Double get() = items.sumOf { it.pledge.interest }
    val missingQuotes: Int get() = items.count { it.price == null }

    /** 該月份的維持率 = 該月質押市值 ÷ 該月質押金額 × 100% */
    val ratio: Double?
        get() = if (totalAmount > 0 && missingQuotes == 0) totalMarketValue / totalAmount * 100 else null
}

data class PledgeView(
    /** 可質押股票總市值：目前所有台股持股的市值 */
    val pledgeableValue: Double,
    val items: List<PledgeItem>,
) {
    /** 依質押日期的月份分組，新的月份在前；同月份內日期新的在前 */
    val months: List<PledgeMonth>
        get() = items
            .groupBy { YearMonth.from(LocalDate.ofEpochDay(it.pledge.date)) }
            .map { (month, list) -> PledgeMonth(month, list.sortedWith(compareByDescending<PledgeItem> { it.pledge.date }.thenByDescending { it.pledge.id })) }
            .sortedByDescending { it.month }

    val totalMarketValue: Double get() = items.sumOf { it.marketValue ?: 0.0 }
    val totalAmount: Double get() = items.sumOf { it.pledge.amount }
    val totalInterest: Double get() = items.sumOf { it.pledge.interest }

    /** 有股票還沒有報價時，總維持率無法正確計算 */
    val missingQuotes: Int get() = items.count { it.price == null }

    /** 質押維持率 = 質押總市值 ÷ 質押總金額 × 100% */
    val ratio: Double?
        get() = if (totalAmount > 0 && missingQuotes == 0) totalMarketValue / totalAmount * 100 else null
}

object PledgeCalculator {
    const val SAFE_RATIO = 250.0
    const val DANGER_RATIO = 175.0

    /**
     * ≥ 250% 安全（綠）、175%～250% 注意（黃）、< 175% 危險（紅）。
     * 以畫面顯示的數字（四捨五入到小數一位）判斷，避免顯示 250.0% 卻是黃色。
     */
    fun level(ratio: Double): PledgeLevel {
        val shown = Math.round(ratio * 10) / 10.0
        return when {
            shown >= SAFE_RATIO -> PledgeLevel.SAFE
            shown >= DANGER_RATIO -> PledgeLevel.WARNING
            else -> PledgeLevel.DANGER
        }
    }

    /**
     * includeLeveraged = false 時，可質押股票總市值不計入分類為「正二」的持股
     */
    fun build(
        pledges: List<Pledge>,
        quotes: Map<Pair<Market, String>, Quote>,
        portfolio: PortfolioView?,
        categories: Map<Pair<Market, String>, AssetCategory> = emptyMap(),
        includeLeveraged: Boolean = true,
    ): PledgeView =
        PledgeView(
            pledgeableValue = portfolio?.positions
                ?.filter { it.holding.market == Market.TW && it.holding.isOpen }
                ?.filter { includeLeveraged || categories[Market.TW to it.holding.symbol] != AssetCategory.LEVERAGED_2X }
                ?.sumOf { it.marketValue ?: it.holding.totalCost } ?: 0.0,
            items = pledges.map { PledgeItem(it, quotes[Market.TW to it.symbol]?.price) },
        )
}
