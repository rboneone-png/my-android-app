package com.rboneone.stockrecord.ui.screens

import android.app.Activity
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.auth.api.identity.Identity
import com.rboneone.stockrecord.auth.AuthMethod
import com.rboneone.stockrecord.auth.Biometric
import com.rboneone.stockrecord.data.SettingsRepository
import com.rboneone.stockrecord.domain.FeeSettings
import com.rboneone.stockrecord.sync.GoogleDrive
import com.rboneone.stockrecord.ui.AppViewModel
import com.rboneone.stockrecord.ui.formatTime
import com.rboneone.stockrecord.ui.plainNumber
import com.rboneone.stockrecord.ui.toAmountOrNull
import kotlinx.coroutines.launch

private val SYNC_HOURS = listOf(1, 6, 12, 24)

@Composable
fun SettingsScreen(vm: AppViewModel) {
    val context = LocalContext.current
    val message by vm.message.collectAsStateWithLifecycle()
    LaunchedEffect(message) {
        message?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            vm.clearMessage()
        }
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        ScreenTopBar("設定")
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            AccountSection(vm)
            RefreshSection(vm)
            SyncSection(vm)
            ExportSection(vm)
            FeeSection(vm)
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun AccountSection(vm: AppViewModel) {
    val context = LocalContext.current
    val account by vm.account.collectAsStateWithLifecycle()
    val current = account ?: return
    val biometricAvailable = remember { Biometric.isAvailable(context) }
    var error by remember { mutableStateOf<String?>(null) }

    Section("帳號") {
        Text("目前帳號：${current.username}", style = MaterialTheme.typography.bodyLarge)
        Text("登入方式", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AuthMethod.entries.forEach { m ->
                FilterChip(
                    selected = current.method == m,
                    enabled = m == AuthMethod.PASSWORD || biometricAvailable,
                    onClick = {
                        error = null
                        if (m == AuthMethod.PASSWORD) {
                            vm.setAuthMethod(m)
                        } else {
                            // 先驗證一次，確認這支手機可以使用
                            Biometric.authenticate(context as FragmentActivity, "設定${m.label}登入", m) { ok, err ->
                                if (ok) vm.setAuthMethod(m) else error = err
                            }
                        }
                    },
                    label = { Text(m.label) },
                )
            }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (!biometricAvailable) Hint("這支手機尚未設定指紋或臉部辨識。")
        OutlinedButton(onClick = { vm.logout() }, modifier = Modifier.fillMaxWidth()) { Text("登出") }
    }
}

@Composable
private fun RefreshSection(vm: AppViewModel) {
    val seconds by vm.refreshSeconds.collectAsStateWithLifecycle()
    var text by rememberSaveable(seconds) { mutableStateOf(seconds.toString()) }
    var info by remember { mutableStateOf<String?>(null) }

    Section("即時股價") {
        NumberField(
            text, { text = it; info = null },
            "持股總覽自動刷新秒數",
            supporting = "預設 ${SettingsRepository.DEFAULT_REFRESH_SECONDS} 秒，最少 ${SettingsRepository.MIN_REFRESH_SECONDS} 秒",
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = {
                val s = text.trim().toIntOrNull()
                info = if (s == null || s < SettingsRepository.MIN_REFRESH_SECONDS) {
                    "請輸入 ${SettingsRepository.MIN_REFRESH_SECONDS} 以上的整數"
                } else {
                    vm.setRefreshSeconds(s)
                    "已儲存"
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("儲存") }
        info?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
    }
}

@Composable
private fun SyncSection(vm: AppViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sync by vm.sync.collectAsStateWithLifecycle()
    val syncing by vm.syncing.collectAsStateWithLifecycle()
    var confirmRestore by remember { mutableStateOf(false) }
    var restoreUri by remember { mutableStateOf<Uri?>(null) }

    // 備份／還原：用系統的檔案選擇畫面，可直接選 Google 雲端硬碟
    val backupFileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri -> uri?.let { vm.backupToFile(it) } }
    val restoreFileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> restoreUri = uri }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            runCatching { Identity.getAuthorizationClient(context).getAuthorizationResultFromIntent(result.data) }
                .onSuccess { vm.onGoogleLinked() }
                .onFailure { vm.onGoogleLinkFailed(it.message ?: "未知錯誤") }
        } else {
            vm.onGoogleLinkFailed("已取消")
        }
    }

    fun link() {
        scope.launch {
            runCatching { GoogleDrive.authorize(context) }
                .onSuccess { r ->
                    val pending = r.pendingIntent
                    if (r.hasResolution() && pending != null) {
                        launcher.launch(IntentSenderRequest.Builder(pending.intentSender).build())
                    } else {
                        vm.onGoogleLinked()
                    }
                }
                .onFailure {
                    vm.onGoogleLinkFailed("${it.message}（請確認已完成 Google Cloud 設定）")
                }
        }
    }

    Section("Google 雲端同步") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { backupFileLauncher.launch(vm.backupFileName()) },
                enabled = !syncing,
                modifier = Modifier.weight(1f),
            ) { Text("備份") }
            OutlinedButton(
                onClick = { restoreFileLauncher.launch(arrayOf("application/json", "application/octet-stream", "text/plain")) },
                enabled = !syncing,
                modifier = Modifier.weight(1f),
            ) { Text("還原") }
        }
        Hint("按「備份」後，在檔案畫面左上角選單選「雲端硬碟」，即可把備份檔存到 Google 雲端硬碟；按「還原」選擇備份檔即可還原。不需要另外設定。")
        HorizontalDivider()
        val autoOn = sync.auto && sync.googleLinked
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("自動同步", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
            Switch(
                checked = autoOn,
                enabled = !syncing,
                onCheckedChange = { on ->
                    if (!on) {
                        vm.setAutoSync(false, sync.intervalHours)
                    } else {
                        // 先記下要開啟，連結成功後就會開始排程；連結失敗會自動關回去
                        vm.setAutoSync(true, sync.intervalHours)
                        if (!sync.googleLinked) link()
                    }
                },
            )
        }
        Hint(
            if (autoOn) "已開啟：會依下方週期自動備份到 Google Drive 的 App 專用空間（在 Drive 網頁上看不到）。"
            else "開啟後會定時自動備份到 Google Drive。第一次開啟需要連結 Google 帳號（需先完成 Google Cloud 設定）。"
        )
        if (sync.googleLinked) {
            if (autoOn) {
                Text("自動備份週期", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SYNC_HOURS.forEach { h ->
                        FilterChip(
                            selected = sync.intervalHours == h,
                            onClick = { vm.setAutoSync(true, h) },
                            label = { Text("$h 小時") },
                        )
                    }
                }
                Hint("有網路時會在背景自動備份；實際執行時間由 Android 系統安排，可能稍有延後。")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { vm.backupNow() }, enabled = !syncing, modifier = Modifier.weight(1f)) {
                    Text(if (syncing) "同步中…" else "立即備份")
                }
                OutlinedButton(onClick = { confirmRestore = true }, enabled = !syncing, modifier = Modifier.weight(1f)) {
                    Text("從雲端還原")
                }
            }
            TextButton(onClick = { vm.unlinkGoogle() }) { Text("取消連結 Google 帳號") }
        }
        if (sync.lastSyncAt > 0) Hint("上次同步：${formatTime(sync.lastSyncAt)}")
        sync.lastResult?.let { Hint(it) }
    }

    restoreUri?.let { uri ->
        AlertDialog(
            onDismissRequest = { restoreUri = null },
            title = { Text("從備份檔還原？") },
            text = { Text("手機上目前這個帳號的交易、股利、分類與通知設定，會被備份檔的內容完全取代。") },
            confirmButton = {
                TextButton(onClick = { restoreUri = null; vm.restoreFromFile(uri) }) {
                    Text("還原", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { restoreUri = null }) { Text("取消") } },
        )
    }

    if (confirmRestore) {
        AlertDialog(
            onDismissRequest = { confirmRestore = false },
            title = { Text("從雲端還原？") },
            text = { Text("手機上目前這個帳號的交易和股利紀錄，會被雲端備份完全取代。") },
            confirmButton = {
                TextButton(onClick = { confirmRestore = false; vm.restoreNow() }) {
                    Text("還原", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmRestore = false }) { Text("取消") } },
        )
    }
}

@Composable
private fun ExportSection(vm: AppViewModel) {
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
    ) { uri -> uri?.let { vm.exportExcel(it) } }
    Section("匯出 Excel") {
        Hint("匯出交易紀錄、持股、股利、已實現損益、每月損益、質押共 6 個工作表。可存到手機或 Google 雲端硬碟。")
        Button(onClick = { launcher.launch(vm.exportFileName()) }, modifier = Modifier.fillMaxWidth()) {
            Text("匯出 Excel")
        }
    }
}

@Composable
private fun FeeSection(vm: AppViewModel) {
    val current = vm.settings.value
    var discount by rememberSaveable { mutableStateOf(plainNumber(current.twDiscount)) }
    var minFee by rememberSaveable { mutableStateOf(plainNumber(current.twMinFee)) }
    var usCommission by rememberSaveable { mutableStateOf(plainNumber(current.usCommission)) }
    var usDivTax by rememberSaveable { mutableStateOf(plainNumber(current.usDividendTaxRate * 100)) }
    var info by remember { mutableStateOf<String?>(null) }

    Section("手續費與稅率") {
        Text("台股", style = MaterialTheme.typography.labelLarge)
        NumberField(
            discount, { discount = it }, "手續費折扣",
            supporting = "例如 2.8 折填 0.28，沒有折扣填 1",
            modifier = Modifier.fillMaxWidth(),
        )
        NumberField(
            minFee, { minFee = it }, "最低手續費（元）",
            supporting = "多數券商為 20 元，零股可能較低",
            modifier = Modifier.fillMaxWidth(),
        )
        Text("美股", style = MaterialTheme.typography.labelLarge)
        NumberField(
            usCommission, { usCommission = it }, "每筆手續費（美元）",
            supporting = "多數美國券商為 0；複委託請填券商收取的金額",
            modifier = Modifier.fillMaxWidth(),
        )
        NumberField(
            usDivTax, { usDivTax = it }, "股利預扣稅率（%）",
            supporting = "台灣投資人一般為 30%",
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = {
                val d = discount.toAmountOrNull()
                val m = minFee.toAmountOrNull()
                val u = usCommission.toAmountOrNull()
                val t = usDivTax.toAmountOrNull()
                info = if (d == null || d <= 0 || d > 1 || m == null || m < 0 || u == null || u < 0 ||
                    t == null || t < 0 || t > 100
                ) {
                    "請輸入正確的數字（折扣需介於 0 到 1 之間）"
                } else {
                    vm.updateSettings(FeeSettings(d, m, u, t / 100))
                    "已儲存。之後新增的紀錄會用新設定計算，已存在的紀錄不會改變。"
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("儲存") }
        info?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
    }
}
