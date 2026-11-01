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
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rboneone.stockrecord.data.Market
import com.rboneone.stockrecord.data.Pledge
import com.rboneone.stockrecord.ui.AppViewModel
import com.rboneone.stockrecord.ui.formatAmount
import com.rboneone.stockrecord.ui.formatDate
import com.rboneone.stockrecord.ui.formatPrice
import com.rboneone.stockrecord.ui.plainNumber
import com.rboneone.stockrecord.ui.theme.pledgeColor
import com.rboneone.stockrecord.ui.toAmountOrNull
import java.time.LocalDate

private const val DAY_MILLIS = 86_400_000L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PledgeEditScreen(vm: AppViewModel, pledgeId: Long, onDone: () -> Unit) {
    val quotes by vm.quotes.collectAsStateWithLifecycle()
    val isNew = pledgeId == 0L

    var loaded by rememberSaveable { mutableStateOf(isNew) }
    var symbol by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("") }
    var nameManual by rememberSaveable { mutableStateOf(!isNew) }
    var sharesText by rememberSaveable { mutableStateOf("") }
    var amountText by rememberSaveable { mutableStateOf("") }
    var rateText by rememberSaveable { mutableStateOf("") }
    var date by rememberSaveable { mutableLongStateOf(LocalDate.now().toEpochDay()) }
    var note by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var original by remember { mutableStateOf<Pledge?>(null) }

    LaunchedEffect(pledgeId) {
        if (isNew) return@LaunchedEffect
        val p = vm.getPledge(pledgeId) ?: return@LaunchedEffect
        original = p
        if (!loaded) {
            symbol = p.symbol; name = p.name; sharesText = plainNumber(p.shares)
            amountText = plainNumber(p.amount); rateText = plainNumber(p.ratePct); date = p.date; note = p.note
            loaded = true
        }
    }

    // 質押只限台股
    val nameHint = rememberStockNameLookup(vm, Market.TW, symbol, enabled = loaded && !nameManual) { name = it }
    val sym = symbol.trim().uppercase()
    val shares = sharesText.toAmountOrNull()
    val amount = amountText.toAmountOrNull()
    val rate = rateText.toAmountOrNull()
    val price = quotes[Market.TW to sym]?.price

    fun save() {
        error = when {
            sym.isEmpty() -> "請輸入股票代碼"
            shares == null || shares <= 0 -> "請輸入正確的質押股數"
            amount == null || amount <= 0 -> "請輸入正確的質押金額"
            rate == null || rate < 0 -> "請輸入正確的質押利率"
            else -> null
        }
        if (error != null) return
        vm.savePledge(
            Pledge(
                id = pledgeId,
                symbol = sym,
                name = name.trim().ifEmpty { sym },
                shares = shares!!,
                amount = amount!!,
                ratePct = rate!!,
                date = date,
                note = note.trim(),
            )
        )
        onDone()
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        ScreenTopBar(
            title = if (isNew) "新增質押" else "編輯質押",
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
            OutlinedTextField(
                value = symbol,
                onValueChange = { symbol = it; nameManual = false },
                label = { Text("股票代碼") },
                placeholder = { Text("例如 2330") },
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
            NumberField(
                sharesText, { sharesText = it }, "質押股數",
                supporting = "1 張 = 1000 股",
                modifier = Modifier.fillMaxWidth(),
            )
            NumberField(amountText, { amountText = it }, "質押金額（元）", modifier = Modifier.fillMaxWidth())
            NumberField(
                rateText, { rateText = it }, "質押利率（%）",
                supporting = "年利率，例如 2.5% 填 2.5",
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = formatDate(date),
                onValueChange = {},
                readOnly = true,
                label = { Text("日期") },
                trailingIcon = {
                    IconButton(onClick = { showDatePicker = true }) {
                        Icon(Icons.Filled.DateRange, contentDescription = "選擇日期")
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("備註（選填）") },
                modifier = Modifier.fillMaxWidth(),
            )

            // 即時試算
            if (amount != null && amount > 0) {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        rate?.let { LabelValue("利息支出（年）", formatAmount(Market.TW, amount * it / 100)) }
                        if (price != null && shares != null && shares > 0) {
                            val ratio = shares * price / amount * 100
                            LabelValue("目前股價", formatPrice(price))
                            LabelValue("質押市值", formatAmount(Market.TW, shares * price))
                            androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                                Text("維持率", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    "%.1f%%".format(ratio),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = pledgeColor(ratio),
                                )
                            }
                        } else if (sym.isNotEmpty()) {
                            Text(
                                "儲存後會自動抓取股價並計算維持率",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(onClick = ::save, modifier = Modifier.fillMaxWidth()) { Text("儲存") }
        }
    }

    if (showDatePicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = date * DAY_MILLIS)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { date = it / DAY_MILLIS }
                    showDatePicker = false
                }) { Text("確定") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("取消") } },
        ) { DatePicker(state) }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("刪除這筆質押？") },
            text = { Text("刪除後無法復原。") },
            confirmButton = {
                TextButton(onClick = {
                    original?.let { vm.deletePledge(it) }
                    confirmDelete = false
                    onDone()
                }) { Text("刪除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("取消") } },
        )
    }
}
