package com.rboneone.stockrecord.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface AlertDao {
    @Query("SELECT * FROM price_alerts ORDER BY market, symbol, id")
    fun observeAll(): Flow<List<PriceAlert>>

    @Query("SELECT * FROM price_alerts")
    suspend fun getAll(): List<PriceAlert>

    @Query("SELECT * FROM price_alerts WHERE enabled = 1")
    suspend fun getEnabled(): List<PriceAlert>

    @Query("SELECT * FROM price_alerts WHERE id = :id")
    suspend fun get(id: Long): PriceAlert?

    @Upsert
    suspend fun upsert(alert: PriceAlert)

    @Query("UPDATE price_alerts SET lastTriggeredDay = :day WHERE id = :id")
    suspend fun markTriggered(id: Long, day: Long)

    @Delete
    suspend fun delete(alert: PriceAlert)

    @Query("DELETE FROM price_alerts")
    suspend fun deleteAll()

    @Insert
    suspend fun insertAll(alerts: List<PriceAlert>)
}
