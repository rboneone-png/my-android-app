package com.rboneone.stockrecord

import android.app.Application
import android.content.Context
import androidx.room.Room
import com.rboneone.stockrecord.alerts.AlertChecker
import com.rboneone.stockrecord.alerts.AlertWorker
import com.rboneone.stockrecord.auth.Account
import com.rboneone.stockrecord.auth.AccountStore
import com.rboneone.stockrecord.auth.AuthMethod
import com.rboneone.stockrecord.data.AppDatabase
import com.rboneone.stockrecord.data.MarketRepository
import com.rboneone.stockrecord.data.SettingsRepository
import com.rboneone.stockrecord.sync.SyncManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class StockApp : Application() {
    val accountStore: AccountStore by lazy { AccountStore(this) }
    val syncManager: SyncManager by lazy { SyncManager(this) }
    val alertChecker: AlertChecker by lazy { AlertChecker(this) }

    override fun onCreate() {
        super.onCreate()
        // 測試環境可能沒有初始化 WorkManager
        runCatching { AlertWorker.schedule(this) }
    }

    private val _session = MutableStateFlow<Account?>(null)
    /** 目前登入的帳號；null 表示在登入頁 */
    val session: StateFlow<Account?> = _session.asStateFlow()

    private val databases = mutableMapOf<String, AppDatabase>()
    private val settingsRepos = mutableMapOf<String, SettingsRepository>()
    private val marketRepos = mutableMapOf<String, MarketRepository>()

    /** 每個帳號一個獨立的資料庫檔案 */
    @Synchronized
    fun database(userId: String): AppDatabase = databases.getOrPut(userId) {
        Room.databaseBuilder(this, AppDatabase::class.java, databaseName(userId))
            .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4, AppDatabase.MIGRATION_4_5, AppDatabase.MIGRATION_5_6, AppDatabase.MIGRATION_6_7)
            .build()
    }

    @Synchronized
    fun settings(userId: String): SettingsRepository =
        settingsRepos.getOrPut(userId) { SettingsRepository(this, userId) }

    @Synchronized
    fun marketRepository(userId: String): MarketRepository =
        marketRepos.getOrPut(userId) { MarketRepository(database(userId).marketDao()) }

    fun login(account: Account) {
        accountStore.setLastUser(account.id)
        _session.value = account
    }

    fun logout() {
        _session.value = null
    }

    fun updateSessionAccount(account: Account) {
        accountStore.update(account)
        if (_session.value?.id == account.id) _session.value = account
    }

    fun createAccount(username: String, password: String, method: AuthMethod): Account {
        val isFirst = accountStore.accounts.value.isEmpty()
        val account = accountStore.create(username, password, method)
        if (isFirst) adoptLegacyData(account.id)
        return account
    }

    /** 舊版沒有帳號功能，把原本的資料交給第一個建立的帳號 */
    private fun adoptLegacyData(userId: String) {
        val legacy = getDatabasePath(LEGACY_DB)
        if (legacy.exists()) {
            for (suffix in listOf("", "-wal", "-shm", "-journal")) {
                val from = getDatabasePath(LEGACY_DB + suffix)
                if (from.exists()) from.renameTo(getDatabasePath(databaseName(userId) + suffix))
            }
        }
        val old = getSharedPreferences(SettingsRepository.LEGACY_PREFS, Context.MODE_PRIVATE)
        if (old.all.isNotEmpty()) {
            val target = getSharedPreferences(SettingsRepository.prefsName(userId), Context.MODE_PRIVATE).edit()
            old.all.forEach { (key, value) -> if (value is String) target.putString(key, value) }
            target.apply()
            old.edit().clear().apply()
        }
    }

    companion object {
        private const val LEGACY_DB = "stock.db"
        fun databaseName(userId: String) = "stock_$userId.db"
    }
}
