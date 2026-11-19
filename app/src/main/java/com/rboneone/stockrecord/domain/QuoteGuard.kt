package com.rboneone.stockrecord.domain

import com.rboneone.stockrecord.data.Market

/**
 * 分割（或反向分割）當天，報價來源的「前一日收盤價」有時還是分割前的價格，
 * 造成今日損益、漲跌幅與「漲跌」類通知出現 −80% 之類的假象。
 * 台股個股一天最多漲跌 10%，現價與前收相差將近整數倍（≥ 1.8 倍、誤差 6% 內）一定是這個問題，
 * 把前收換成和現價同一個基準。美股暴跌可能超過 45%，不自動判斷。
 */
object QuoteGuard {
    fun previousClose(market: Market, isIndex: Boolean, price: Double, previousClose: Double): Double {
        if (market != Market.TW || isIndex || price <= 0 || previousClose <= 0) return previousClose
        val down = previousClose / price
        if (down >= 1.8) return nearInteger(down)?.let { previousClose / it } ?: previousClose
        val up = price / previousClose
        if (up >= 1.8) return nearInteger(up)?.let { previousClose * it } ?: previousClose
        return previousClose
    }

    private fun nearInteger(ratio: Double): Double? {
        val n = Math.round(ratio).toDouble()
        return n.takeIf { Math.abs(ratio - n) / n <= 0.06 }
    }
}
