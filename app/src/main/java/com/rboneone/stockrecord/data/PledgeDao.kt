package com.rboneone.stockrecord.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface PledgeDao {
    @Query("SELECT * FROM pledges ORDER BY date DESC, id DESC")
    fun observeAll(): Flow<List<Pledge>>

    @Query("SELECT * FROM pledges")
    suspend fun getAll(): List<Pledge>

    @Query("SELECT * FROM pledges WHERE id = :id")
    suspend fun get(id: Long): Pledge?

    @Upsert
    suspend fun upsert(pledge: Pledge)

    @Delete
    suspend fun delete(pledge: Pledge)

    @Query("DELETE FROM pledges")
    suspend fun deleteAll()

    @Insert
    suspend fun insertAll(pledges: List<Pledge>)
}
