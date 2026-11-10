package com.rboneone.stockrecord.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rboneone.stockrecord.data.Market
import com.rboneone.stockrecord.data.StockSplit
import com.rboneone.stockrecord.domain.SplitMath
import com.rboneone.stockrecord.ui.AppViewModel
import com.rboneone.stockrecord.ui.formatDate
import com.rboneone.stockrecord.ui.formatShares
import java.time.LocalDate

/**
 * 股票分割設定：股票（依選擇帶入）、暫停交易日、恢復交易日、每股拆分（1 分 5 請輸入 5）。
 * 暫停交易日必須早於恢復交易日；到了恢復交易日，App 自動調整股數與價格。
 */
@Composable
fun SplitEditScreen(
    vm: AppViewModel,
    splitId: Long,
    initialMarket: Market,
    initialSymbol: String,
    onBack: () -> Unit,
    onDone: () -> Unit,
) {
    val holdings by vm.holdings.collectAsStateWithLifecycle()
    val isNew = splitId == 0L
    val today = LocalDate.now().toEpochDay()

    var loaded by rememberSaveable { mutableStateOf(isNew) }
    var market by rememberSaveable { mutableStateOf(initialMarket) }
    var symbol by rememberSaveable { mutableStateOf(initialSymbol) }
    var name by rememberSaveable { mutableStateOf("") }
    var suspendDate by rememberSaveable { mutableLongStateOf(today) }
    var resumeDate by rememberSaveable { mutableLongStateOf(today + 1) }
    var ratioText by rememberSaveable { mutableStateOf("") }
    var original by remember { mutableStateOf<StockSplit?>(null) }
    var showError by remember { mutableStateOf(false) }
    var confirmUndo by remember { mutableStateOf(false) }

    LaunchedEffect(splitId) {
        if (isNew) return@LaunchedEffect
        val s = vm.getSplit(splitId) ?: return@LaunchedEffect
        original = s
        if (!loaded) {
            market = s.market; symbol = s.symbol; name = s.name
            suspendDate = s.suspendDate; resumeDate = s.resumeDate; ratioText = s.ratio.toString()
            loaded = true
        }
    }

    val holding = holdings.firstOrNull { it.market == market && it.symbol == symbol }
    val displayName = name.ifBlank { holding?.name ?: symbol }
    val applied = original?.applied == true
    val ratio = ratioText.trim().toIntOrNull()
    val error = SplitMath.validate(suspendDate, resumeDate, ratio)
    // 日期設定錯誤馬上提示；每股拆分在按儲存、或已經輸入過之後才提示
    val shownError = when {
        suspendDate >= resumeDate -> error
        showError || ratioText.isNotEmpty() -> error
        else -> null
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        ScreenTopBar(
            title = if (isNew) "股票分割設定" else if (applied) "股票分割（已執行）" else "編輯股票分割",
            navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回") }
            },
            actions = {
                if (!isNew && !applied) {
                    IconButton(onClick = { confirmUndo = true }) { Icon(Icons.Filled.Delete, contentDescription = "刪除") }
                }
            },
        )
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // 股票：依點選代入
            ReadOnlyField("股票", "$displayName（$symbol・${market.label}）")
            if (holding != null) ReadOnlyField("目前持有", "${formatShares(holding.shares)} 股")

            if (applied) {
                // 已經執行：只能看，或還原
                ReadOnlyField("暫停交易日", formatDate(suspendDate))
                ReadOnlyField("恢復交易日", formatDate(resumeDate))
                ReadOnlyField("每股拆分", "1 分 ${original?.ratio}")
                Text(
                    "這筆分割已在恢復交易日自動調整完成：恢復交易日之前的買賣、股利、質押股數與通知價格，都已換算成分割後的股數與價格。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(onClick = { confirmUndo = true }, modifier = Modifier.fillMaxWidth()) { Text("還原這次分割") }
            } else {
                // 暫停交易日、恢復交易日：下拉選擇日期
                Text("暫停交易日", style = MaterialTheme.typography.labelLarge)
                DateDropdowns(suspendDate) { suspendDate = it }
                Text("恢復交易日", style = MaterialTheme.typography.labelLarge)
                DateDropdowns(resumeDate) { resumeDate = it }
                // 每股拆分：提示如 1 分 5，請輸入 5
                OutlinedTextField(
                    value = ratioText,
                    onValueChange = { ratioText = it.filter(Char::isDigit).take(4) },
                    label = { Text("每股拆分") },
                    placeholder = { Text("例如 5") },
                    singleLine = true,
                    isError = shownError != null && (suspendDate < resumeDate),
                    supportingText = { Text("1 股分成幾股？例如 1 分 5，請輸入 5") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                shownError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (error == null && ratio != null) {
                    Text(
                        buildString {
                            if (holding != null) append("分割後持有 ${formatShares(holding.shares * ratio)} 股（${formatShares(holding.shares)} × $ratio），每股成本變成 1/$ratio；")
                            append("金額與總成本不變。")
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    if (resumeDate <= today) {
                        Text("恢復交易日已經到了，儲存後會馬上調整。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                }
                Text(
                    "到了恢復交易日，App 會自動把這檔股票在恢復交易日之前的買賣、股利、質押股數與通知價格，" +
                        "調整成分割後的股數與價格（金額不變）。每次打開 App 與背景檢查時都會確認。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                    onClick = {
                        showError = true
                        if (error == null && ratio != null) {
                            vm.saveSplit(
                                StockSplit(
                                    id = splitId, market = market, symbol = symbol, name = displayName,
                                    suspendDate = suspendDate, resumeDate = resumeDate, ratio = ratio,
                                )
                            )
                            onDone()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("儲存") }
            }
        }
    }

    if (confirmUndo) {
        AlertDialog(
            onDismissRequest = { confirmUndo = false },
            title = { Text(if (applied) "還原這次分割？" else "刪除這筆分割設定？") },
            text = {
                Text(
                    if (applied) "買賣、股利、質押股數與通知價格會調回分割前的股數與價格，這筆分割設定也會刪除。"
                    else "這筆分割設定會刪除，到了恢復交易日也不會調整。"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmUndo = false
                    original?.let { vm.undoSplit(it) }
                    onDone()
                }) { Text(if (applied) "還原" else "刪除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmUndo = false }) { Text("取消") } },
        )
    }
}
