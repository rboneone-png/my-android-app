package com.rboneone.stockrecord.data

import com.rboneone.stockrecord.domain.SplitMath
import com.rboneone.stockrecord.network.YahooApi

/**
 * 自動修復「分割已執行，但買賣還是分割前的股數與價格」（還原備份、補登分割前的買賣之後會發生）。
 *
 * 做法：對每一筆已執行的分割，抓那檔股票的歷史收盤價（Yahoo 沒調整到的部分先自己對齊），
 * 把恢復交易日之前的每筆買賣價格，和「同一天的收盤價」比較：
 * - 價格接近當天收盤價 → 已經調整過，不動
 * - 價格是當天收盤價的將近一個倍數 → 還是分割前的原價，調整這一筆
 * 所有買賣都還是原價的話，整筆分割（買賣、股利、通知價格）重新執行一次；質押股數另外逐筆判斷（見 repairPledges）。
 * 抓不到歷史價（沒網路）或找不到那天的收盤價時，什麼都不改，下次再檢查。重複執行不會重複調整。
 */
object SplitRepair {
    /** 回傳修復了幾筆買賣 */
    suspend fun run(db: AppDatabase, today: Long): Int {
        var fixed = 0
        val splits = db.stockSplitDao().getAll().filter { it.applied }
        for (sp in splits) {
            val before = db.tradeDao().getAll()
                .filter { it.market == sp.market && it.symbol == sp.symbol && it.date < sp.resumeDate }
            if (before.isEmpty()) continue
            val yahoo = db.marketDao().getInfo(sp.market, sp.symbol)?.yahooSymbol ?: continue
            val bars = runCatching {
                YahooApi.history(yahoo, before.minOf { it.date } - 10, minOf(today, sp.resumeDate + 7))
            }.getOrNull()?.takeIf { it.isNotEmpty() } ?: continue
            val jump = SplitMath.unadjustedBefore(bars, sp)
            val aligned = if (jump != null) SplitMath.alignBars(bars, sp.ratio, jump) else bars
            val raw = SplitMath.unadjustedTrades(before, aligned, sp)
            if (raw.isEmpty()) continue
            if (raw.size == before.size) {
                // 全部都還是原價：整筆分割重新執行（連同股利、質押股數、通知價格）
                SplitApplier.reapply(db, sp)
            } else {
                raw.forEach { db.tradeDao().upsert(SplitMath.adjustTrade(it, sp, forward = true)) }
            }
            fixed += raw.size
        }
        // 買賣修好之後再判斷質押（要用調整後的持股數）
        for (sp in splits) fixed += repairPledges(db, sp, today)
        return fixed
    }

    /**
     * 質押股數沒跟著分割調整（或被多調整）時逐筆修正。判斷方式見 [SplitMath.pledgeAdjustment]。
     * 回傳修正了幾筆。
     */
    private suspend fun repairPledges(db: AppDatabase, sp: StockSplit, today: Long): Int {
        if (sp.market != Market.TW) return 0
        val pledges = db.pledgeDao().getAll().filter { it.symbol == sp.symbol && it.date < sp.resumeDate }
        if (pledges.isEmpty()) return 0
        val trades = db.tradeDao().getAll().filter { it.market == sp.market && it.symbol == sp.symbol }
        val dividends = db.dividendDao().getAll().filter { it.market == sp.market && it.symbol == sp.symbol }
        // 質押日期可能比實際買進日早一點，所以用「歷來持股最多的時候」當上限：質押股數不可能超過它
        val events = trades.map { it.date to (if (it.type == TradeType.BUY) it.shares else -it.shares) } +
            dividends.filter { it.stockShares != 0.0 }.map { it.date to it.stockShares }
        var run = 0.0
        var peak = 0.0
        events.sortedBy { it.first }.forEach { (_, q) -> run += q; if (run > peak) peak = run }
        // 收盤價只用來判斷「沒調整到」；抓不到（沒網路）時只做「質押股數超過持股」的判斷
        val yahoo = db.marketDao().getInfo(sp.market, sp.symbol)?.yahooSymbol
        val bars = yahoo?.let {
            runCatching { YahooApi.history(it, pledges.minOf { p -> p.date } - 10, minOf(today, sp.resumeDate + 7)) }.getOrNull()
        }?.takeIf { it.isNotEmpty() }
        val aligned = bars?.let { b -> SplitMath.unadjustedBefore(b, sp)?.let { SplitMath.alignBars(b, sp.ratio, it) } ?: b }
        var fixed = 0
        for (p in pledges) {
            val close = aligned?.lastOrNull { it.date <= p.date }?.close
            when (SplitMath.pledgeAdjustment(p, sp, peak, close)) {
                1 -> { db.pledgeDao().upsert(SplitMath.adjustPledge(p, sp, forward = true)); fixed++ }
                -1 -> { db.pledgeDao().upsert(SplitMath.adjustPledge(p, sp, forward = false)); fixed++ }
            }
        }
        return fixed
    }
}
