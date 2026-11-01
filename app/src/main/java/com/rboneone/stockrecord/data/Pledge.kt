package com.rboneone.stockrecord.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/** 股票質押（台股），金額皆為台幣 */
@Entity(tableName = "pledges")
data class Pledge(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val symbol: String,
    val name: String,
    /** 質押股數 */
    val shares: Double,
    /** 質押（借款）金額 */
    val amount: Double,
    /** 年利率（%），例如 2.5 代表 2.5% */
    val ratePct: Double,
    /** LocalDate.toEpochDay() */
    val date: Long,
    val note: String = "",
) {
    /** 利息支出 = 利率 × 質押金額（年） */
    val interest: Double get() = amount * ratePct / 100
}
