package com.rboneone.stockrecord.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface HighWatchDao {
    @Query("SELECT * FROM high_watch ORDER BY position, id")
    fun observeAll(): Flow<List<HighWatchItem>>

    @Query("SELECT * FROM high_watch ORDER BY position, id")
    suspend fun getAll(): List<HighWatchItem>

    @Query("SELECT * FROM high_watch WHERE id = :id")
    suspend fun get(id: Long): HighWatchItem?

    @Query("SELECT COALESCE(MAX(position), -1) FROM high_watch")
    suspend fun maxPosition(): Int

    @Query("SELECT COUNT(*) FROM high_watch WHERE market = :market AND symbol = :symbol")
    suspend fun count(market: Market, symbol: String): Int

    @Insert
    suspend fun insert(item: HighWatchItem): Long

    @Update
    suspend fun update(item: HighWatchItem)

    @Update
    suspend fun updateAll(items: List<HighWatchItem>)

    @Query("UPDATE high_watch SET highPrice = :high, highDate = :date, highUpdatedAt = :updatedAt WHERE id = :id")
    suspend fun updateHigh(id: Long, high: Double, date: Long, updatedAt: Long)

    @Delete
    suspend fun delete(item: HighWatchItem)

    @Query("DELETE FROM high_watch")
    suspend fun deleteAll()

    @Insert
    suspend fun insertAll(items: List<HighWatchItem>)

    /** 依傳入的順序重新編號 */
    @Transaction
    suspend fun reorder(ids: List<Long>) {
        val byId = getAll().associateBy { it.id }
        updateAll(ids.mapIndexedNotNull { i, id -> byId[id]?.copy(position = i) })
    }
}
