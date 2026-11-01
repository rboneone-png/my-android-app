package com.rboneone.stockrecord.data

import androidx.room.Entity

/** 資產配置分類；曝險倍數用來計算曝險 */
enum class AssetCategory(val label: String, val exposure: Double) {
    CORE("原型", 1.0),
    LEVERAGED_2X("正二", 2.0),
    CASH_LIKE("類現金", 0.0),
    OTHER("其他", 0.0),
}

/** 每檔股票一個分類，同一檔股票的所有交易共用 */
@Entity(tableName = "stock_category", primaryKeys = ["market", "symbol"])
data class StockCategory(
    val market: Market,
    val symbol: String,
    val category: AssetCategory,
)
