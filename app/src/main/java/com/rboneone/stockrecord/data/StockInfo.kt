package com.rboneone.stockrecord.data

import androidx.room.Entity

/** 查詢過的股票資料（名稱、Yahoo 代號），存起來下次不用再查 */
@Entity(tableName = "stock_info", primaryKeys = ["market", "symbol"])
data class StockInfo(
    val market: Market,
    val symbol: String,
    val name: String,
    /** Yahoo Finance 代號，例如 2330.TW、6488.TWO、BRK-B */
    val yahooSymbol: String,
    val updatedAt: Long,
)
