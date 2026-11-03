package com.rboneone.stockrecord.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface CashFlowDao {
    @Query("SELECT * FROM cash_flows ORDER BY date DESC, id DESC")
    fun observeAll(): Flow<List<CashFlow>>

    @Query("SELECT * FROM cash_flows")
    suspend fun getAll(): List<CashFlow>

    @Query("SELECT * FROM cash_flows WHERE id = :id")
    suspend fun get(id: Long): CashFlow?

    @Upsert
    suspend fun upsert(flow: CashFlow)

    @Delete
    suspend fun delete(flow: CashFlow)

    @Query("DELETE FROM cash_flows")
    suspend fun deleteAll()

    @Insert
    suspend fun insertAll(flows: List<CashFlow>)
}
