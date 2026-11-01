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
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rboneone.stockrecord.data.Dividend
import com.rboneone.stockrecord.data.Market
import com.rboneone.stockrecord.domain.FeeCalculator
import com.rboneone.stockrecord.domain.Portfolio
import com.rboneone.stockrecord.ui.AppViewModel
import com.rboneone.stockrecord.ui.formatDate
import com.rboneone.stockrecord.ui.formatMoney
import com.rboneone.stockrecord.ui.formatShares
import com.rboneone.stockrecord.ui.plainNumber
import com.rboneone.stockrecord.ui.toAmountOrNull
import java.time.LocalDate

private const val DAY_MILLIS = 86_400_000L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DividendEditScreen(vm: AppViewModel, dividendId: Long, onDone: () -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val trades by vm.trades.collectAsStateWithLifecycle()
    val dividends by vm.dividends.collectAsStateWithLifecycle()
    val isNew = dividendId == 0L

    var loaded by rememberSaveable { mutableStateOf(isNew) }
    var market by rememberSaveable { mutableStateOf(Market.TW) }
    var symbol by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("") }
    var nameManual by rememberSaveable { mutableStateOf(!isNew) }
    var date by rememberSaveable { mutableLongStateOf(LocalDate.now().toEpochDay()) }
    var cashText by rememberSaveable { mutableStateOf("") }
    var taxText by rememberSaveable { mutableStateOf("") }
    var taxManual by rememberSaveable { mutableStateOf(!isNew) }
    var feeText by rememberSaveable { mutableStateOf("") }
    var stockText by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var original by remember { mutableStateOf<Dividend?>(null) }

    LaunchedEffect(dividendId) {
        if (isNew) return@LaunchedEffect
        val d = vm.getDividend(dividendId) ?: return@LaunchedEffect
        original = d
        if (!loaded) {
            market = d.market; symbol = d.symbol; name = d.name; date = d.date
            cashText = plainNumber(d.cashAmount); taxText = plainNumber(d.tax); feeText = plainNumber(d.fee)
            stockText = plainNumber(d.stockShares); note = d.note
            loaded = true
        }
    }

    val nameHint = rememberStockNameLookup(vm, market, symbol, enabled = loaded && !nameManual) { name = it }

    val cash = cashText.toAmountOrNull()
    val autoTax = FeeCalculator.dividendTax(market, cash ?: 0.0, settings)
    LaunchedEffect(autoTax, taxManual) {
        if (!taxManual) taxText = plainNumber(autoTax)
    }

    val sym = symbol.trim().uppercase()
    val heldShares = Portfolio.sharesOn(
        trades.filter { it.market == market && it.symbol == sym },
        dividends.filter { it.market == market && it.symbol == sym && it.id != dividendId },
        date,
    )

    fun save() {
        val tax = taxText.toAmountOrNull() ?: 0.0
        val fee = feeText.toAmountOrNull() ?: 0.0
        val stock = stockText.toAmountOrNull() ?: 0.0
        val cashAmount = cash ?: 0.0
        error = when {
            sym.isEmpty() -> "請輸入股票代號"
            cashAmount < 0 || tax < 0 || fee < 0 || stock < 0 -> "金額和股數不能是負數"
            cashAmount <= 0 && stock <= 0 -> "請輸入現金股利或配股股數"
            tax + fee > cashAmount -> "扣款金額不能超過現金股利"
            else -> null
        }
        if (error != null) return
        vm.saveDividend(
            Dividend(
                id = dividendId,
                market = market,
                symbol = sym,
                name = name.trim().ifEmpty { sym },
                date = date,
                cashAmount = cashAmount,
                tax = tax,
                fee = fee,
                stockShares = stock,
                note = note.trim(),
            )
        )
        onDone()
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        ScreenTopBar(
            title = if (isNew) "新增股利" else "編輯股利",
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
            OutlinedTextField(
                value = formatDate(date),
                onValueChange = {},
                readOnly = true,
                label = { Text("發放日") },
                supportingText = if (sym.isNotEmpty()) ({ Text("當天持有 ${formatShares(heldShares)} 股") }) else null,
                trailingIcon = {
                    IconButton(onClick = { showDatePicker = true }) {
                        Icon(Icons.Filled.DateRange, contentDescription = "選擇日期")
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            NumberField(
                cashText, { cashText = it },
                if (market == Market.TW) "現金股利總額（元）" else "現金股利總額（美元）",
                supporting = "稅前金額",
                modifier = Modifier.fillMaxWidth(),
            )
            NumberField(
                taxText, { taxText = it; taxManual = true },
                if (market == Market.TW) "二代健保補充保費" else "預扣稅",
                supporting = if (taxManual) null else when (market) {
                    Market.TW -> "自動計算：單筆 2 萬元以上扣 2.11%"
                    Market.US -> "自動計算：預扣 ${plainNumber(settings.usDividendTaxRate * 100)}%"
                },
                onResetAuto = if (taxManual) ({ taxManual = false }) else null,
                modifier = Modifier.fillMaxWidth(),
            )
            NumberField(
                feeText, { feeText = it },
                "匯費 / 手續費",
                modifier = Modifier.fillMaxWidth(),
            )
            if (cash != null && cash > 0) {
                val net = cash - (taxText.toAmountOrNull() ?: 0.0) - (feeText.toAmountOrNull() ?: 0.0)
                Text("實際入帳　${formatMoney(market, net)}", style = MaterialTheme.typography.bodyLarge)
            }
            NumberField(
                stockText, { stockText = it },
                "股票股利（配股股數）",
                supporting = "沒有配股請留空",
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("備註（選填）") },
                modifier = Modifier.fillMaxWidth(),
            )
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
            title = { Text("刪除這筆股利？") },
            text = { Text("刪除後無法復原。") },
            confirmButton = {
                TextButton(onClick = {
                    original?.let { vm.deleteDividend(it) }
                    confirmDelete = false
                    onDone()
                }) { Text("刪除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("取消") } },
        )
    }
}
