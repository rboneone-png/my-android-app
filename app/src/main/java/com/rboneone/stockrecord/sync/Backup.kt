package com.rboneone.stockrecord.sync

import com.rboneone.stockrecord.data.AlertType
import com.rboneone.stockrecord.data.AssetCategory
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
import org.json.JSONArray
import org.json.JSONObject

data class BackupData(
    val exportedAt: Long,
    val trades: List<Trade>,
    val dividends: List<Dividend>,
    val fees: FeeSettings,
    val categories: List<StockCategory> = emptyList(),
    /** 資產配置用的現金（台幣） */
    val cash: Double = 0.0,
    val alerts: List<PriceAlert> = emptyList(),
    val pledges: List<Pledge> = emptyList(),
    /** 庫存股自訂順序 */
    val holdingOrder: Map<Market, List<String>> = emptyMap(),
    val watchlist: List<WatchItem> = emptyList(),
    /** 股票漲跌清單（不含抓到的最高價，還原後重新抓） */
    val highWatch: List<HighWatchItem> = emptyList(),
)

/** 備份檔格式（JSON），密碼不會放進備份 */
object Backup {
    private const val VERSION = 1

    fun serialize(data: BackupData): String {
        val trades = JSONArray()
        data.trades.forEach {
            trades.put(
                JSONObject()
                    .put("id", it.id).put("market", it.market.name).put("symbol", it.symbol).put("name", it.name)
                    .put("type", it.type.name).put("date", it.date).put("shares", it.shares).put("price", it.price)
                    .put("fee", it.fee).put("tax", it.tax).put("taxType", it.taxType.name).put("note", it.note)
            )
        }
        val dividends = JSONArray()
        data.dividends.forEach {
            dividends.put(
                JSONObject()
                    .put("id", it.id).put("market", it.market.name).put("symbol", it.symbol).put("name", it.name)
                    .put("date", it.date).put("cashAmount", it.cashAmount).put("tax", it.tax).put("fee", it.fee)
                    .put("stockShares", it.stockShares).put("note", it.note)
            )
        }
        val fees = JSONObject()
            .put("twDiscount", data.fees.twDiscount)
            .put("twMinFee", data.fees.twMinFee)
            .put("usCommission", data.fees.usCommission)
            .put("usDividendTaxRate", data.fees.usDividendTaxRate)
        val categories = JSONArray()
        data.categories.forEach {
            categories.put(
                JSONObject().put("market", it.market.name).put("symbol", it.symbol).put("category", it.category.name)
            )
        }
        val alerts = JSONArray()
        data.alerts.forEach {
            alerts.put(
                JSONObject()
                    .put("id", it.id).put("market", it.market.name).put("symbol", it.symbol).put("name", it.name)
                    .put("type", it.type.name).put("threshold", it.threshold).put("enabled", it.enabled)
            )
        }
        val pledges = JSONArray()
        data.pledges.forEach {
            pledges.put(
                JSONObject()
                    .put("id", it.id).put("symbol", it.symbol).put("name", it.name).put("shares", it.shares)
                    .put("amount", it.amount).put("ratePct", it.ratePct).put("date", it.date).put("note", it.note)
            )
        }
        val order = JSONObject()
        data.holdingOrder.forEach { (market, symbols) -> order.put(market.name, JSONArray(symbols)) }
        val watch = JSONArray()
        data.watchlist.forEach {
            watch.put(
                JSONObject().put("id", it.id).put("market", it.market.name).put("symbol", it.symbol)
                    .put("name", it.name).put("position", it.position)
            )
        }
        val highWatch = JSONArray()
        data.highWatch.forEach {
            highWatch.put(
                JSONObject().put("id", it.id).put("market", it.market.name).put("symbol", it.symbol)
                    .put("name", it.name).put("position", it.position)
            )
        }
        return JSONObject()
            .put("version", VERSION)
            .put("highWatch", highWatch)
            .put("watchlist", watch)
            .put("holdingOrder", order)
            .put("pledges", pledges)
            .put("alerts", alerts)
            .put("categories", categories)
            .put("cash", data.cash)
            .put("exportedAt", data.exportedAt)
            .put("fees", fees)
            .put("trades", trades)
            .put("dividends", dividends)
            .toString()
    }

    fun parse(json: String): BackupData {
        val root = JSONObject(json)
        val trades = root.getJSONArray("trades").let { a ->
            (0 until a.length()).map { i ->
                val o = a.getJSONObject(i)
                Trade(
                    id = o.getLong("id"),
                    market = Market.valueOf(o.getString("market")),
                    symbol = o.getString("symbol"),
                    name = o.getString("name"),
                    type = TradeType.valueOf(o.getString("type")),
                    date = o.getLong("date"),
                    shares = o.getDouble("shares"),
                    price = o.getDouble("price"),
                    fee = o.getDouble("fee"),
                    tax = o.getDouble("tax"),
                    taxType = TaxType.valueOf(o.getString("taxType")),
                    note = o.optString("note"),
                )
            }
        }
        val dividends = root.optJSONArray("dividends")?.let { a ->
            (0 until a.length()).map { i ->
                val o = a.getJSONObject(i)
                Dividend(
                    id = o.getLong("id"),
                    market = Market.valueOf(o.getString("market")),
                    symbol = o.getString("symbol"),
                    name = o.getString("name"),
                    date = o.getLong("date"),
                    cashAmount = o.getDouble("cashAmount"),
                    tax = o.getDouble("tax"),
                    fee = o.getDouble("fee"),
                    stockShares = o.getDouble("stockShares"),
                    note = o.optString("note"),
                )
            }
        } ?: emptyList()
        val defaults = FeeSettings()
        val fees = root.optJSONObject("fees")?.let {
            FeeSettings(
                twDiscount = it.optDouble("twDiscount", defaults.twDiscount),
                twMinFee = it.optDouble("twMinFee", defaults.twMinFee),
                usCommission = it.optDouble("usCommission", defaults.usCommission),
                usDividendTaxRate = it.optDouble("usDividendTaxRate", defaults.usDividendTaxRate),
            )
        } ?: defaults
        // 舊版備份沒有分類與現金，視為空白
        val categories = root.optJSONArray("categories")?.let { a ->
            (0 until a.length()).mapNotNull { i ->
                val o = a.getJSONObject(i)
                runCatching {
                    StockCategory(
                        Market.valueOf(o.getString("market")),
                        o.getString("symbol"),
                        AssetCategory.valueOf(o.getString("category")),
                    )
                }.getOrNull()
            }
        } ?: emptyList()
        val alerts = root.optJSONArray("alerts")?.let { a ->
            (0 until a.length()).mapNotNull { i ->
                val o = a.getJSONObject(i)
                runCatching {
                    PriceAlert(
                        id = o.getLong("id"),
                        market = Market.valueOf(o.getString("market")),
                        symbol = o.getString("symbol"),
                        name = o.getString("name"),
                        type = AlertType.valueOf(o.getString("type")),
                        threshold = o.getDouble("threshold"),
                        enabled = o.optBoolean("enabled", true),
                    )
                }.getOrNull()
            }
        } ?: emptyList()
        val pledges = root.optJSONArray("pledges")?.let { a ->
            (0 until a.length()).map { i ->
                val o = a.getJSONObject(i)
                Pledge(
                    id = o.getLong("id"),
                    symbol = o.getString("symbol"),
                    name = o.getString("name"),
                    shares = o.getDouble("shares"),
                    amount = o.getDouble("amount"),
                    ratePct = o.getDouble("ratePct"),
                    date = o.getLong("date"),
                    note = o.optString("note"),
                )
            }
        } ?: emptyList()
        val holdingOrder = root.optJSONObject("holdingOrder")?.let { o ->
            Market.entries.mapNotNull { m ->
                o.optJSONArray(m.name)?.let { a -> m to (0 until a.length()).map { a.getString(it) } }
            }.toMap()
        } ?: emptyMap()
        val watchlist = root.optJSONArray("watchlist")?.let { a ->
            (0 until a.length()).mapNotNull { i ->
                val o = a.getJSONObject(i)
                runCatching {
                    WatchItem(
                        id = o.getLong("id"),
                        market = Market.valueOf(o.getString("market")),
                        symbol = o.getString("symbol"),
                        name = o.getString("name"),
                        position = o.getInt("position"),
                    )
                }.getOrNull()
            }
        } ?: emptyList()
        val highWatch = root.optJSONArray("highWatch")?.let { a ->
            (0 until a.length()).mapNotNull { i ->
                val o = a.getJSONObject(i)
                runCatching {
                    HighWatchItem(
                        id = o.getLong("id"),
                        market = Market.valueOf(o.getString("market")),
                        symbol = o.getString("symbol"),
                        name = o.getString("name"),
                        position = o.getInt("position"),
                    )
                }.getOrNull()
            }
        } ?: emptyList()
        return BackupData(
            root.optLong("exportedAt"), trades, dividends, fees, categories, root.optDouble("cash", 0.0), alerts, pledges,
            holdingOrder, watchlist, highWatch,
        )
    }
}
