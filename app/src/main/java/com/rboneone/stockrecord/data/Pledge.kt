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
    /** 歸還日 LocalDate.toEpochDay()；null 表示還在質押中 */
    val returnDate: Long? = null,
) {
    /** 利息支出 = 利率 × 質借金額（年） */
    val interest: Double get() = amount * ratePct / 100

    /** 還在質押中（沒有歸還） */
    val isActive: Boolean get() = returnDate == null

    /** 質押天數：質押日到 [end]（歸還日）的日數，質押當天歸還為 0 天 */
    fun daysTo(end: Long): Long = (end - date).coerceAtLeast(0)

    /** 質押 [days] 天的利息 = 質借金額 × 年利率 × 天數 ÷ 365，四捨五入到整數（元） */
    fun interestForDays(days: Long): Long = Math.round(amount * ratePct / 100 * days / DAYS_IN_YEAR)

    /** 歸還時的質押天數；還沒歸還為 null */
    val returnDays: Long? get() = returnDate?.let { daysTo(it) }

    /** 歸還時要付的利息（元）；還沒歸還為 null */
    val returnInterest: Long? get() = returnDays?.let { interestForDays(it) }

    companion object {
        /** 利息的年天數 */
        const val DAYS_IN_YEAR = 365.0
    }
}
