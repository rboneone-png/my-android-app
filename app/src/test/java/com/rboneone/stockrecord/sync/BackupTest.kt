package com.rboneone.stockrecord.sync

import com.rboneone.stockrecord.data.AlertType
import com.rboneone.stockrecord.data.AssetCategory
import com.rboneone.stockrecord.data.CashFlow
import com.rboneone.stockrecord.data.CashFlowType
import com.rboneone.stockrecord.data.Dividend
import com.rboneone.stockrecord.data.HighWatchItem
import com.rboneone.stockrecord.data.Market
import com.rboneone.stockrecord.data.Pledge
import com.rboneone.stockrecord.data.PriceAlert
import com.rboneone.stockrecord.data.StockCategory
import com.rboneone.stockrecord.data.TaxType
import com.rboneone.stockrecord.data.Trade
import com.rboneone.stockrecord.data.TradeType
import com.rboneone.stockrecord.data.WatchItem
import com.rboneone.stockrecord.domain.FeeSettings
import org.junit.Assert.assertEquals
import org.junit.Test

class BackupTest {
    @Test
    fun roundTrip() {
        val data = BackupData(
            exportedAt = 123,
            trades = listOf(
                Trade(5, Market.TW, "2330", "台積電", TradeType.SELL, 20_000, 1000.0, 600.5, 239.0, 900.0, TaxType.DAY_TRADE, "備註"),
                Trade(6, Market.US, "BRK.B", "Berkshire", TradeType.BUY, 20_001, 1.5, 500.25, 0.0, 0.0, fxRate = 31.4567),
                Trade(12, Market.US, "VOO", "Vanguard", TradeType.BUY, 20_001, 1.0, 400.0, 0.0, 0.0),   // 舊紀錄還沒補匯率
            ),
            dividends = listOf(
                Dividend(7, Market.TW, "0056", "元大高股息", 20_002, 25_000.0, 527.0, 10.0, 0.0, "", perShare = 0.5),
                Dividend(8, Market.TW, "0050", "元大台灣50", 20_003, 10_000.0, 0.0, 0.0, 0.0, ""),   // 舊紀錄沒有每股股利
                Dividend(13, Market.US, "VOO", "Vanguard", 20_004, 12.0, 3.6, 0.0, 0.0, "", fxRate = 32.1),
            ),
            fees = FeeSettings(0.3, 1.0, 2.5, 0.1),
            categories = listOf(
                StockCategory(Market.TW, "0050", AssetCategory.CORE),
                StockCategory(Market.TW, "00631L", AssetCategory.LEVERAGED_2X),
                StockCategory(Market.TW, "2884", AssetCategory.OTHER),
            ),
            cash = 123_456.0,
            alerts = listOf(
                PriceAlert(9, Market.TW, "2330", "台積電", AlertType.FALL_PCT, 3.0, enabled = false),
                PriceAlert(10, Market.US, "AAPL", "Apple Inc.", AlertType.RISE_AMOUNT, 5.5),
            ),
            pledges = listOf(Pledge(11, "2330", "台積電", 1000.0, 1_000_000.0, 2.5, 20_000, "備註")),
            holdingOrder = mapOf(Market.TW to listOf("2884", "2330"), Market.US to listOf("VOO")),
            watchlist = listOf(WatchItem(3, Market.TW, "2454", "聯發科", 0), WatchItem(4, Market.US, "NVDA", "NVIDIA", 1)),
            highWatch = listOf(HighWatchItem(5, Market.TW, "0050", "元大台灣50", 0), HighWatchItem(6, Market.US, "QQQ", "Invesco QQQ", 1)),
            cashFlows = listOf(
                CashFlow(1, Market.TW, CashFlowType.DEPOSIT, 20_100, 500_000.0, "薪轉"),
                CashFlow(2, Market.US, CashFlowType.WITHDRAW, 20_101, 1_250.5),
            ),
        )
        assertEquals(data, Backup.parse(Backup.serialize(data)))
    }

    @Test
    fun oldBackupWithoutCategories() {
        val json = """{"version":1,"exportedAt":1,"trades":[],"dividends":[]}"""
        val data = Backup.parse(json)
        assertEquals(emptyList<StockCategory>(), data.categories)
        assertEquals(0.0, data.cash, 0.0)
        assertEquals(emptyList<HighWatchItem>(), data.highWatch)
        assertEquals(emptyList<CashFlow>(), data.cashFlows)
    }

    @Test
    fun highWatchBackup_dropsCachedHigh() {
        val item = HighWatchItem(1, Market.TW, "0050", "元大台灣50", 0, highPrice = 80.0, highDate = 20_000, highUpdatedAt = 5)
        val restored = Backup.parse(Backup.serialize(BackupData(0, emptyList(), emptyList(), FeeSettings(), highWatch = listOf(item))))
        assertEquals(item.copy(highPrice = null, highDate = null, highUpdatedAt = 0), restored.highWatch.single())
    }

    @Test
    fun oldDividendWithoutPerShare_parsesAsNull() {
        val json = """{"version":1,"exportedAt":1,"trades":[],"dividends":[{"id":1,"market":"TW","symbol":"0056","name":"x","date":20000,
            "cashAmount":1000.0,"tax":0.0,"fee":0.0,"stockShares":0.0,"note":""}]}"""
        assertEquals(null, Backup.parse(json).dividends.single().perShare)
    }

    @Test
    fun accountRoundTrip_andOldBackupHasNone() {
        val acc = BackupAccount("小明", "c2FsdA==", "aGFzaA==", "FINGERPRINT")
        val data = Backup.parse(Backup.serialize(BackupData(1, emptyList(), emptyList(), FeeSettings(), account = acc)))
        assertEquals(acc, data.account)
        assertEquals(null, Backup.parse("""{"version":1,"exportedAt":1,"trades":[],"dividends":[]}""").account)
    }

    @Test
    fun loansRoundTrip_andOldBackupHasNone() {
        val loan = com.rboneone.stockrecord.data.Loan(
            3, com.rboneone.stockrecord.data.LoanType.CAR, 800_000.0, 2.5, 60, 4, 15, 20_000, "房車",
        )
        val data = Backup.parse(Backup.serialize(BackupData(1, emptyList(), emptyList(), FeeSettings(), loans = listOf(loan))))
        assertEquals(listOf(loan), data.loans)
        assertEquals(emptyList<com.rboneone.stockrecord.data.Loan>(), Backup.parse("""{"version":1,"exportedAt":1,"trades":[],"dividends":[]}""").loans)
    }

    @Test
    fun clecSettingsRoundTrip_andDefaultsForOldBackup() {
        val data = Backup.parse(Backup.serialize(BackupData(1, emptyList(), emptyList(), FeeSettings(), clecLivingCost = 600_000.0, clecMethod = "523")))
        assertEquals(600_000.0, data.clecLivingCost, 0.0)
        assertEquals("523", data.clecMethod)
        val old = Backup.parse("""{"version":1,"exportedAt":1,"trades":[],"dividends":[]}""")
        assertEquals(0.0, old.clecLivingCost, 0.0)
        assertEquals("433", old.clecMethod)
    }

    @Test
    fun clecOverridesRoundTrip_nullMeansAuto() {
        val o = com.rboneone.stockrecord.domain.Clec.Overrides(indexEtf = 5_000_000.0, leveragedEtf = 0.0, cashLike = null)
        val data = Backup.parse(Backup.serialize(BackupData(1, emptyList(), emptyList(), FeeSettings(), clecOverrides = o)))
        assertEquals(o, data.clecOverrides)
        assertEquals(com.rboneone.stockrecord.domain.Clec.Overrides(), Backup.parse("""{"version":1,"exportedAt":1,"trades":[],"dividends":[]}""").clecOverrides)
    }

    @Test
    fun pledgeReturnDateRoundTrip_andOldPledgeIsActive() {
        val returned = com.rboneone.stockrecord.data.Pledge(1, "2330", "台積電", 1000.0, 100_000.0, 2.0, 10, "", 40)
        val active = com.rboneone.stockrecord.data.Pledge(2, "2330", "台積電", 1000.0, 100_000.0, 2.0, 20)
        val data = Backup.parse(Backup.serialize(BackupData(1, emptyList(), emptyList(), FeeSettings(), pledges = listOf(returned, active))))
        assertEquals(listOf(40L, null), data.pledges.map { it.returnDate })
        val old = Backup.parse("""{"version":1,"exportedAt":1,"trades":[],"dividends":[],"pledges":[{"id":3,"symbol":"2330","name":"x","shares":1000,"amount":1000,"ratePct":2,"date":5}]}""")
        assertEquals(null, old.pledges.single().returnDate)
    }

    @Test
    fun splitsRoundTrip_andOldBackupHasNone() {
        val sp = com.rboneone.stockrecord.data.StockSplit(7, Market.TW, "0050", "元大台灣50", 100, 110, 5, true)
        val data = Backup.parse(Backup.serialize(BackupData(1, emptyList(), emptyList(), FeeSettings(), splits = listOf(sp))))
        assertEquals(listOf(sp), data.splits)
        assertEquals(emptyList<com.rboneone.stockrecord.data.StockSplit>(), Backup.parse("""{"version":1,"exportedAt":1,"trades":[],"dividends":[]}""").splits)
    }
}
