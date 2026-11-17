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

    /** 通知：股價、漲跌金額的門檻跟著每股價格調整；百分比的門檻不用動 */
    fun adjustAlert(a: PriceAlert, split: StockSplit, forward: Boolean): PriceAlert {
        if (a.market != split.market || a.symbol != split.symbol) return a
        val perShare = a.type == AlertType.PRICE_ABOVE || a.type == AlertType.PRICE_BELOW ||
            a.type == AlertType.RISE_AMOUNT || a.type == AlertType.FALL_AMOUNT
        if (!perShare) return a
        return if (forward) a.copy(threshold = a.threshold / split.ratio) else a.copy(threshold = a.threshold * split.ratio)
    }

    /**
     * Yahoo 的歷史收盤價是不是已經依這次分割調整過。
     * 分割後 Yahoo 會把「恢復交易日之前」的歷史價往回調整（價格 ÷ 倍數），但剛分割的前幾天可能還沒調整。
     * 比較恢復交易日前最後一天的收盤價 [preClose]（Yahoo 給的）和恢復交易日當天或之後的價格 [postPrice]：
     * 已調整的話兩者相近（一般漲跌不會超過倍數的平方根倍）；沒調整的話前者大約是後者的「倍數」倍。
     */
    fun barsAdjusted(preClose: Double, postPrice: Double, ratio: Int): Boolean =
        preClose <= 0 || postPrice <= 0 || preClose / postPrice < Math.sqrt(ratio.toDouble())

    /**
     * 把歷史收盤價換成和（已執行分割的）交易紀錄同一個基準：
     * 股數已經 × 倍數的話，恢復交易日之前的價格也要是 ÷ 倍數之後的價格，市值才算得對。
     * Yahoo 已經調整過（[adjusted] 為 true）就不用動。
     */
    fun alignBars(bars: List<Bar>, split: StockSplit, adjusted: Boolean): List<Bar> =
        if (adjusted) bars else bars.map { if (it.date < split.resumeDate) it.copy(close = it.close / split.ratio) else it }
}
