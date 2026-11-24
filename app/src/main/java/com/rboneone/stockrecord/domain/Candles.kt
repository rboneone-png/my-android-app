package com.rboneone.stockrecord.domain

/** 一根日 K：日期（LocalDate.toEpochDay()）、開高低收、成交量（股） */
data class Candle(val date: Long, val open: Double, val high: Double, val low: Double, val close: Double, val volume: Long)

/** 走勢圖用的技術指標與資料整理 */
object Indicators {
    /** 簡單移動平均：前 n−1 筆不足 n 筆，為 null */
    fun sma(values: List<Double>, n: Int): List<Double?> {
        val out = ArrayList<Double?>(values.size)
        var sum = 0.0
        for (i in values.indices) {
            sum += values[i]
            if (i >= n) sum -= values[i - n]
            out += if (i >= n - 1) sum / n else null
        }
        return out
    }

    /** RSI（Wilder 平滑，預設 14 日）：前 n 筆不足，為 null；0～100 */
    fun rsi(closes: List<Double>, n: Int = 14): List<Double?> {
        val out = MutableList<Double?>(closes.size) { null }
        if (closes.size <= n) return out
        var gain = 0.0
        var loss = 0.0
        for (i in 1..n) {
            val d = closes[i] - closes[i - 1]
            if (d > 0) gain += d else loss -= d
        }
        gain /= n; loss /= n
        fun value() = if (loss == 0.0) (if (gain == 0.0) 50.0 else 100.0) else 100 - 100 / (1 + gain / loss)
        out[n] = value()
        for (i in n + 1 until closes.size) {
            val d = closes[i] - closes[i - 1]
            gain = (gain * (n - 1) + maxOf(d, 0.0)) / n
            loss = (loss * (n - 1) + maxOf(-d, 0.0)) / n
            out[i] = value()
        }
        return out
    }

    /**
     * 剛分割完時 Yahoo 的歷史價可能只調整一部分：把沒調整到的舊價格與成交量換成分割後的基準
     * （辨識方式同 [HighWatch.splitAdjustedHigh]）。
     */
    fun splitAdjusted(candles: List<Candle>): List<Candle> {
        val sorted = candles.sortedBy { it.date }
        val f = HighWatch.splitFactors(sorted.map { HighBar(it.date, it.high, it.close, it.volume) })
        return sorted.mapIndexed { i, c ->
            if (f[i] == 1.0) c
            else c.copy(open = c.open * f[i], high = c.high * f[i], low = c.low * f[i], close = c.close * f[i],
                volume = Math.round(c.volume / f[i]))
        }
    }
}
