package com.rboneone.stockrecord.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface MarketDao {
    @Query("SELECT * FROM stock_info WHERE market = :market AND symbol = :symbol")
    suspend fun getInfo(market: Market, symbol: String): StockInfo?

    @Query("SELECT * FROM stock_info")
    fun observeInfo(): Flow<List<StockInfo>>

    @Upsert
    suspend fun upsertInfo(info: StockInfo)

    @Query("SELECT * FROM quotes")
    fun observeQuotes(): Flow<List<Quote>>

    @Query("SELECT * FROM quotes")
    suspend fun getQuotes(): List<Quote>

    @Upsert
    suspend fun upsertQuote(quote: Quote)
}
