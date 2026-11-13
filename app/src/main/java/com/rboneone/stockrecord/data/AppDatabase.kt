package com.rboneone.stockrecord.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Trade::class, StockInfo::class, Quote::class, Dividend::class, StockCategory::class, PriceAlert::class, Pledge::class, WatchItem::class, HighWatchItem::class, CashFlow::class, Loan::class, StockSplit::class],
    version = 14,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun tradeDao(): TradeDao
    abstract fun marketDao(): MarketDao
    abstract fun dividendDao(): DividendDao
    abstract fun categoryDao(): CategoryDao
    abstract fun alertDao(): AlertDao
    abstract fun pledgeDao(): PledgeDao
    abstract fun loanDao(): LoanDao
    abstract fun stockSplitDao(): StockSplitDao
    abstract fun watchDao(): WatchDao
    abstract fun highWatchDao(): HighWatchDao
    abstract fun cashFlowDao(): CashFlowDao

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

        /** v8 → v9：新增帳務管理的現金存入／領出表 */
        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `cash_flows` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`market` TEXT NOT NULL, `type` TEXT NOT NULL, `date` INTEGER NOT NULL, " +
                        "`amount` REAL NOT NULL, `note` TEXT NOT NULL)"
                )
            }
        }

        /** v9 → v10：股利新增「每股股利」欄位（舊紀錄為空） */
        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `dividends` ADD COLUMN `perShare` REAL")
            }
        }

        /** v13 → v14：美股買賣與股利各自記住當時的匯率（舊紀錄為空，之後由 App 抓歷史匯率補上） */
        val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `trades` ADD COLUMN `fxRate` REAL")
                db.execSQL("ALTER TABLE `dividends` ADD COLUMN `fxRate` REAL")
            }
        }

        /** v12 → v13：新增股票分割資料表 */
        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `stock_splits` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`market` TEXT NOT NULL, `symbol` TEXT NOT NULL, `name` TEXT NOT NULL, " +
                        "`suspendDate` INTEGER NOT NULL, `resumeDate` INTEGER NOT NULL, `ratio` INTEGER NOT NULL, " +
                        "`applied` INTEGER NOT NULL)"
                )
            }
        }

        /** v11 → v12：質押新增「歸還日」欄位（舊紀錄為空＝還在質押中） */
        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `pledges` ADD COLUMN `returnDate` INTEGER")
            }
        }

        /** v10 → v11：新增貸款資料表 */
        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `loans` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`type` TEXT NOT NULL, `principal` REAL NOT NULL, `annualRatePct` REAL NOT NULL, " +
                        "`totalPeriods` INTEGER NOT NULL, `paidPeriods` INTEGER NOT NULL, `payDay` INTEGER NOT NULL, " +
                        "`startDate` INTEGER NOT NULL, `note` TEXT NOT NULL)"
                )
            }
        }
    }
}
