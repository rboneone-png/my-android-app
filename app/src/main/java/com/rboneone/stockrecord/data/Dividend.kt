package com.rboneone.stockrecord.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "dividends")
data class Dividend(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val market: Market,
    val symbol: String,
    val name: String,
    /** 發放日 LocalDate.toEpochDay() */
    val date: Long,
    /** 現金股利總額（稅前） */
    val cashAmount: Double,
    /** 代扣稅款：台股二代健保補充保費、美股預扣稅 */
    val tax: Double,
    /** 匯費等手續費 */
    val fee: Double,
    /** 股票股利配發的股數 */
    val stockShares: Double,
    val note: String = "",
) {
    /** 實際入帳的現金 */
    val netCash: Double get() = cashAmount - tax - fee
}
