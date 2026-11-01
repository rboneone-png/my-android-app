package com.rboneone.stockrecord.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/** 「股票漲跌」清單：比較現價與近 2 年的最高價 */
@Entity(tableName = "high_watch")
data class HighWatchItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val market: Market,
    val symbol: String,
    val name: String,
    /** 使用者自訂的排列位置，越小越前面 */
    val position: Int,
    /** 從網路抓到的近 2 年最高價（快取，離線時顯示） */
    val highPrice: Double? = null,
    /** 最高價出現的日期，LocalDate.toEpochDay() */
    val highDate: Long? = null,
    /** 上次抓最高價的時間（毫秒），0 代表還沒抓過 */
    val highUpdatedAt: Long = 0,
) {
    companion object {
        /** 最高價的期間：近 2 年 */
        const val PERIOD_YEARS = 2
    }
}
