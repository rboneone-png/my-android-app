package com.rboneone.stockrecord.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface DividendDao {
    @Query("SELECT * FROM dividends ORDER BY date DESC, id DESC")
    fun observeAll(): Flow<List<Dividend>>

    @Query("SELECT * FROM dividends")
    suspend fun getAll(): List<Dividend>

    @Query("SELECT * FROM dividends WHERE id = :id")
    suspend fun get(id: Long): Dividend?

    @Upsert
    suspend fun upsert(dividend: Dividend)

    @Delete
    suspend fun delete(dividend: Dividend)

    @Query("DELETE FROM dividends")
    suspend fun deleteAll()

    @Insert
    suspend fun insertAll(dividends: List<Dividend>)
}
