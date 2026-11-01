package com.rboneone.stockrecord.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface TradeDao {
    @Query("SELECT * FROM trades ORDER BY date DESC, id DESC")
    fun observeAll(): Flow<List<Trade>>

    @Query("SELECT * FROM trades")
    suspend fun getAll(): List<Trade>

    @Query("SELECT * FROM trades WHERE id = :id")
    suspend fun get(id: Long): Trade?

    @Upsert
    suspend fun upsert(trade: Trade)

    @Delete
    suspend fun delete(trade: Trade)

    @Query("DELETE FROM trades")
    suspend fun deleteAll()

    @Insert
    suspend fun insertAll(trades: List<Trade>)
}
