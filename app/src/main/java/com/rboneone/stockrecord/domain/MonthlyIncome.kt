package com.rboneone.stockrecord.domain

import com.rboneone.stockrecord.data.Dividend
import com.rboneone.stockrecord.data.Market
import java.time.LocalDate
import java.time.YearMonth

/** 某個月的已實現損益與股利收入 */
data class MonthIncome(
    val month: YearMonth,
    val realized: Double,
    val dividends: Double,
) {
    val total: Double get() = realized + dividends
}

object MonthlyIncome {
    /** 最近 count 個月（含本月），沒有資料的月份為 0 */
    fun build(
        market: Market,
        realized: List<RealizedEvent>,
        dividends: List<Dividend>,
        endMonth: YearMonth = YearMonth.now(),
        count: Int = 12,
    ): List<MonthIncome> {
        fun monthOf(epochDay: Long) = YearMonth.from(LocalDate.ofEpochDay(epochDay))
        val r = realized.filter { it.market == market }.groupBy { monthOf(it.date) }
        val d = dividends.filter { it.market == market }.groupBy { monthOf(it.date) }
        return (count - 1 downTo 0).map { back ->
            val m = endMonth.minusMonths(back.toLong())
            MonthIncome(m, r[m].orEmpty().sumOf { it.pnl }, d[m].orEmpty().sumOf { it.netCash })
        }
    }

    /** 所有有資料的月份（給 Excel 用），由舊到新 */
    fun all(realized: List<RealizedEvent>, dividends: List<Dividend>): List<Pair<Market, MonthIncome>> {
        fun monthOf(epochDay: Long) = YearMonth.from(LocalDate.ofEpochDay(epochDay))
        val keys = (realized.map { it.market to monthOf(it.date) } + dividends.map { it.market to monthOf(it.date) })
            .distinct()
            .sortedWith(compareBy({ it.second }, { it.first }))
        return keys.map { (market, m) ->
            market to MonthIncome(
                m,
                realized.filter { it.market == market && monthOf(it.date) == m }.sumOf { it.pnl },
                dividends.filter { it.market == market && monthOf(it.date) == m }.sumOf { it.netCash },
            )
        }
    }
}
