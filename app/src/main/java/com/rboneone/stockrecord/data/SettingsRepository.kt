package com.rboneone.stockrecord.data

import android.content.Context
import android.content.SharedPreferences
import com.rboneone.stockrecord.domain.Clec
import com.rboneone.stockrecord.domain.FeeSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 自動備份週期換算成總小時 */
fun SyncConfig.totalHours(): Int = (intervalDays * 24 + intervalHours).coerceAtLeast(1)

data class SyncConfig(
    /** true：依週期自動備份；false：手動 */
    val auto: Boolean = false,
    /** 自動備份週期 = 天數 × 24 + 小時數（預設 0 天 24 小時），至少 1 小時 */
    val intervalDays: Int = 0,
    val intervalHours: Int = 24,
    /** 自動備份寫入的檔案（使用者用系統檔案選擇器選的，可放在 Google 雲端硬碟）；null 表示還沒選 */
    val autoFileUri: String? = null,
    val autoFileName: String? = null,
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

    private val _clecLivingCost = MutableStateFlow(prefs.double(KEY_CLEC_LIVING, 0.0))
    /** CLEC 退休投資策略：一年生活費（元） */
    val clecLivingCost: StateFlow<Double> = _clecLivingCost.asStateFlow()

    private val _clecMethod = MutableStateFlow(prefs.getString(KEY_CLEC_METHOD, null) ?: "433")
    /** CLEC 退休投資策略：配置方式代碼（例如 433） */
    val clecMethod: StateFlow<String> = _clecMethod.asStateFlow()

    private val _clecOverrides = MutableStateFlow(
        Clec.Overrides(
            prefs.getString(KEY_CLEC_F, null)?.toDoubleOrNull(),
            prefs.getString(KEY_CLEC_G, null)?.toDoubleOrNull(),
            prefs.getString(KEY_CLEC_H, null)?.toDoubleOrNull(),
        )
    )
    /** CLEC 退休投資策略：F 指數ETF、G 正二ETF、H 類現金 的手動數值；null 表示自動 */
    val clecOverrides: StateFlow<Clec.Overrides> = _clecOverrides.asStateFlow()

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
        intervalDays = prefs.getInt(KEY_SYNC_DAYS, 0),
        intervalHours = prefs.getInt(KEY_SYNC_HOURS, 24),
        autoFileUri = prefs.getString(KEY_AUTO_URI, null),
        autoFileName = prefs.getString(KEY_AUTO_NAME, null),
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

    fun setClecLivingCost(amount: Double) {
        prefs.edit().putString(KEY_CLEC_LIVING, amount.toString()).apply()
        _clecLivingCost.value = amount
    }

    fun setClecOverrides(o: Clec.Overrides) {
        prefs.edit().apply {
            fun put(key: String, v: Double?) = if (v == null) remove(key) else putString(key, v.toString())
            put(KEY_CLEC_F, o.indexEtf); put(KEY_CLEC_G, o.leveragedEtf); put(KEY_CLEC_H, o.cashLike)
        }.apply()
        _clecOverrides.value = o
    }

    fun setClecMethod(code: String) {
        prefs.edit().putString(KEY_CLEC_METHOD, code).apply()
        _clecMethod.value = code
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
            .putInt(KEY_SYNC_DAYS, s.intervalDays)
            .putInt(KEY_SYNC_HOURS, s.intervalHours)
            .putString(KEY_AUTO_URI, s.autoFileUri)
            .putString(KEY_AUTO_NAME, s.autoFileName)
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
        private const val KEY_CLEC_LIVING = "clec_living_cost"
        private const val KEY_CLEC_METHOD = "clec_method"
        private const val KEY_CLEC_F = "clec_f"
        private const val KEY_CLEC_G = "clec_g"
        private const val KEY_CLEC_H = "clec_h"
        private const val KEY_PLEDGE_ALERT_DAY = "pledge_alert_day"
        private const val KEY_PLEDGE_INCLUDE_LEV = "pledge_include_leveraged"
        private const val KEY_SYNC_AUTO = "sync_auto"
        private const val KEY_SYNC_HOURS = "sync_hours"
        private const val KEY_SYNC_DAYS = "sync_days"
        private const val KEY_AUTO_URI = "sync_auto_uri"
        private const val KEY_AUTO_NAME = "sync_auto_name"
        private const val KEY_LAST_SYNC = "last_sync"
        private const val KEY_LAST_RESULT = "last_sync_result"
    }
}
