package com.rboneone.stockrecord.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.rboneone.stockrecord.data.AlertType
import com.rboneone.stockrecord.data.Market
import com.rboneone.stockrecord.data.PriceAlert
import com.rboneone.stockrecord.ui.AppViewModel
import com.rboneone.stockrecord.ui.plainNumber
import com.rboneone.stockrecord.ui.toAmountOrNull
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlertEditScreen(vm: AppViewModel, alertId: Long, onDone: () -> Unit) {
    val isNew = alertId == 0L
    var loaded by rememberSaveable { mutableStateOf(isNew) }
    var market by rememberSaveable { mutableStateOf(Market.TW) }
    var symbol by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("") }
    var nameManual by rememberSaveable { mutableStateOf(!isNew) }
    var type by rememberSaveable { mutableStateOf(AlertType.RISE_PCT) }
    var thresholdText by rememberSaveable { mutableStateOf("") }
    var enabled by rememberSaveable { mutableStateOf(true) }
    var expanded by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var original by remember { mutableStateOf<PriceAlert?>(null) }

    LaunchedEffect(alertId) {
        if (isNew) return@LaunchedEffect
        val a = vm.getAlert(alertId) ?: return@LaunchedEffect
        original = a
        if (!loaded) {
            market = a.market; symbol = a.symbol; name = a.name; type = a.type
            thresholdText = plainNumber(abs(a.threshold)); enabled = a.enabled
            loaded = true
        }
    }

    val nameHint = rememberStockNameLookup(vm, market, symbol, enabled = loaded && !nameManual) { name = it }
    val unit = when {
        type.isPercent -> "%"
        market == Market.TW -> "元"
        else -> "美元"
    }

    fun save() {
        val sym = symbol.trim().uppercase()
        val t = thresholdText.toAmountOrNull()?.let { abs(it) }
        error = when {
            sym.isEmpty() -> "請輸入股票代號"
            t == null || t <= 0 -> "請輸入大於 0 的設定值"
            else -> null
        }
        if (error != null) return
        // 修改條件後重新開始計算，今天符合也會再通知
        vm.saveAlert(
            PriceAlert(
                id = alertId,
                market = market,
                symbol = sym,
                name = name.trim().ifEmpty { sym },
                type = type,
                threshold = t!!,
                enabled = enabled,
            )
        )
        onDone()
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        ScreenTopBar(
            title = if (isNew) "新增通知" else "編輯通知",
            navigationIcon = {
                IconButton(onClick = onDone) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                }
            },
            actions = {
                if (!isNew) {
                    IconButton(onClick = { confirmDelete = true }) {
                        Icon(Icons.Filled.Delete, contentDescription = "刪除")
                    }
                }
            },
        )
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Market.entries.forEach { m ->
                    FilterChip(
                        selected = market == m,
                        onClick = { market = m; if (symbol.isNotBlank()) nameManual = false },
                        label = { Text(m.label) },
                    )
                }
            }
            OutlinedTextField(
                value = symbol,
                onValueChange = { symbol = it; nameManual = false },
                label = { Text("股票代號") },
                placeholder = { Text(if (market == Market.TW) "例如 2330" else "例如 AAPL") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = name,
                onValueChange = { name = it; nameManual = true },
                label = { Text("股票名稱") },
                supportingText = nameHint?.let { { Text(it) } },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                OutlinedTextField(
                    value = type.label,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("通知條件") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
                )
                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    AlertType.entries.forEach { t ->
                        DropdownMenuItem(
                            text = { Text(t.label) },
                            onClick = { type = t; expanded = false },
                            contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                        )
                    }
                }
            }

            NumberField(
                thresholdText, { thresholdText = it; error = null },
                if (type == AlertType.PRICE_ABOVE || type == AlertType.PRICE_BELOW) "股價（$unit）" else "設定值（$unit）",
                supporting = conditionHint(type, unit, thresholdText.toAmountOrNull()?.let { abs(it) }),
                modifier = Modifier.fillMaxWidth(),
            )
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(onClick = ::save, modifier = Modifier.fillMaxWidth()) { Text("儲存") }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("刪除這個通知？") },
            confirmButton = {
                TextButton(onClick = {
                    original?.let { vm.deleteAlert(it) }
                    confirmDelete = false
                    onDone()
                }) { Text("刪除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("取消") } },
        )
    }
}

/** 用一句話說明什麼情況會通知 */
private fun conditionHint(type: AlertType, unit: String, value: Double?): String {
    val v = value?.let { plainNumber(it) } ?: "X"
    return when (type) {
        AlertType.RISE_AMOUNT -> "今天上漲 $v $unit（含）以上時通知"
        AlertType.FALL_AMOUNT -> "今天下跌 $v $unit（含）以上時通知"
        AlertType.RISE_PCT -> "今天漲幅達 $v%（含）以上時通知"
        AlertType.FALL_PCT -> "今天跌幅達 $v%（含）以上時通知"
        AlertType.PRICE_ABOVE -> "股價漲到 $v $unit（含）以上時通知"
        AlertType.PRICE_BELOW -> "股價跌到 $v $unit（含）以下時通知"
    }
}
