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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rboneone.stockrecord.data.AssetCategory
import com.rboneone.stockrecord.data.Market
import com.rboneone.stockrecord.data.TaxType
import com.rboneone.stockrecord.data.Trade
import com.rboneone.stockrecord.data.TradeType
import com.rboneone.stockrecord.domain.FeeCalculator
import com.rboneone.stockrecord.domain.Portfolio
import com.rboneone.stockrecord.domain.Valuation
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
fun TradeEditScreen(vm: AppViewModel, tradeId: Long, onDone: () -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val trades by vm.trades.collectAsStateWithLifecycle()
    val dividends by vm.dividends.collectAsStateWithLifecycle()
    val isNew = tradeId == 0L

    var loaded by rememberSaveable { mutableStateOf(isNew) }
    var market by rememberSaveable { mutableStateOf(Market.TW) }
    var type by rememberSaveable { mutableStateOf(TradeType.BUY) }
    var symbol by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("") }
    var date by rememberSaveable { mutableLongStateOf(LocalDate.now().toEpochDay()) }
    var sharesText by rememberSaveable { mutableStateOf("") }
    var priceText by rememberSaveable { mutableStateOf("") }
    var taxType by rememberSaveable { mutableStateOf(TaxType.STOCK) }
    var feeText by rememberSaveable { mutableStateOf("") }
    var taxText by rememberSaveable { mutableStateOf("") }
    // 使用者手動改過手續費 / 稅金後，就不再自動覆蓋
    var feeManual by rememberSaveable { mutableStateOf(!isNew) }
    var taxManual by rememberSaveable { mutableStateOf(!isNew) }
    var note by rememberSaveable { mutableStateOf("") }
    // 使用者自己輸入過名稱，就不再自動帶入；改代號時會重新自動查詢
    var nameManual by rememberSaveable { mutableStateOf(!isNew) }
    var error by remember { mutableStateOf<String?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var original by remember { mutableStateOf<Trade?>(null) }

    LaunchedEffect(tradeId) {
        if (isNew) return@LaunchedEffect
        val t = vm.getTrade(tradeId) ?: return@LaunchedEffect
        original = t
        if (!loaded) {
            market = t.market; type = t.type; symbol = t.symbol; name = t.name; date = t.date
            sharesText = plainNumber(t.shares); priceText = plainNumber(t.price)
            taxType = t.taxType; feeText = plainNumber(t.fee); taxText = plainNumber(t.tax)
            note = t.note
            loaded = true
        }
    }

    // 分類存在股票上：輸入代號後帶出目前的分類，使用者改過才會在儲存時寫回
    var category by rememberSaveable { mutableStateOf<AssetCategory?>(null) }
    var categoryTouched by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(market, symbol, loaded) {
        val sym = symbol.trim().uppercase()
        if (!loaded || categoryTouched || sym.isEmpty()) return@LaunchedEffect
        category = vm.getCategory(market, sym)
    }

    val nameHint = rememberStockNameLookup(vm, market, symbol, enabled = loaded && !nameManual) { name = it }

    val shares = sharesText.toAmountOrNull()
    val price = priceText.toAmountOrNull()
    val auto = FeeCalculator.calculate(market, type, shares ?: 0.0, price ?: 0.0, taxType, settings)
    LaunchedEffect(auto, feeManual, taxManual) {
        if (!feeManual) feeText = plainNumber(auto.fee)
        if (!taxManual) taxText = plainNumber(auto.tax)
    }

    fun save() {
        val sym = symbol.trim().uppercase()
        val fee = feeText.toAmountOrNull() ?: 0.0
        val tax = if (market == Market.TW && type == TradeType.SELL) taxText.toAmountOrNull() ?: 0.0 else 0.0
        error = when {
            sym.isEmpty() -> "請輸入股票代號"
            shares == null || shares <= 0 -> "請輸入正確的股數"
            price == null || price <= 0 -> "請輸入正確的價格"
            fee < 0 || tax < 0 -> "手續費和稅金不能是負數"
            type == TradeType.SELL && shares > Portfolio.sharesHeld(trades, market, sym, tradeId, dividends) + 1e-9 ->
                "賣出股數超過目前持有的 ${formatShares(Portfolio.sharesHeld(trades, market, sym, tradeId, dividends))} 股"
            else -> null
        }
        if (error != null) return
        if (categoryTouched) vm.setCategory(market, sym, category)
        vm.saveTrade(
            Trade(
                id = tradeId,
                market = market,
                symbol = sym,
                name = name.trim().ifEmpty { sym },
                type = type,
                date = date,
                shares = shares!!,
                price = price!!,
                fee = fee,
                tax = tax,
                taxType = taxType,
                note = note.trim(),
            )
        )
        onDone()
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        ScreenTopBar(
            title = if (isNew) "新增交易" else "編輯交易",
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
            ChipRow(Market.entries, market, { it.label }) {
                market = it
                taxType = Valuation.defaultTaxType(it, symbol.trim())
                if (symbol.isNotBlank()) nameManual = false
            }
            ChipRow(TradeType.entries, type, { it.label }) { type = it }

            OutlinedTextField(
                value = symbol,
                onValueChange = {
                    symbol = it
                    nameManual = false
                    taxType = Valuation.defaultTaxType(market, it.trim())
                },
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
                placeholder = { Text(if (market == Market.TW) "例如 台積電" else "例如 Apple") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Text("分類", style = MaterialTheme.typography.labelLarge)
            CategoryChips(category) { category = it; categoryTouched = true }
            if (category == null) {
                Text(
                    "尚未分類的持股不會計入資產配置",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedTextField(
                value = formatDate(date),
                onValueChange = {},
                readOnly = true,
                label = { Text("交易日期") },
                trailingIcon = {
                    IconButton(onClick = { showDatePicker = true }) {
                        Icon(Icons.Filled.DateRange, contentDescription = "選擇日期")
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                NumberField(
                    sharesText, { sharesText = it }, "股數",
                    supporting = if (market == Market.TW) "1 張 = 1000 股" else null,
                    modifier = Modifier.weight(1f),
                )
                NumberField(
                    priceText, { priceText = it },
                    if (market == Market.TW) "成交價（元）" else "成交價（美元）",
                    modifier = Modifier.weight(1f),
                )
            }
            if (shares != null && price != null) {
                Text(
                    "成交金額　${formatMoney(market, shares * price)}",
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            if (market == Market.TW && type == TradeType.SELL) {
                Text("證交稅類別", style = MaterialTheme.typography.labelLarge)
                ChipRow(TaxType.entries, taxType, { it.label }) { taxType = it }
            }

            NumberField(
                feeText, { feeText = it; feeManual = true }, "手續費",
                supporting = if (feeManual) null else autoFeeHint(market, settings.twDiscount),
                onResetAuto = if (feeManual) ({ feeManual = false }) else null,
                modifier = Modifier.fillMaxWidth(),
            )
            if (market == Market.TW && type == TradeType.SELL) {
                NumberField(
                    taxText, { taxText = it; taxManual = true }, "證交稅",
                    supporting = if (taxManual) null else "自動計算",
                    onResetAuto = if (taxManual) ({ taxManual = false }) else null,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
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
            title = { Text("刪除這筆交易？") },
            text = { Text("刪除後無法復原。") },
            confirmButton = {
                TextButton(onClick = {
                    original?.let { vm.deleteTrade(it) }
                    confirmDelete = false
                    onDone()
                }) { Text("刪除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("取消") } },
        )
    }
}

private fun autoFeeHint(market: Market, discount: Double): String = when (market) {
    Market.TW -> "自動計算：0.1425% × ${plainNumber(discount)} 折扣"
    Market.US -> "自動帶入設定裡的每筆手續費"
}

@Composable
private fun <T> ChipRow(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option ->
            FilterChip(
                selected = option == selected,
                onClick = { onSelect(option) },
                label = { Text(label(option)) },
            )
        }
    }
}

@Composable
fun NumberField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    /** 需要部分文字上色時使用，優先於 supporting */
    supportingAnnotated: AnnotatedString? = null,
    /** 有值時顯示「自動」按鈕，讓使用者恢復自動計算 */
    onResetAuto: (() -> Unit)? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        supportingText = supportingAnnotated?.let { { Text(it) } } ?: supporting?.let { { Text(it) } },
        trailingIcon = onResetAuto?.let { reset ->
            { TextButton(onClick = reset) { Text("自動") } }
        },
        modifier = modifier,
    )
}
