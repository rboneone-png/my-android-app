package com.rboneone.stockrecord.data

import android.content.Context
import android.content.SharedPreferences
import com.rboneone.stockrecord.domain.FeeSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class SyncConfig(
    /** true：依週期自動備份；false：手動 */
    val auto: Boolean = false,
    val intervalHours: Int = 24,
    val googleLinked: Boolean = false,
    val lastSyncAt: Long = 0,
    val lastResult: String? = null,
)

/** 每個帳號各自一份設定 */
class SettingsRepository(context: Context, userId: String) {
    private val prefs = context.getSharedPreferences(prefsName(userId), Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(loadFees())
    val settings: StateFlow<FeeSettings> = _settings.asStateFlow()

    private val _refreshSeconds = MutableStateFlow(prefs.getInt(KEY_REFRESH, DEFAULT_REFRESH_SECONDS))
    /** 持股總覽自動刷新股價的秒數 */
    val refreshSeconds: StateFlow<Int> = _refreshSeconds.asStateFlow()

    private val _cash = MutableStateFlow(prefs.double(KEY_CASH, 0.0))
    /** 資產配置用的現金（台幣），由使用者輸入 */
    val cash: StateFlow<Double> = _cash.asStateFlow()

    private val _holdingOrder = MutableStateFlow(
        Market.entries.associateWith { m -> prefs.getString(orderKey(m), null).orEmpty().split(',').filter { it.isNotEmpty() } }
    )
    /** 使用者自訂的庫存股順序（股票代號），依市場分開 */
    val holdingOrder: StateFlow<Map<Market, List<String>>> = _holdingOrder.asStateFlow()

    fun setHoldingOrder(market: Market, symbols: List<String>) {
        prefs.edit().putString(orderKey(market), symbols.joinToString(",")).apply()
        _holdingOrder.value = _holdingOrder.value + (market to symbols)
    }

    private fun orderKey(market: Market) = "holding_order_${market.name}"

    private val _sync = MutableStateFlow(loadSync())
    val sync: StateFlow<SyncConfig> = _sync.asStateFlow()

    private fun SharedPreferences.double(key: String, default: Double) =
        getString(key, null)?.toDoubleOrNull() ?: default

    private fun loadFees(): FeeSettings {
        val d = FeeSettings()
        return FeeSettings(
            twDiscount = prefs.double(KEY_TW_DISCOUNT, d.twDiscount),
            twMinFee = prefs.double(KEY_TW_MIN_FEE, d.twMinFee),
            usCommission = prefs.double(KEY_US_COMMISSION, d.usCommission),
            usDividendTaxRate = prefs.double(KEY_US_DIV_TAX, d.usDividendTaxRate),
        )
    }

    private fun loadSync() = SyncConfig(
        auto = prefs.getBoolean(KEY_SYNC_AUTO, false),
        intervalHours = prefs.getInt(KEY_SYNC_HOURS, 24),
        googleLinked = prefs.getBoolean(KEY_GOOGLE_LINKED, false),
        lastSyncAt = prefs.getLong(KEY_LAST_SYNC, 0),
        lastResult = prefs.getString(KEY_LAST_RESULT, null),
    )

    fun update(settings: FeeSettings) {
        prefs.edit()
            .putString(KEY_TW_DISCOUNT, settings.twDiscount.toString())
            .putString(KEY_TW_MIN_FEE, settings.twMinFee.toString())
            .putString(KEY_US_COMMISSION, settings.usCommission.toString())
            .putString(KEY_US_DIV_TAX, settings.usDividendTaxRate.toString())
            .apply()
        _settings.value = settings
    }

    /** 上次發出「質押維持率過低」通知的交易日，同一天只通知一次 */
    var pledgeAlertDay: Long
        get() = prefs.getLong(KEY_PLEDGE_ALERT_DAY, -1)
        set(value) = prefs.edit().putLong(KEY_PLEDGE_ALERT_DAY, value).apply()

    private val _pledgeIncludeLeveraged = MutableStateFlow(prefs.getBoolean(KEY_PLEDGE_INCLUDE_LEV, true))
    /** 可質押股票總市值是否包含「正二」分類 */
    val pledgeIncludeLeveraged: StateFlow<Boolean> = _pledgeIncludeLeveraged.asStateFlow()

    fun setPledgeIncludeLeveraged(include: Boolean) {
        prefs.edit().putBoolean(KEY_PLEDGE_INCLUDE_LEV, include).apply()
        _pledgeIncludeLeveraged.value = include
    }

    fun setCash(amount: Double) {
        prefs.edit().putString(KEY_CASH, amount.toString()).apply()
        _cash.value = amount
    }

    fun setRefreshSeconds(seconds: Int) {
        prefs.edit().putInt(KEY_REFRESH, seconds).apply()
        _refreshSeconds.value = seconds
    }

    fun updateSync(transform: (SyncConfig) -> SyncConfig) {
        val s = transform(_sync.value)
        prefs.edit()
            .putBoolean(KEY_SYNC_AUTO, s.auto)
            .putInt(KEY_SYNC_HOURS, s.intervalHours)
            .putBoolean(KEY_GOOGLE_LINKED, s.googleLinked)
            .putLong(KEY_LAST_SYNC, s.lastSyncAt)
            .putString(KEY_LAST_RESULT, s.lastResult)
            .apply()
        _sync.value = s
    }

    companion object {
        const val DEFAULT_REFRESH_SECONDS = 30
        const val MIN_REFRESH_SECONDS = 10

        fun prefsName(userId: String) = "settings_$userId"

        /** 舊版（單人）設定檔名稱 */
        const val LEGACY_PREFS = "settings"

        private const val KEY_TW_DISCOUNT = "tw_discount"
        private const val KEY_TW_MIN_FEE = "tw_min_fee"
        private const val KEY_US_COMMISSION = "us_commission"
        private const val KEY_US_DIV_TAX = "us_dividend_tax"
        private const val KEY_REFRESH = "refresh_seconds"
        private const val KEY_CASH = "cash_twd"
        private const val KEY_PLEDGE_ALERT_DAY = "pledge_alert_day"
        private const val KEY_PLEDGE_INCLUDE_LEV = "pledge_include_leveraged"
        private const val KEY_SYNC_AUTO = "sync_auto"
        private const val KEY_SYNC_HOURS = "sync_hours"
        private const val KEY_GOOGLE_LINKED = "google_linked"
        private const val KEY_LAST_SYNC = "last_sync"
        private const val KEY_LAST_RESULT = "last_sync_result"
    }
}
