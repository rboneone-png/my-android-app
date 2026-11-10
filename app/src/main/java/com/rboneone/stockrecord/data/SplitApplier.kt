package com.rboneone.stockrecord.data

import androidx.room.withTransaction
import com.rboneone.stockrecord.domain.SplitMath

/** 到了恢復交易日自動做股票分割調整（整批在同一個交易裡，要嘛全部完成、要嘛都不動） */
object SplitApplier {
    /** 執行所有已到期、還沒執行的分割，回傳執行了幾筆 */
    suspend fun applyDue(db: AppDatabase, today: Long): Int {
        val due = db.stockSplitDao().getAll().filter { SplitMath.isDue(it, today) }.sortedBy { it.resumeDate }
        for (split in due) adjust(db, split, forward = true)
        return due.size
    }

    /** 還原一筆已執行的分割（把股數與價格調回去），並刪除這筆分割紀錄 */
    suspend fun undo(db: AppDatabase, split: StockSplit) {
        if (split.applied) adjust(db, split, forward = false)
        db.stockSplitDao().delete(split)
    }

    private suspend fun adjust(db: AppDatabase, split: StockSplit, forward: Boolean) {
        db.withTransaction {
            // 在交易裡再確認一次狀態：同時有兩處（畫面與背景）檢查到期時，不會調整兩次
            val current = db.stockSplitDao().get(split.id) ?: return@withTransaction
            if (current.applied == forward) return@withTransaction
            db.tradeDao().getAll().forEach { t ->
                val n = SplitMath.adjustTrade(t, split, forward)
                if (n != t) db.tradeDao().upsert(n)
            }
            db.dividendDao().getAll().forEach { d ->
                val n = SplitMath.adjustDividend(d, split, forward)
                if (n != d) db.dividendDao().upsert(n)
            }
            db.pledgeDao().getAll().forEach { p ->
                val n = SplitMath.adjustPledge(p, split, forward)
                if (n != p) db.pledgeDao().upsert(n)
            }
            db.alertDao().getAll().forEach { a ->
                val n = SplitMath.adjustAlert(a, split, forward)
                if (n != a) db.alertDao().upsert(n)
            }
            db.stockSplitDao().upsert(split.copy(applied = forward))
        }
    }
}
