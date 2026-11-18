package com.rboneone.stockrecord.domain

/** 期間最高價與出現日期（LocalDate.toEpochDay()） */
data class PeriodHigh(val price: Double, val date: Long)

/** 日線：日期、最高價、收盤價、成交量（沒有成交量資料時為 null） */
data class HighBar(val date: Long, val high: Double, val close: Double, val volume: Long?)

object HighWatch {
    /**
     * 找出歷史價格中「沒有調整到」的分割（Yahoo 剛分割完的幾天，歷史價常常只調整一部分），把分界日之前的最高價換成分割後的基準。
     *
     * 判斷：相鄰兩個交易日收盤價差了將近整數倍（≥ 1.8 倍，誤差 6% 內；台股一天最多漲跌 10%，不會這樣），
     * 而且成交量同時往反方向變動將近同樣的倍數（Yahoo 調整後的成交量會 × 倍數）。
     * 下跌＝分割（之前的價格 ÷ 倍數）、上漲＝反向分割（之前的價格 × 倍數）。
     * 回傳調整後的期間最高價（日期與價格）；沒有任何資料時回傳 null。
     */
    fun splitAdjustedHigh(bars: List<HighBar>): PeriodHigh? {
        val sorted = bars.sortedBy { it.date }
        if (sorted.isEmpty()) return null
        // factor[i]：第 i 筆之前要乘上的調整倍數（從最新往回累乘）
        val factor = DoubleArray(sorted.size) { 1.0 }
        var acc = 1.0
        for (i in sorted.size - 1 downTo 1) {
            factor[i] = acc
            val a = sorted[i - 1]; val b = sorted[i]
            val n = jumpFactor(a, b)
            if (n != null) acc *= n
        }
        factor[0] = acc
        var best: PeriodHigh? = null
        sorted.forEachIndexed { i, bar ->
            val h = bar.high * factor[i]
            if (best == null || h > best!!.price) best = PeriodHigh(h, bar.date)
        }
        return best
    }

    /** 前一天 a → 當天 b 之間若是沒調整到的分割，回傳「a 要乘上多少」（分割 1/N、反向分割 N），否則 null */
    private fun jumpFactor(a: HighBar, b: HighBar): Double? {
        if (a.close <= 0 || b.close <= 0) return null
        val down = a.close / b.close
        val up = b.close / a.close
        val isSplit = down >= 1.8
        val ratio = if (isSplit) down else up
        if (ratio < 1.8) return null
        val n = Math.round(ratio).toDouble()
        if (Math.abs(ratio - n) / n > 0.06) return null
        val va = a.volume; val vb = b.volume
        if (va != null && vb != null && va > 0 && vb > 0) {
            val volRatio = if (isSplit) vb.toDouble() / va else va.toDouble() / vb
            if (volRatio < n * 0.7) return null
        }
        return if (isSplit) 1.0 / n else n
    }

    /**
     * 顯示的最高價：網路抓到的期間最高價；盤中現價若更高（創新高）就用現價。
     * 還沒抓到最高價時回傳 null。
     */
    fun effectiveHigh(storedHigh: Double?, price: Double?): Double? =
        storedHigh?.let { if (price != null && price > it) price else it }

    /** 漲跌幅 = 現價 ÷ 最高價 × 100% */
    fun ratio(price: Double?, high: Double?): Double? =
        if (price != null && high != null && high > 0) price / high * 100 else null

    /** 技術點：P1～P5 依序是從最高點下跌 10%～50% 的價位 */
    data class DropLevel(val label: String, val dropPct: Int, val price: Double)

    val DROP_PCTS = listOf(10, 20, 30, 40, 50)

    /** 股價 = 2 年最高點 × (1 − 下跌百分比)；還沒有最高價時回傳空清單 */
    fun dropLevels(high: Double?): List<DropLevel> =
        if (high == null || high <= 0) emptyList()
        else DROP_PCTS.mapIndexed { i, pct -> DropLevel("P${i + 1}", pct, high * (100 - pct) / 100.0) }
}
