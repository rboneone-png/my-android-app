package com.rboneone.stockrecord.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface StockSplitDao {
    @Query("SELECT * FROM stock_splits ORDER BY resumeDate DESC, id DESC")
    fun observeAll(): Flow<List<StockSplit>>

    @Query("SELECT * FROM stock_splits")
    suspend fun getAll(): List<StockSplit>

    @Query("SELECT * FROM stock_splits WHERE id = :id")
    suspend fun get(id: Long): StockSplit?

    @Upsert
    suspend fun upsert(split: StockSplit)

    @Delete
    suspend fun delete(split: StockSplit)

    @Query("DELETE FROM stock_splits")
    suspend fun deleteAll()

    @Insert
    suspend fun insertAll(splits: List<StockSplit>)
}
