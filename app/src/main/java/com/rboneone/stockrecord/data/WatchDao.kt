package com.rboneone.stockrecord.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface WatchDao {
    @Query("SELECT * FROM watchlist ORDER BY position, id")
    fun observeAll(): Flow<List<WatchItem>>

    @Query("SELECT * FROM watchlist ORDER BY position, id")
    suspend fun getAll(): List<WatchItem>

    @Query("SELECT COALESCE(MAX(position), -1) FROM watchlist")
    suspend fun maxPosition(): Int

    @Query("SELECT COUNT(*) FROM watchlist WHERE market = :market AND symbol = :symbol")
    suspend fun count(market: Market, symbol: String): Int

    @Insert
    suspend fun insert(item: WatchItem)

    @Update
    suspend fun updateAll(items: List<WatchItem>)

    @Delete
    suspend fun delete(item: WatchItem)

    @Query("DELETE FROM watchlist")
    suspend fun deleteAll()

    @Insert
    suspend fun insertAll(items: List<WatchItem>)

    /** 依傳入的順序重新編號 */
    @Transaction
    suspend fun reorder(ids: List<Long>) {
        val byId = getAll().associateBy { it.id }
        updateAll(ids.mapIndexedNotNull { i, id -> byId[id]?.copy(position = i) })
    }
}
