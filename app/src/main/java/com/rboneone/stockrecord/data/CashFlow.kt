package com.rboneone.stockrecord.data

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class CashFlowType(val label: String) {
    DEPOSIT("存入現金"),
    WITHDRAW("領出現金"),
}

/** 帳務管理的現金存入／領出紀錄，金額一律為正數，台股用台幣、美股用美元 */
@Entity(tableName = "cash_flows")
data class CashFlow(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val market: Market,
    val type: CashFlowType,
    /** LocalDate.toEpochDay() */
    val date: Long,
    val amount: Double,
    val note: String = "",
) {
    /** 對餘額的影響：存入為正、領出為負 */
    val signedAmount: Double get() = if (type == CashFlowType.DEPOSIT) amount else -amount
}
