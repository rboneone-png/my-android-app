package com.rboneone.stockrecord.sync

import com.rboneone.stockrecord.data.AlertType
import com.rboneone.stockrecord.data.AssetCategory
import com.rboneone.stockrecord.data.Dividend
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
                Trade(6, Market.US, "BRK.B", "Berkshire", TradeType.BUY, 20_001, 1.5, 500.25, 0.0, 0.0),
            ),
            dividends = listOf(Dividend(7, Market.TW, "0056", "元大高股息", 20_002, 25_000.0, 527.0, 10.0, 0.0, "")),
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
        )
        assertEquals(data, Backup.parse(Backup.serialize(data)))
    }

    @Test
    fun oldBackupWithoutCategories() {
        val json = """{"version":1,"exportedAt":1,"trades":[],"dividends":[]}"""
        val data = Backup.parse(json)
        assertEquals(emptyList<StockCategory>(), data.categories)
        assertEquals(0.0, data.cash, 0.0)
    }
}
