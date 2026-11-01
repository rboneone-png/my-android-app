package com.rboneone.stockrecord.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Trade::class, StockInfo::class, Quote::class, Dividend::class, StockCategory::class, PriceAlert::class, Pledge::class, WatchItem::class, HighWatchItem::class],
    version = 8,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun tradeDao(): TradeDao
    abstract fun marketDao(): MarketDao
    abstract fun dividendDao(): DividendDao
    abstract fun categoryDao(): CategoryDao
    abstract fun alertDao(): AlertDao
    abstract fun pledgeDao(): PledgeDao
    abstract fun watchDao(): WatchDao
    abstract fun highWatchDao(): HighWatchDao

    companion object {
        /** v1 → v2：新增股票資料與報價表，保留原有交易紀錄 */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `stock_info` (`market` TEXT NOT NULL, `symbol` TEXT NOT NULL, " +
                        "`name` TEXT NOT NULL, `yahooSymbol` TEXT NOT NULL, `updatedAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`market`, `symbol`))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `quotes` (`market` TEXT NOT NULL, `symbol` TEXT NOT NULL, " +
                        "`price` REAL NOT NULL, `previousClose` REAL NOT NULL, `marketTime` INTEGER NOT NULL, " +
                        "`updatedAt` INTEGER NOT NULL, PRIMARY KEY(`market`, `symbol`))"
                )
            }
        }

        /** v2 → v3：新增股利紀錄表 */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `dividends` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`market` TEXT NOT NULL, `symbol` TEXT NOT NULL, `name` TEXT NOT NULL, `date` INTEGER NOT NULL, " +
                        "`cashAmount` REAL NOT NULL, `tax` REAL NOT NULL, `fee` REAL NOT NULL, " +
                        "`stockShares` REAL NOT NULL, `note` TEXT NOT NULL)"
                )
            }
        }

        /** v3 → v4：新增股票資產分類表（原型／正二／類現金） */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `stock_category` (`market` TEXT NOT NULL, `symbol` TEXT NOT NULL, " +
                        "`category` TEXT NOT NULL, PRIMARY KEY(`market`, `symbol`))"
                )
            }
        }

        /** v4 → v5：新增股價通知表 */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `price_alerts` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`market` TEXT NOT NULL, `symbol` TEXT NOT NULL, `name` TEXT NOT NULL, `type` TEXT NOT NULL, " +
                        "`threshold` REAL NOT NULL, `enabled` INTEGER NOT NULL, `lastTriggeredDay` INTEGER)"
                )
            }
        }

        /** v5 → v6：新增股票質押表 */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `pledges` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`symbol` TEXT NOT NULL, `name` TEXT NOT NULL, `shares` REAL NOT NULL, `amount` REAL NOT NULL, " +
                        "`ratePct` REAL NOT NULL, `date` INTEGER NOT NULL, `note` TEXT NOT NULL)"
                )
            }
        }

        /** v6 → v7：新增觀察股票表 */
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `watchlist` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`market` TEXT NOT NULL, `symbol` TEXT NOT NULL, `name` TEXT NOT NULL, `position` INTEGER NOT NULL)"
                )
            }
        }

        /** v7 → v8：新增「股票漲跌」（現價與期間最高價比較）表 */
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `high_watch` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`market` TEXT NOT NULL, `symbol` TEXT NOT NULL, `name` TEXT NOT NULL, " +
                        "`position` INTEGER NOT NULL, `highPrice` REAL, " +
                        "`highDate` INTEGER, `highUpdatedAt` INTEGER NOT NULL)"
                )
            }
        }
    }
}
