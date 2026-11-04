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

    /** 維持率 = 質押股數 × 股價 ÷ 質借金額 × 100% */
    val ratio: Double? get() = marketValue?.takeIf { pledge.amount > 0 }?.let { it / pledge.amount * 100 }
}

/** 同一個質押月份的質押 */
data class PledgeMonth(val month: YearMonth, val items: List<PledgeItem>) {
    val totalMarketValue: Double get() = items.sumOf { it.marketValue ?: 0.0 }
    val totalAmount: Double get() = items.sumOf { it.pledge.amount }
    val totalInterest: Double get() = items.sumOf { it.pledge.interest }
    val missingQuotes: Int get() = items.count { it.price == null }

    /** 該月份的維持率 = 該月質押市值 ÷ 該月質借金額 × 100% */
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

    /** 質押維持率 = 質押總市值 ÷ 質借總金額 × 100% */
    val ratio: Double?
        get() = if (totalAmount > 0 && missingQuotes == 0) totalMarketValue / totalAmount * 100 else null
}

/** 質押選擇清單的一檔股票 */
data class PledgeCandidate(
    val symbol: String,
    val name: String,
    /** 可質押股數：持股扣掉已質押股數後，無條件捨去到 1000 股的倍數 */
    val shares: Double,
    /** 沒有報價時為 null */
    val price: Double?,
    /** 是否為「正二」分類，「包含正二」關閉時不列入清單 */
    val leveraged: Boolean,
) {
    /** 可質押市值 = 可質押股數 × 股價 */
    val marketValue: Double? get() = price?.let { it * shares }
}

object PledgeCalculator {
    const val SAFE_RATIO = 250.0
    const val DANGER_RATIO = 175.0

    /** 質押以 1000 股（1 張）為單位 */
    const val LOT = 1000.0

    /** 質押率（質借金額 ÷ 可質押市值）上限 */
    const val MAX_LTV_PCT = 60.0

    /** 質借金額的最小單位（元） */
    const val AMOUNT_UNIT = 1000.0

    /** 新增質押時維持率的下限（%），低於無法新增 */
    const val MIN_MAINTENANCE_PCT = 166.0

    /** 可質押股數：（持股 − 已質押股數）不足 1000 股的零股不計，例如 23,211 股只算 23,000 股 */
    fun pledgeableShares(held: Double, pledged: Double): Double =
        maxOf(0.0, Math.floor((held - pledged) / LOT + 1e-9) * LOT)

    /**
     * 質押選擇清單：台股持股，扣除已質押股數後可質押股數 ≥ 1000 股者。
     * 是否排除正二由呼叫端依 leveraged 決定。
     */
    fun candidates(
        pledges: List<Pledge>,
        quotes: Map<Pair<Market, String>, Quote>,
        portfolio: PortfolioView?,
        categories: Map<Pair<Market, String>, AssetCategory> = emptyMap(),
    ): List<PledgeCandidate> =
        portfolio?.positions.orEmpty()
            .filter { it.holding.market == Market.TW && it.holding.isOpen }
            .mapNotNull { pos ->
                val symbol = pos.holding.symbol
                val pledged = pledges.filter { it.symbol == symbol }.sumOf { it.shares }
                val shares = pledgeableShares(pos.holding.shares, pledged)
                if (shares < LOT) return@mapNotNull null
                PledgeCandidate(
                    symbol = symbol,
                    name = pos.displayName,
                    shares = shares,
                    price = quotes[Market.TW to symbol]?.price,
                    leveraged = categories[Market.TW to symbol] == AssetCategory.LEVERAGED_2X,
                )
            }
            .sortedBy { it.symbol }

    /** 質借金額必須是 1000 元的倍數；合法時回傳 null，否則回傳說明 */
    fun amountError(amount: Double?): String? = when {
        amount == null -> "請輸入質借金額"
        amount <= 0 -> "質借金額需大於 0"
        amount % AMOUNT_UNIT != 0.0 ->
            "質借金額須為 1,000 的倍數（如 5,000、12,000），請重新輸入"
        else -> null
    }

    /** 質押股數必須是 1000 股的倍數，且不超過可質押股數；合法時回傳 null，否則回傳說明 */
    fun sharesError(shares: Double?, available: Double): String? = when {
        shares == null -> "請輸入質押股數"
        shares <= 0 -> "質押股數需大於 0"
        shares % LOT != 0.0 -> "質押股數須為 1,000 股的倍數（如 1,000、5,000），請重新輸入"
        shares > available -> "質押股數不可超過可質押股數 %,.0f".format(available)
        else -> null
    }

    /** 維持率 = 質押股數 × 股價 ÷ 質借金額 × 100%；缺資料時為 null */
    fun maintenancePct(shares: Double, price: Double?, amount: Double): Double? =
        if (price != null && price > 0 && shares > 0 && amount > 0) shares * price / amount * 100 else null

    /** 維持率是否低於下限（以顯示的小數兩位判斷，剛好 166.00% 可以） */
    fun belowMinMaintenance(maintenancePct: Double): Boolean =
        Math.round(maintenancePct * 100) / 100.0 < MIN_MAINTENANCE_PCT

    /** 質押率 = 質借金額 ÷ 可質押市值 × 100%；沒有市值時為 null */
    fun ltvPct(amount: Double, marketValue: Double?): Double? =
        marketValue?.takeIf { it > 0 }?.let { amount / it * 100 }

    /** 質押率是否超過上限（以顯示的小數兩位判斷，剛好 60.00% 可以） */
    fun exceedsMaxLtv(ltvPct: Double): Boolean = Math.round(ltvPct * 100) / 100.0 > MAX_LTV_PCT

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
