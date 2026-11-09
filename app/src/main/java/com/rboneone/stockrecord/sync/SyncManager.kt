package com.rboneone.stockrecord.sync

import android.net.Uri
import androidx.room.withTransaction
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.rboneone.stockrecord.StockApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

class SyncManager(private val app: StockApp) {
    /** 以帳號名稱命名，換新手機時建立同名帳號就能還原 */
    private fun fileName(userId: String): String {
        val username = app.accountStore.find(userId)?.username ?: userId
        return "stockrecord_$username.json"
    }

    private suspend fun collect(userId: String): BackupData {
        val db = app.database(userId)
        return BackupData(
            exportedAt = System.currentTimeMillis(),
            trades = db.tradeDao().getAll(),
            dividends = db.dividendDao().getAll(),
            fees = app.settings(userId).settings.value,
            categories = db.categoryDao().getAll(),
            cash = app.settings(userId).cash.value,
            alerts = db.alertDao().getAll(),
            pledges = db.pledgeDao().getAll(),
            holdingOrder = app.settings(userId).holdingOrder.value,
            watchlist = db.watchDao().getAll(),
            highWatch = db.highWatchDao().getAll(),
            cashFlows = db.cashFlowDao().getAll(),
            loans = db.loanDao().getAll(),
            clecLivingCost = app.settings(userId).clecLivingCost.value,
            clecMethod = app.settings(userId).clecMethod.value,
            clecOverrides = app.settings(userId).clecOverrides.value,
            account = app.accountStore.find(userId)?.let { BackupAccount(it.username, it.salt, it.passwordHash, it.method.name) },
        )
    }

    /** 寫入自動備份檔（使用者先前選好的檔案） */
    suspend fun backupAuto(userId: String): Result<Unit> {
        val uri = app.settings(userId).sync.value.autoFileUri?.let(Uri::parse)
            ?: return Result.failure<Unit>(IllegalStateException("還沒有選擇自動備份檔案")).also { record(userId, it.exceptionOrNull(), "備份") }
        return backupToFile(userId, uri)
    }

    /** 從自動備份檔還原，回傳還原的筆數 */
    suspend fun restoreAuto(userId: String): Result<Int> {
        val uri = app.settings(userId).sync.value.autoFileUri?.let(Uri::parse)
            ?: return Result.failure<Int>(IllegalStateException("還沒有選擇自動備份檔案")).also { record(userId, it.exceptionOrNull(), "還原") }
        return restoreFromFile(userId, uri)
    }

    /** 檔案權限被收回或檔案被刪除時，自動備份無法再寫入 */
    fun isFileLost(e: Throwable?) = e is SecurityException || e is java.io.FileNotFoundException

    /** 備份成檔案，存到使用者選的位置（可選 Google 雲端硬碟） */
    suspend fun backupToFile(userId: String, uri: Uri): Result<Unit> = runCatching {
        val json = Backup.serialize(collect(userId))
        withContext(Dispatchers.IO) {
            // 有些檔案提供者（例如雲端硬碟）不接受 "wt"，退回 "w"
            val out = runCatching { app.contentResolver.openOutputStream(uri, "wt") }.getOrNull()
                ?: app.contentResolver.openOutputStream(uri, "w") ?: error("無法寫入檔案")
            out.use { it.write(json.toByteArray(Charsets.UTF_8)) }
        }
    }.also { record(userId, it.exceptionOrNull(), "備份") }

    /** 從使用者選的備份檔還原，回傳還原的筆數 */
    suspend fun restoreFromFile(userId: String, uri: Uri): Result<Int> = runCatching {
        val json = withContext(Dispatchers.IO) {
            val input = app.contentResolver.openInputStream(uri) ?: error("無法讀取檔案")
            input.use { it.readBytes().toString(Charsets.UTF_8) }
        }
        val data = runCatching { Backup.parse(json) }.getOrElse { error("這不是股票記帳的備份檔") }
        apply(userId, data)
    }.also { record(userId, it.exceptionOrNull(), "還原") }

    /** 自動備份用固定檔名，每次覆寫同一個檔案 */
    fun autoFileName(userId: String): String {
        val username = app.accountStore.find(userId)?.username ?: "backup"
        return "股票記帳自動備份_$username.json"
    }

    /** 檔名例如「股票記帳備份_小明_20261030-1530.json」 */
    fun suggestedFileName(userId: String): String {
        val username = app.accountStore.find(userId)?.username ?: "backup"
        val time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmm"))
        return "股票記帳備份_${username}_$time.json"
    }

    /** 新安裝時用備份檔登入：資料還原到剛建立的帳號 */
    suspend fun restoreInto(userId: String, data: BackupData): Int = apply(userId, data)

    /** 用備份取代手機上這個帳號的資料 */
    private suspend fun apply(userId: String, data: BackupData): Int {
        val db = app.database(userId)
        db.withTransaction {
            db.tradeDao().deleteAll()
            db.dividendDao().deleteAll()
            db.tradeDao().insertAll(data.trades)
            db.dividendDao().insertAll(data.dividends)
            db.categoryDao().deleteAll()
            db.categoryDao().insertAll(data.categories)
            db.alertDao().deleteAll()
            db.alertDao().insertAll(data.alerts)
            db.pledgeDao().deleteAll()
            db.pledgeDao().insertAll(data.pledges)
            db.watchDao().deleteAll()
            db.watchDao().insertAll(data.watchlist)
            db.highWatchDao().deleteAll()
            db.highWatchDao().insertAll(data.highWatch)
            db.cashFlowDao().deleteAll()
            db.cashFlowDao().insertAll(data.cashFlows)
            db.loanDao().deleteAll()
            db.loanDao().insertAll(data.loans)
        }
        app.settings(userId).update(data.fees)
        app.settings(userId).setCash(data.cash)
        app.settings(userId).setClecLivingCost(data.clecLivingCost)
        app.settings(userId).setClecMethod(data.clecMethod)
        app.settings(userId).setClecOverrides(data.clecOverrides)
        data.holdingOrder.forEach { (market, symbols) -> app.settings(userId).setHoldingOrder(market, symbols) }
        return data.trades.size + data.dividends.size
    }

    private fun record(userId: String, error: Throwable?, action: String) {
        app.settings(userId).updateSync {
            if (error == null) it.copy(lastSyncAt = System.currentTimeMillis(), lastResult = "${action}成功")
            else if (isFileLost(error))
                // 檔案不見或沒有權限：關掉自動備份，請使用者重新選檔
                it.copy(auto = false, autoFileUri = null, autoFileName = null,
                    lastResult = "${action}失敗：無法存取備份檔案，請重新選擇檔案")
            else it.copy(lastResult = "${action}失敗：${error.message}")
        }
    }

    fun schedule(userId: String, hours: Int, replace: Boolean) {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(hours.toLong(), TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setInputData(workDataOf(SyncWorker.KEY_USER to userId))
            .build()
        WorkManager.getInstance(app).enqueueUniquePeriodicWork(
            workName(userId),
            if (replace) ExistingPeriodicWorkPolicy.UPDATE else ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    fun cancel(userId: String) {
        WorkManager.getInstance(app).cancelUniqueWork(workName(userId))
    }

    private fun workName(userId: String) = "sync-$userId"
}

/** 背景自動備份 */
class SyncWorker(context: android.content.Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val userId = inputData.getString(KEY_USER) ?: return Result.failure()
        val app = applicationContext as StockApp
        val result = app.syncManager.backupAuto(userId)
        if (result.isSuccess) return Result.success()
        // 檔案存取失敗重試也沒用（record 已關閉自動備份）；其他錯誤（多半是網路）最多重試 3 次
        if (app.syncManager.isFileLost(result.exceptionOrNull())) {
            app.syncManager.cancel(userId)
            return Result.success()
        }
        return if (runAttemptCount < 3) Result.retry() else Result.success() // 定期工作不能回 failure，否則之後不再執行
    }

    companion object {
        const val KEY_USER = "user_id"
    }
}
