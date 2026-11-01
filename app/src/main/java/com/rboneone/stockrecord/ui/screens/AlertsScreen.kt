package com.rboneone.stockrecord.ui.screens

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.rboneone.stockrecord.ui.theme.riseFallColored
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rboneone.stockrecord.alerts.AlertChecker
import com.rboneone.stockrecord.alerts.AlertNotifier
import com.rboneone.stockrecord.data.PriceAlert
import com.rboneone.stockrecord.data.Quote
import com.rboneone.stockrecord.ui.AppViewModel
import com.rboneone.stockrecord.ui.formatPct
import com.rboneone.stockrecord.ui.formatPrice
import com.rboneone.stockrecord.ui.formatSignedPrice
import com.rboneone.stockrecord.ui.theme.pnlColor

@Composable
fun AlertsScreen(vm: AppViewModel, onEdit: (Long) -> Unit) {
    val alerts by vm.alerts.collectAsStateWithLifecycle()
    val quotes by vm.quotes.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var granted by remember { mutableStateOf(AlertNotifier.hasPermission(context)) }
    var denied by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        granted = ok
        denied = !ok
    }
    // 從系統設定回來時重新檢查權限
    LifecycleResumeEffect(Unit) {
        granted = AlertNotifier.hasPermission(context)
        onPauseOrDispose { }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            ScreenTopBar("股價通知")
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (!granted) {
                    item(key = "permission") {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("尚未允許通知", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    if (denied) "請到系統設定開啟「股票記帳」的通知權限，否則條件達到時無法通知你。"
                                    else "需要允許通知權限，條件達到時才能通知你。",
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Button(onClick = {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !denied) {
                                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    } else {
                                        context.startActivity(
                                            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                        )
                                    }
                                }) { Text(if (denied) "開啟系統設定" else "允許通知") }
                            }
                        }
                    }
                }
                item(key = "info") {
                    Text(
                        "以今天相對昨日收盤的漲跌判斷，同一條件每個交易日只通知一次。" +
                            "App 開著時隨股價自動刷新檢查；App 關閉時約每 15 分鐘在背景檢查一次（省電模式下可能延後）。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (alerts.isEmpty()) {
                    item(key = "empty") { EmptyHint("還沒有設定任何通知\n按右下角「新增通知」") }
                }
                items(alerts, key = { it.id }) { alert ->
                    AlertCard(
                        alert = alert,
                        quote = quotes[alert.market to alert.symbol],
                        onClick = { onEdit(alert.id) },
                        onToggle = { vm.setAlertEnabled(alert, it) },
                    )
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = { onEdit(0) },
            icon = { Icon(Icons.Filled.Add, contentDescription = null) },
            text = { Text("新增通知") },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        )
    }
}

@Composable
private fun AlertCard(alert: PriceAlert, quote: Quote?, onClick: () -> Unit, onToggle: (Boolean) -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    "${alert.name.takeIf { it != alert.symbol } ?: ""} ${alert.symbol}".trim(),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    riseFallColored("${alert.market.label}・${alert.conditionText()}"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                if (quote != null && quote.previousClose > 0) {
                    val change = quote.price - quote.previousClose
                    Text(
                        "目前 ${formatPrice(quote.price)}　${formatSignedPrice(change)}（${formatPct(change / quote.previousClose * 100)}）",
                        style = MaterialTheme.typography.bodySmall,
                        color = pnlColor(change),
                    )
                    if (alert.lastTriggeredDay == AlertChecker.tradingDay(quote)) {
                        Text(
                            "今天已通知",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Switch(checked = alert.enabled, onCheckedChange = onToggle)
        }
    }
}
