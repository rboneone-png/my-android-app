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
        )
    }

    /** 把手機資料上傳到 Google 雲端 */
    suspend fun backup(userId: String): Result<Unit> = runCatching {
        val token = GoogleDrive.token(app) ?: error("需要重新連結 Google 帳號")
        GoogleDrive.upload(token, fileName(userId), Backup.serialize(collect(userId)))
    }.also { record(userId, it.exceptionOrNull(), "備份") }

    /** 用雲端備份取代手機資料，回傳還原的筆數 */
    suspend fun restore(userId: String): Result<Int> = runCatching {
        val token = GoogleDrive.token(app) ?: error("需要重新連結 Google 帳號")
        val json = GoogleDrive.download(token, fileName(userId)) ?: error("雲端還沒有這個帳號的備份")
        apply(userId, Backup.parse(json))
    }.also { record(userId, it.exceptionOrNull(), "還原") }

    /** 備份成檔案，存到使用者選的位置（可選 Google 雲端硬碟） */
    suspend fun backupToFile(userId: String, uri: Uri): Result<Unit> = runCatching {
        val json = Backup.serialize(collect(userId))
        withContext(Dispatchers.IO) {
            val out = app.contentResolver.openOutputStream(uri, "wt") ?: error("無法寫入檔案")
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

    /** 檔名例如「股票記帳備份_小明_20261030-1530.json」 */
    fun suggestedFileName(userId: String): String {
        val username = app.accountStore.find(userId)?.username ?: "backup"
        val time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmm"))
        return "股票記帳備份_${username}_$time.json"
    }

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
        }
        app.settings(userId).update(data.fees)
        app.settings(userId).setCash(data.cash)
        data.holdingOrder.forEach { (market, symbols) -> app.settings(userId).setHoldingOrder(market, symbols) }
        return data.trades.size + data.dividends.size
    }

    private fun record(userId: String, error: Throwable?, action: String) {
        app.settings(userId).updateSync {
            if (error == null) it.copy(lastSyncAt = System.currentTimeMillis(), lastResult = "${action}成功")
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
        return if (app.syncManager.backup(userId).isSuccess) Result.success() else Result.retry()
    }

    companion object {
        const val KEY_USER = "user_id"
    }
}
