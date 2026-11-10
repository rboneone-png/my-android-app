package com.rboneone.stockrecord.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 股票分割（例如 1 股分成 5 股）。到了 [resumeDate]（恢復交易日）時，App 會自動把這檔股票的
 * 歷史買賣、股利、質押與通知設定調整成分割後的股數與價格，並把 [applied] 設為 true。
 */
@Entity(tableName = "stock_splits")
data class StockSplit(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val market: Market,
    val symbol: String,
    val name: String,
    /** 暫停交易日 LocalDate.toEpochDay()，必須早於恢復交易日 */
    val suspendDate: Long,
    /** 恢復交易日 LocalDate.toEpochDay()：到這一天自動做分割調整 */
    val resumeDate: Long,
    /** 每股拆分：1 股變成幾股（1 分 5 就是 5） */
    val ratio: Int,
    /** 是否已經執行過分割調整 */
    val applied: Boolean = false,
)
