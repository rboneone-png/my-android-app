package com.rboneone.stockrecord.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/** 觀察中的股票（不一定有持有） */
@Entity(tableName = "watchlist")
data class WatchItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val market: Market,
    val symbol: String,
    val name: String,
    /** 使用者自訂的排列位置，越小越前面 */
    val position: Int,
)
