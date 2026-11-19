package com.rboneone.stockrecord.domain

import com.rboneone.stockrecord.data.AlertType
import com.rboneone.stockrecord.data.Dividend
import com.rboneone.stockrecord.data.Pledge
import com.rboneone.stockrecord.data.PriceAlert
import com.rboneone.stockrecord.data.StockSplit
import com.rboneone.stockrecord.data.Trade

/**
 * 股票分割的資料調整。分割（forward）：股數 × 倍數、每股價格 ÷ 倍數，金額不變；
 * 還原（reverse）則相反。只調整「恢復交易日之前」的歷史資料，恢復交易日（含）之後的紀錄本來就是分割後的。
 */
object SplitMath {
    const val MIN_RATIO = 2
    const val MAX_RATIO = 1000

    /** 股數取到小數 6 位，避免除法留下浮點誤差 */
    private fun shares(v: Double) = Math.round(v * 1_000_000) / 1_000_000.0

    /** 設定檢查；合法時回傳 null，否則回傳錯誤說明 */
    fun validate(suspendDate: Long, resumeDate: Long, ratio: Int?): String? = when {
        suspendDate >= resumeDate -> "設定錯誤：暫停交易日必須早於恢復交易日"
        ratio == null || ratio < MIN_RATIO || ratio > MAX_RATIO ->
            "每股拆分請輸入 $MIN_RATIO 以上的整數（例如 1 分 5，請輸入 5）"
        else -> null
    }

    /** 是否該執行了：還沒執行，而且今天已經到（或過了）恢復交易日 */
    fun isDue(split: StockSplit, today: Long): Boolean = !split.applied && today >= split.resumeDate

    private fun affects(split: StockSplit, market: com.rboneone.stockrecord.data.Market, symbol: String, date: Long) =
        split.market == market && split.symbol == symbol && date < split.resumeDate

    fun adjustTrade(t: Trade, split: StockSplit, forward: Boolean): Trade {
        if (!affects(split, t.market, t.symbol, t.date)) return t
        return if (forward) t.copy(shares = shares(t.shares * split.ratio), price = t.price / split.ratio)
        else t.copy(shares = shares(t.shares / split.ratio), price = t.price * split.ratio)
    }

    /** 股利：配股股數跟著調整、每股股利反向調整，現金股利總額不變 */
    fun adjustDividend(d: Dividend, split: StockSplit, forward: Boolean): Dividend {
        if (!affects(split, d.market, d.symbol, d.date)) return d
        return if (forward) d.copy(stockShares = shares(d.stockShares * split.ratio), perShare = d.perShare?.let { it / split.ratio })
        else d.copy(stockShares = shares(d.stockShares / split.ratio), perShare = d.perShare?.let { it * split.ratio })
    }

    /** 質押（台股）：質押股數調整，質借金額不變 */
    fun adjustPledge(p: Pledge, split: StockSplit, forward: Boolean): Pledge {
        if (split.market != com.rboneone.stockrecord.data.Market.TW || p.symbol != split.symbol || p.date >= split.resumeDate) return p
        return if (forward) p.copy(shares = shares(p.shares * split.ratio)) else p.copy(shares = shares(p.shares / split.ratio))
    }

    /**
     * 已執行的分割，判斷一筆質押的股數是不是沒跟著調整（+1＝還是分割前、要 × 倍數；−1＝被多調整了、要 ÷ 倍數；0＝正常或無法判斷）。
     * - [heldAtDate]：質押當天持有的股數（分割後的基準）；質押股數不可能比持股多。
     * - [closeAtDate]：質押當天的收盤價（分割後的基準）；質押當下維持率至少 166%，
     *   質押股數 × 收盤價 ÷ 質借金額 遠低於這個值，代表股數還是分割前的。
     */
    fun pledgeAdjustment(p: Pledge, split: StockSplit, heldAtDate: Double, closeAtDate: Double?): Int {
        if (split.market != com.rboneone.stockrecord.data.Market.TW || p.symbol != split.symbol || p.date >= split.resumeDate) return 0
        val n = split.ratio.toDouble()
        if (p.shares > heldAtDate + 1e-6 && p.shares / n <= heldAtDate + 1e-6) return -1
        if (closeAtDate != null && closeAtDate > 0 && p.amount > 0 && p.shares * n <= heldAtDate + 1e-6) {
            val maintenance = p.shares * closeAtDate / p.amount
            if (maintenance < 1.66 / Math.sqrt(n)) return 1
        }
        return 0
    }

    /** 通知：股價、漲跌金額的門檻跟著每股價格調整；百分比的門檻不用動 */
    fun adjustAlert(a: PriceAlert, split: StockSplit, forward: Boolean): PriceAlert {
        if (a.market != split.market || a.symbol != split.symbol) return a
        val perShare = a.type == AlertType.PRICE_ABOVE || a.type == AlertType.PRICE_BELOW ||
            a.type == AlertType.RISE_AMOUNT || a.type == AlertType.FALL_AMOUNT
        if (!perShare) return a
        return if (forward) a.copy(threshold = a.threshold / split.ratio) else a.copy(threshold = a.threshold * split.ratio)
    }

    /**
     * 找出 Yahoo 歷史收盤價「沒調整到的部分」的分界日。
     *
     * 分割後 Yahoo 會把歷史價往回調整（價格 ÷ 倍數），但剛分割時常常只調整到一部分（例如只調整了最近幾天），
     * 更早的日子還是分割前的原價——同一份資料裡價格水準會突然掉一個倍數。
     * 這裡在 [bars]（含恢復交易日前後）裡找「連續兩個交易日收盤價相差約一個倍數」的地方，
     * 回傳這次下跌的那一天（分界日）：分界日之前的價格還沒調整。找不到回傳 null（整份都已調整、或整份都沒有分割後的價格）。
     * 一般交易日的漲跌幅不會超過倍數的平方根倍，所以不會把正常的大跌當成分界。
     */
    fun unadjustedBefore(bars: List<Bar>, split: StockSplit): Long? {
        val sorted = bars.sortedBy { it.date }
        val threshold = Math.sqrt(split.ratio.toDouble())
        var found: Long? = null
        for (i in 1 until sorted.size) {
            val a = sorted[i - 1]
            val b = sorted[i]
            if (b.close > 0 && a.close / b.close >= threshold) found = b.date
        }
        return found
    }

    /**
     * 把歷史收盤價換成和（已執行分割的）交易紀錄同一個基準：
     * 股數已經 × 倍數，所以還沒調整的價格（[unadjustedBefore] 之前的日子）也要 ÷ 倍數，市值才算得對。
     */
    fun alignBars(bars: List<Bar>, ratio: Int, unadjustedBefore: Long): List<Bar> =
        bars.map { if (it.date < unadjustedBefore) it.copy(close = it.close / ratio) else it }

    /**
     * 已執行的分割，找出「沒有調整到」的買賣（還原備份、補登分割前的買賣之後常見）：
     * 把每筆恢復交易日之前的買賣價格，和「同一天的市場收盤價」比——[bars] 要是已經和分割後對齊的價格（見 [alignBars]）。
     * 買賣價格不會離當天收盤價太遠，所以：價格接近當天收盤價 → 已調整；價格是當天收盤價的將近一個倍數
     * （超過倍數的平方根倍）→ 還是分割前的原價，沒有調整到。找不到那天（或之前）的收盤價的買賣，不判斷。
     */
    fun unadjustedTrades(trades: List<Trade>, bars: List<Bar>, split: StockSplit): List<Trade> {
        val sorted = bars.sortedBy { it.date }
        val threshold = Math.sqrt(split.ratio.toDouble())
        return trades.filter { t ->
            if (t.market != split.market || t.symbol != split.symbol || t.date >= split.resumeDate || t.price <= 0) return@filter false
            val ref = sorted.lastOrNull { it.date <= t.date }?.close?.takeIf { it > 0 } ?: return@filter false
            t.price / ref >= threshold
        }
    }

    /** 這檔股票在這個恢復交易日是不是已經有分割設定（再設定一次會把股數重複調整） */
    fun isDuplicate(existing: List<StockSplit>, candidate: StockSplit): Boolean =
        existing.any { it.id != candidate.id && it.market == candidate.market && it.symbol == candidate.symbol && it.resumeDate == candidate.resumeDate }

    /** 同一次分割（暫停日可能不同，但市場、代號、恢復交易日、倍數一樣就當作同一筆） */
    private fun sameSplit(a: StockSplit, b: StockSplit) =
        a.market == b.market && a.symbol == b.symbol && a.resumeDate == b.resumeDate && a.ratio == b.ratio

    /**
     * 還原備份時，手機上「備份裡沒有」的分割要保留下來。
     * 備份比這筆分割的設定更早做的話，備份裡的買賣還是分割前的股數與價格，所以這些分割要再執行一次：
     * 回傳要補進去的分割（id 歸零、applied = false），之後由到期檢查自動調整。
     * 備份裡已經有的分割不動：applied 為 false 的會自己到期執行，applied 為 true 代表備份裡的買賣本來就是分割後的。
     */
    fun splitsToCarryOver(deviceSplits: List<StockSplit>, backupSplits: List<StockSplit>): List<StockSplit> =
        deviceSplits.filter { d -> backupSplits.none { sameSplit(it, d) } }.map { it.copy(id = 0, applied = false) }
}
