package com.rboneone.stockrecord.ui.screens

import android.app.Activity
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.Icon
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import com.rboneone.stockrecord.ui.theme.AppCard as Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.rboneone.stockrecord.export.ImportResult
import com.rboneone.stockrecord.export.ImportMode
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rboneone.stockrecord.auth.AuthMethod
import com.rboneone.stockrecord.auth.Biometric
import com.rboneone.stockrecord.data.SettingsRepository
import com.rboneone.stockrecord.domain.FeeSettings
import com.rboneone.stockrecord.ui.AppViewModel
import com.rboneone.stockrecord.ui.formatTime
import com.rboneone.stockrecord.ui.plainNumber
import com.rboneone.stockrecord.ui.toAmountOrNull
import kotlinx.coroutines.launch


@Composable
fun SettingsScreen(vm: AppViewModel, onOpenAlerts: () -> Unit) {
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
            ThemeSection(vm)
            AlertsEntry(vm, onOpenAlerts)
            SyncSection(vm)
            ExportSection(vm)
            FeeSection(vm)
        }
    }
}

/** 設定項目：平常只顯示標題，點一下展開或收合，預設收合 */
@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    var expanded by rememberSaveable(title) { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth()) {
        Column {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(onClickLabel = if (expanded) "收合" else "展開") { expanded = !expanded }
                    .padding(horizontal = 12.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    if (expanded) Icons.Filled.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                )
                Text(title, Modifier.padding(start = 4.dp).weight(1f), style = MaterialTheme.typography.titleMedium)
            }
            if (expanded) {
                Column(
                    Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    content()
                }
            }
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

    // 自動備份檔：建立時要求長期的讀寫權限，背景備份才能一直寫入同一個檔案
    val autoFileLauncher = rememberLauncherForActivityResult(PersistableCreateDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
            val name = runCatching {
                context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
                    ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
            }.getOrNull() ?: "自動備份檔"
            vm.setAutoFile(uri, name)
        }
    }

    Section("雲端備份") {
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
        // 開關一直都在：沒有備份檔時打開會先請你選檔案；關掉只是暫停，檔案設定會保留
        val autoOn = sync.auto && sync.autoFileUri != null
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("自動備份", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
            Text(if (autoOn) "開" else "關", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(8.dp))
            Switch(
                checked = autoOn,
                enabled = !syncing,
                onCheckedChange = { on ->
                    when {
                        !on -> vm.setAutoSync(false)
                        sync.autoFileUri == null -> autoFileLauncher.launch(vm.autoBackupFileName())
                        else -> vm.setAutoSync(true)
                    }
                },
            )
        }
        if (sync.autoFileUri == null) {
            Hint("選一個備份檔（建議存到「雲端硬碟」），之後 App 會定時自動把資料寫進同一個檔案。不需要 Google Cloud 設定，也不會過期。")
            OutlinedButton(
                onClick = { autoFileLauncher.launch(vm.autoBackupFileName()) },
                enabled = !syncing,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (syncing) "備份中…" else "選擇自動備份檔案") }
        } else {
            Hint("備份檔：${sync.autoFileName ?: "已選擇"}　目前：${if (autoOn) "自動備份已開啟" else "自動備份已關閉（不會定時備份，仍可手動立即備份）"}")
            if (autoOn) {
                IntervalEditor(sync.intervalDays, sync.intervalHours) { d, h -> vm.setAutoInterval(d, h) }
                Hint("有網路時會在背景自動寫入備份檔；實際執行時間由 Android 系統安排，可能稍有延後。")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { vm.backupNow() }, enabled = !syncing, modifier = Modifier.weight(1f)) {
                    Text(if (syncing) "備份中…" else "立即備份")
                }
                OutlinedButton(onClick = { confirmRestore = true }, enabled = !syncing, modifier = Modifier.weight(1f)) {
                    Text("從備份檔還原")
                }
            }
            TextButton(onClick = { vm.clearAutoFile() }) { Text("更換或取消自動備份檔案") }
        }
        if (sync.lastSyncAt > 0) Hint("上次備份：${formatTime(sync.lastSyncAt)}")
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
            title = { Text("從備份檔還原？") },
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
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var parsed by remember { mutableStateOf<ImportResult?>(null) }
    var confirmReplace by remember { mutableStateOf(false) }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            vm.readExcel(uri)
                .onSuccess { parsed = it }
                .onFailure { Toast.makeText(context, "匯入失敗：${it.message}", Toast.LENGTH_LONG).show() }
        }
    }
    Section("匯出／匯入 Excel") {
        Hint("匯出：交易紀錄、持股、股利、已實現損益、每月損益、質押共 6 個工作表，可存到手機或 Google 雲端硬碟。")
        Button(onClick = { launcher.launch(vm.exportFileName()) }, modifier = Modifier.fillMaxWidth()) {
            Text("匯出 Excel")
        }
        HorizontalDivider()
        Hint(
            "匯入：讀取「交易紀錄」「股利」兩個工作表（格式同匯出，依標題列名稱對應欄位，順序可不同）。" +
                "手續費、交易稅、扣稅留空時，依目前的費率設定自動計算。建議先匯出一次當範本，改完再匯入。"
        )
        OutlinedButton(
            onClick = { importLauncher.launch(arrayOf("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "application/octet-stream")) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("匯入 Excel") }
    }

    parsed?.let { p ->
        AlertDialog(
            onDismissRequest = { parsed = null },
            title = { Text("匯入 Excel") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("讀到 ${p.trades.size} 筆買賣、${p.dividends.size} 筆股利。")
                    if (p.errors.isNotEmpty()) {
                        Text("有 ${p.errors.size} 處讀不懂，這些列不會匯入：", color = MaterialTheme.colorScheme.error)
                        p.errors.take(8).forEach { Text("・$it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                        if (p.errors.size > 8) Text("…還有 ${p.errors.size - 8} 處", style = MaterialTheme.typography.bodySmall)
                    }
                    Text(
                        "「新增」會加到現有資料，和現有紀錄完全相同的會略過（同一個檔案匯入兩次不會重複）；" +
                            "「取代」會先刪除現有的所有買賣與股利再匯入。匯入後若賣出超過持有股數，會整批不匯入。",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = p.trades.isNotEmpty() || p.dividends.isNotEmpty(),
                    onClick = { vm.applyImport(p, ImportMode.ADD); parsed = null },
                ) { Text("新增") }
            },
            dismissButton = {
                Row {
                    TextButton(
                        enabled = p.trades.isNotEmpty() || p.dividends.isNotEmpty(),
                        onClick = { confirmReplace = true },
                    ) { Text("取代現有資料", color = MaterialTheme.colorScheme.error) }
                    TextButton(onClick = { parsed = null }) { Text("取消") }
                }
            },
        )
    }
    if (confirmReplace && parsed != null) {
        AlertDialog(
            onDismissRequest = { confirmReplace = false },
            title = { Text("取代現有資料？") },
            text = { Text("會刪除目前所有買賣與股利紀錄，改用 Excel 裡的內容，無法復原。建議先到「雲端備份」備份一次。") },
            confirmButton = {
                TextButton(onClick = {
                    parsed?.let { vm.applyImport(it, ImportMode.REPLACE) }
                    confirmReplace = false; parsed = null
                }) { Text("取代", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmReplace = false }) { Text("取消") } },
        )
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

/** 建立檔案時要求「長期」讀寫權限，讓背景工作之後還能寫入同一個檔案 */
private class PersistableCreateDocument : ActivityResultContracts.CreateDocument("application/json") {
    override fun createIntent(context: android.content.Context, input: String): android.content.Intent =
        super.createIntent(context, input).addFlags(
            android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                android.content.Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION,
        )
}

/** 自訂自動備份週期：天數＋小時數，按「套用」才生效；合計至少 1 小時 */
@Composable
private fun IntervalEditor(days: Int, hours: Int, onApply: (Int, Int) -> Boolean) {
    var d by rememberSaveable(days, hours) { mutableStateOf(days.toString()) }
    var h by rememberSaveable(days, hours) { mutableStateOf(hours.toString()) }
    var info by remember { mutableStateOf<String?>(null) }
    Text("自動備份週期", style = MaterialTheme.typography.labelLarge)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = d, onValueChange = { d = it.filter(Char::isDigit).take(3); info = null },
            label = { Text("天") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f),
        )
        OutlinedTextField(
            value = h, onValueChange = { h = it.filter(Char::isDigit).take(4); info = null },
            label = { Text("小時") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f),
        )
        OutlinedButton(onClick = {
            val ok = onApply(d.toIntOrNull() ?: 0, h.toIntOrNull() ?: 0)
            info = if (ok) "已套用" else "週期至少 1 小時，最多 365 天"
        }) { Text("套用") }
    }
    info?.let { Hint(it) }
}

/** 配色模式：淺色模式（預設）、深色模式，立即套用 */
@Composable
private fun ThemeSection(vm: AppViewModel) {
    val dark by vm.darkTheme.collectAsStateWithLifecycle()
    Section("配色模式") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = !dark, onClick = { vm.setDarkTheme(false) }, label = { Text("淺色模式") })
            FilterChip(selected = dark, onClick = { vm.setDarkTheme(true) }, label = { Text("深色模式") })
        }
        Hint("預設為淺色模式；選擇後馬上生效，登入頁也會套用。")
    }
}

/** 股價通知：點一下進入通知清單與設定（原本在底部導覽列） */
@Composable
private fun AlertsEntry(vm: AppViewModel, onOpen: () -> Unit) {
    val alerts by vm.alerts.collectAsStateWithLifecycle()
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(horizontal = 12.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
            Text("股價通知", Modifier.padding(start = 4.dp).weight(1f), style = MaterialTheme.typography.titleMedium)
            Text(
                "${alerts.size} 筆",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 4.dp),
            )
        }
    }
}
