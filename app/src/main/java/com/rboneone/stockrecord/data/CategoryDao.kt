package com.rboneone.stockrecord.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {
    @Query("SELECT * FROM stock_category")
    fun observeAll(): Flow<List<StockCategory>>

    @Query("SELECT * FROM stock_category")
    suspend fun getAll(): List<StockCategory>

    @Query("SELECT * FROM stock_category WHERE market = :market AND symbol = :symbol")
    suspend fun get(market: Market, symbol: String): StockCategory?

    @Upsert
    suspend fun upsert(category: StockCategory)

    @Query("DELETE FROM stock_category WHERE market = :market AND symbol = :symbol")
    suspend fun delete(market: Market, symbol: String)

    @Query("DELETE FROM stock_category")
    suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(categories: List<StockCategory>)
}
