package com.rboneone.stockrecord.export

import com.rboneone.stockrecord.data.AssetCategory
import com.rboneone.stockrecord.data.Dividend
import com.rboneone.stockrecord.data.Market
import com.rboneone.stockrecord.data.Trade
import com.rboneone.stockrecord.domain.MonthlyIncome
import com.rboneone.stockrecord.domain.PledgeView
import com.rboneone.stockrecord.domain.Portfolio
import com.rboneone.stockrecord.domain.PortfolioView
import java.time.LocalDate

/** 把帳號資料整理成 Excel 的 6 個工作表 */
object ExcelExporter {
    private fun date(epochDay: Long) = Cell.Text(LocalDate.ofEpochDay(epochDay).toString())
    private fun text(s: String) = Cell.Text(s)
    private fun money(market: Market, v: Double) = Cell.Number(v, if (market == Market.TW) 0 else 2)
    private fun dec(v: Double) = Cell.Number(v, 2)
    private fun int(v: Double) = Cell.Number(v, 0)
    /** 股數：整股顯示整數，零股（美股小數股）才顯示小數 */
    private fun shares(v: Double) = Cell.Number(v, if (v % 1.0 == 0.0) 0 else 2)
    private fun opt(v: Double?, make: (Double) -> Cell) = v?.let(make) ?: Cell.Empty

    fun buildSheets(
        trades: List<Trade>,
        dividends: List<Dividend>,
        portfolio: PortfolioView?,
        categories: Map<Pair<Market, String>, AssetCategory>,
        pledges: PledgeView?,
    ): List<Sheet> {
        val realized = Portfolio.realizedEvents(trades, dividends)
        return listOf(
            Sheet(
                "交易紀錄",
                listOf("日期", "市場", "代號", "名稱", "買賣", "股數", "成交價", "成交金額", "手續費", "交易稅", "備註"),
                trades.sortedWith(compareBy({ it.date }, { it.id })).map {
                    listOf(
                        date(it.date), text(it.market.label), text(it.symbol), text(it.name), text(it.type.label),
                        shares(it.shares), dec(it.price), money(it.market, it.amount),
                        money(it.market, it.fee), money(it.market, it.tax), text(it.note),
                    )
                },
                widths = listOf(12, 6, 10, 18, 6, 10, 10, 14, 10, 10, 20),
            ),
            Sheet(
                "持股",
                listOf(
                    "市場", "代號", "名稱", "分類", "股數", "買均", "成本均", "總成本", "現價", "市值",
                    "未實現損益", "總損益（含股息）", "報酬率%", "已實現損益", "股息收入",
                ),
                portfolio?.positions.orEmpty().filter { it.holding.isOpen }.map { p ->
                    val h = p.holding
                    listOf(
                        text(h.market.label), text(h.symbol), text(p.displayName),
                        text(categories[h.market to h.symbol]?.label ?: "未分類"),
                        shares(h.shares), dec(h.avgCost), dec(h.adjustedAvgCost), money(h.market, h.totalCost),
                        opt(p.quote?.price) { dec(it) }, opt(p.marketValue) { money(h.market, it) },
                        opt(p.unrealizedPnl) { money(h.market, it) }, opt(p.totalPnl) { money(h.market, it) },
                        opt(p.totalPnlPct) { dec(it) }, money(h.market, h.realizedPnl), money(h.market, h.dividendIncome),
                    )
                },
                widths = listOf(6, 10, 18, 8, 10, 10, 10, 14, 10, 14, 14, 16, 10, 14, 12),
            ),
            Sheet(
                "股利",
                listOf("發放日", "市場", "代號", "名稱", "現金股利", "扣稅／健保", "匯費", "實際入帳", "配股股數", "備註"),
                dividends.sortedBy { it.date }.map {
                    listOf(
                        date(it.date), text(it.market.label), text(it.symbol), text(it.name),
                        money(it.market, it.cashAmount), money(it.market, it.tax), money(it.market, it.fee),
                        money(it.market, it.netCash), shares(it.stockShares), text(it.note),
                    )
                },
                widths = listOf(12, 6, 10, 18, 12, 12, 8, 12, 10, 20),
            ),
            Sheet(
                "已實現損益",
                listOf("賣出日期", "市場", "代號", "名稱", "股數", "賣出價", "賣出成本", "已實現損益"),
                realized.map {
                    listOf(
                        date(it.date), text(it.market.label), text(it.symbol), text(it.name),
                        shares(it.shares), dec(it.price), money(it.market, it.cost), money(it.market, it.pnl),
                    )
                },
                widths = listOf(12, 6, 10, 18, 10, 10, 14, 14),
            ),
            Sheet(
                "每月損益",
                listOf("月份", "市場", "已實現損益", "股利收入", "合計"),
                MonthlyIncome.all(realized, dividends).map { (market, m) ->
                    listOf(
                        text("%d-%02d".format(m.month.year, m.month.monthValue)), text(market.label),
                        money(market, m.realized), money(market, m.dividends), money(market, m.total),
                    )
                },
                widths = listOf(10, 6, 14, 14, 14),
            ),
            Sheet(
                "質押",
                listOf("日期", "代號", "名稱", "質押股數", "質押金額", "利率%", "利息支出（年）", "現價", "質押市值", "維持率%"),
                pledges?.items.orEmpty().map { item ->
                    val p = item.pledge
                    listOf(
                        date(p.date), text(p.symbol), text(p.name), shares(p.shares), int(p.amount), dec(p.ratePct),
                        int(p.interest), opt(item.price) { dec(it) }, opt(item.marketValue) { int(it) },
                        opt(item.ratio) { dec(it) },
                    )
                },
                widths = listOf(12, 10, 18, 10, 14, 8, 14, 10, 14, 10),
            ),
        )
    }
}
