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
) {
    val amount: Double get() = shares * price
}
