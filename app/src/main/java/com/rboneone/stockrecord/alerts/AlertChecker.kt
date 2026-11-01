package com.rboneone.stockrecord.alerts

import com.rboneone.stockrecord.StockApp
import com.rboneone.stockrecord.data.Market
import com.rboneone.stockrecord.data.Pledge
import com.rboneone.stockrecord.data.Quote
import com.rboneone.stockrecord.domain.PledgeCalculator
import com.rboneone.stockrecord.domain.PledgeLevel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** 檢查股價通知條件，符合時發出通知；同一條件每個交易日只通知一次 */
class AlertChecker(private val app: StockApp) {

    /** refresh = true 時先抓最新報價（背景檢查用）；App 內已經刷新過時傳 false */
    suspend fun check(userId: String, refresh: Boolean): Int {
        val db = app.database(userId)
        val alerts = db.alertDao().getEnabled()
        val pledges = db.pledgeDao().getAll()
        if (alerts.isEmpty() && pledges.isEmpty()) return 0
        if (refresh) {
            val stocks = alerts.map { it.market to it.symbol } + pledges.map { Market.TW to it.symbol }
            app.marketRepository(userId).refreshQuotes(stocks.distinct())
        }
        val quotes = db.marketDao().getQuotes().associateBy { it.market to it.symbol }
        val multiUser = app.accountStore.accounts.value.size > 1
        val username = app.accountStore.find(userId)?.username
        var count = 0
        for (alert in alerts) {
            val q = quotes[alert.market to alert.symbol] ?: continue
            val day = tradingDay(q)
            if (alert.lastTriggeredDay == day) continue
            if (!alert.isTriggered(q.price, q.previousClose)) continue
            val change = q.price - q.previousClose
            val pct = change / q.previousClose * 100
            val sign = if (change > 0) "+" else ""
            val title = "${alert.name} ${alert.symbol} ${alert.conditionText()}"
            val text = buildString {
                append("現價 ${"%,.2f".format(q.price)}，漲跌 $sign${"%,.2f".format(change)}（$sign${"%.2f".format(pct)}%）")
                if (multiUser && username != null) append("\n帳號：$username")
            }
            AlertNotifier.notify(app, "$userId/${alert.id}".hashCode(), title, text)
            db.alertDao().markTriggered(alert.id, day)
            count++
        }
        if (checkPledges(userId, pledges, quotes, if (multiUser) username else null)) count++
        return count
    }

    /** 質押維持率低於 175% 時通知，每個交易日最多一次 */
    private fun checkPledges(
        userId: String,
        pledges: List<Pledge>,
        quotes: Map<Pair<Market, String>, Quote>,
        username: String?,
    ): Boolean {
        if (pledges.isEmpty()) return false
        val view = PledgeCalculator.build(pledges, quotes, portfolio = null)
        val ratio = view.ratio ?: return false
        if (PledgeCalculator.level(ratio) != PledgeLevel.DANGER) return false
        val day = pledges.mapNotNull { quotes[Market.TW to it.symbol] }.maxOfOrNull { tradingDay(it) } ?: return false
        val settings = app.settings(userId)
        if (settings.pledgeAlertDay == day) return false
        val text = buildString {
            append("目前質押維持率 ${"%.1f".format(ratio)}%，")
            append("質押總市值 ${"%,.0f".format(view.totalMarketValue)}，質押總金額 ${"%,.0f".format(view.totalAmount)}")
            if (username != null) append("\n帳號：$username")
        }
        AlertNotifier.notify(app, "$userId/pledge".hashCode(), "質押維持率低於 ${PledgeCalculator.DANGER_RATIO.toInt()}%", text)
        settings.pledgeAlertDay = day
        return true
    }

    /** 背景工作：檢查所有帳號 */
    suspend fun checkAll() {
        app.accountStore.accounts.value.forEach { runCatching { check(it.id, refresh = true) } }
    }

    companion object {
        fun zone(market: Market): ZoneId = when (market) {
            Market.TW -> ZoneId.of("Asia/Taipei")
            Market.US -> ZoneId.of("America/New_York")
        }

        /** 報價所屬的交易日（交易所當地日期） */
        fun tradingDay(q: Quote): Long =
            if (q.marketTime > 0) Instant.ofEpochSecond(q.marketTime).atZone(zone(q.market)).toLocalDate().toEpochDay()
            else LocalDate.now(zone(q.market)).toEpochDay()
    }
}
