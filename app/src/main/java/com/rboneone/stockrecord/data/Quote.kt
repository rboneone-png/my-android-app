package com.rboneone.stockrecord.data

import androidx.room.Entity

/** 最近一次抓到的報價，沒有網路時仍可顯示 */
@Entity(tableName = "quotes", primaryKeys = ["market", "symbol"])
data class Quote(
    val market: Market,
    val symbol: String,
    val price: Double,
    val previousClose: Double,
    /** 交易所報價時間（epoch 秒） */
    val marketTime: Long,
    /** 抓取時間（epoch 毫秒） */
    val updatedAt: Long,
) {
    companion object {
        /** 美元兌台幣匯率也存在 quotes 表，用 Yahoo 的代號當 key */
        const val USD_TWD = "TWD=X"
    }
}
