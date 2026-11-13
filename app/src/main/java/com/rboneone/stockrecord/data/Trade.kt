package com.rboneone.stockrecord.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "trades")
data class Trade(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val market: Market,
    val symbol: String,
    val name: String,
    val type: TradeType,
    /** LocalDate.toEpochDay() */
    val date: Long,
    val shares: Double,
    val price: Double,
    val fee: Double,
    val tax: Double,
    val taxType: TaxType = TaxType.STOCK,
    val note: String = "",
    /** 美股：交易當時的美元兌台幣匯率（帳務管理換算台幣用）；台股為 null。舊紀錄補上前也是 null */
    val fxRate: Double? = null,
) {
    val amount: Double get() = shares * price
}
