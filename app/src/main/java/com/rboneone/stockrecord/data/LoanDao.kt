package com.rboneone.stockrecord.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface LoanDao {
    @Query("SELECT * FROM loans ORDER BY id")
    fun observeAll(): Flow<List<Loan>>

    @Query("SELECT * FROM loans")
    suspend fun getAll(): List<Loan>

    @Query("SELECT * FROM loans WHERE id = :id")
    suspend fun get(id: Long): Loan?

    @Upsert
    suspend fun upsert(loan: Loan)

    @Delete
    suspend fun delete(loan: Loan)

    @Query("DELETE FROM loans")
    suspend fun deleteAll()

    @Insert
    suspend fun insertAll(loans: List<Loan>)
}
