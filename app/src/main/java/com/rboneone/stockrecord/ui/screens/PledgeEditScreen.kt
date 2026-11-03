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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rboneone.stockrecord.data.Market
import com.rboneone.stockrecord.data.Pledge
import com.rboneone.stockrecord.domain.PledgeCalculator
import com.rboneone.stockrecord.ui.AppViewModel
import com.rboneone.stockrecord.ui.formatAmount
import com.rboneone.stockrecord.ui.formatPrice
import com.rboneone.stockrecord.ui.formatShares
import com.rboneone.stockrecord.ui.plainNumber
import com.rboneone.stockrecord.ui.theme.ProfitRed
import com.rboneone.stockrecord.ui.toAmountOrNull
import java.time.LocalDate
import java.time.YearMonth

/**
 * 質押設定。新增時從「選擇質押股票」帶入股票代碼，可質押股數與可質押市值依代碼自動計算；
 * 編輯既有質押時，股票與質押股數固定，只能改金額、利率與日期。
 */
@Composable
fun PledgeEditScreen(
    vm: AppViewModel,
    pledgeId: Long,
    initialSymbol: String,
    onBack: () -> Unit,
    onDone: () -> Unit,
) {
    val quotes by vm.quotes.collectAsStateWithLifecycle()
    val candidates by vm.pledgeCandidates.collectAsStateWithLifecycle()
    val includeLeveraged by vm.pledgeIncludeLeveraged.collectAsStateWithLifecycle()
    val isNew = pledgeId == 0L

    var loaded by rememberSaveable { mutableStateOf(isNew) }
    var symbol by rememberSaveable { mutableStateOf(initialSymbol) }
    var name by rememberSaveable { mutableStateOf("") }
    var amountText by rememberSaveable { mutableStateOf("") }
    var rateText by rememberSaveable { mutableStateOf("") }
    var date by rememberSaveable { mutableLongStateOf(LocalDate.now().toEpochDay()) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var original by remember { mutableStateOf<Pledge?>(null) }

    LaunchedEffect(Unit) { vm.refreshQuotes(force = false) }
    LaunchedEffect(pledgeId) {
        if (isNew) return@LaunchedEffect
        val p = vm.getPledge(pledgeId) ?: return@LaunchedEffect
        original = p
        if (!loaded) {
            symbol = p.symbol; name = p.name
            amountText = plainNumber(p.amount); rateText = plainNumber(p.ratePct); date = p.date
            loaded = true
        }
    }

    val sym = symbol.trim().uppercase()
    val candidate = candidates.firstOrNull { it.symbol == sym }
    val leveragedHidden = isNew && candidate != null && candidate.leveraged && !includeLeveraged

    // 名稱：依代碼自動帶入（持股清單有就直接用，否則查詢）
    val nameHint = rememberStockNameLookup(vm, Market.TW, symbol, enabled = isNew && loaded) { name = it }
    LaunchedEffect(candidate?.name) { if (isNew && name.isBlank()) candidate?.name?.let { name = it } }

    val price = quotes[Market.TW to sym]?.price
    // 新增：可質押股數取自選擇清單的算法；編輯：沿用已儲存的質押股數
    val shares: Double? = if (isNew) candidate?.takeUnless { leveragedHidden }?.shares else original?.shares
    val marketValue = if (price != null && shares != null) price * shares else null

    val amount = amountText.toAmountOrNull()
    val rate = rateText.toAmountOrNull()
    val amountError = if (amountText.isBlank()) null else PledgeCalculator.amountError(amount)
    val ltv = if (amount != null && amount > 0) PledgeCalculator.ltvPct(amount, marketValue) else null
    // 編輯既有質押時，只在金額被改動時才檢查上限（股價下跌造成的超標不擋住修改日期等）
    val capApplies = isNew || amount != original?.amount
    val overLimit = ltv != null && capApplies && PledgeCalculator.exceedsMaxLtv(ltv)

    fun save() {
        error = when {
            sym.isEmpty() -> "請輸入股票代碼"
            leveragedHidden -> "「包含正二」已關閉，正二不列入可質押，請先開啟"
            shares == null || shares <= 0 -> "這檔股票沒有可質押股數：需持有 1,000 股以上，已質押的股數會扣除"
            price == null -> "尚未取得股價，請稍後再試"
            else -> PledgeCalculator.amountError(amount)
        } ?: when {
            rate == null || rate < 0 -> "請輸入質押利率（%）"
            overLimit -> "質押率 %.2f%% 超過上限 %.0f%%，無法新增，請降低質押金額".format(ltv, PledgeCalculator.MAX_LTV_PCT)
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
                note = original?.note.orEmpty(),
            )
        )
        onDone()
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        ScreenTopBar(
            title = if (isNew) "質押設定" else "編輯質押",
            navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回") }
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
            // A. 股票代碼：使用者輸入（編輯既有質押時固定）
            OutlinedTextField(
                value = symbol,
                onValueChange = { symbol = it; name = ""; error = null },
                label = { Text("股票代碼") },
                placeholder = { Text("例如 2330") },
                singleLine = true,
                readOnly = !isNew,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                modifier = Modifier.fillMaxWidth(),
            )
            // B. 股票名稱：自動帶入
            ReadOnlyField("股票名稱", name.ifBlank { "--" }, supporting = nameHint)
            // C. 股價：自動帶入
            ReadOnlyField("股價", price?.let { formatPrice(it) } ?: "--",
                supporting = if (sym.isNotEmpty() && price == null) "尚未取得股價，可到持股總覽按更新" else null)
            // D. 可質押股數、E. 可質押市值
            ReadOnlyField(
                "可質押股數", shares?.let { formatShares(it) } ?: "--",
                supporting = when {
                    sym.isEmpty() -> null
                    leveragedHidden -> "「包含正二」已關閉，正二不列入可質押"
                    shares == null -> "沒有可質押股數：需持有 1,000 股以上（零股不計，已質押的股數會扣除）"
                    else -> null
                },
                isError = sym.isNotEmpty() && shares == null,
            )
            ReadOnlyField("可質押市值", marketValue?.let { formatAmount(Market.TW, it) } ?: "--")

            // F. 質押金額：使用者輸入，千元為單位
            OutlinedTextField(
                value = amountText,
                onValueChange = { amountText = it; error = null },
                label = { Text("質押金額（元）") },
                singleLine = true,
                isError = amountError != null,
                supportingText = {
                    Text(amountError ?: "須為 1,000 的倍數，例如 5,000、12,000（5,100、5,010、5,001 不可）")
                },
                keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            // G. 質押利率（%）
            NumberField(
                rateText, { rateText = it; error = null }, "質押利率（%）",
                supporting = "年利率，例如 2.5% 填 2.5",
                modifier = Modifier.fillMaxWidth(),
            )
            // H. 質押率 = 質押金額 ÷ 可質押市值，最高 60%
            ReadOnlyField(
                "質押率（質押金額 ÷ 可質押市值）",
                ltv?.let { "%.2f%%".format(it) } ?: "--",
                supporting = if (overLimit) "超過上限 ${PledgeCalculator.MAX_LTV_PCT.toInt()}%，無法新增，請降低質押金額"
                else "上限 ${PledgeCalculator.MAX_LTV_PCT.toInt()}%",
                isError = overLimit,
                valueColor = if (overLimit) ProfitRed else Color.Unspecified,
            )
            // I. 利息預估 = 質押金額 × 質押利率（年）
            ReadOnlyField(
                "利息預估（年）",
                if (amount != null && amount > 0 && rate != null && rate >= 0)
                    formatAmount(Market.TW, amount * rate / 100) else "--",
                supporting = "質押金額 × 質押利率",
            )
            // J. 日期：預設今日，下拉選擇
            DateDropdowns(date) { date = it }

            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(onClick = ::save, enabled = !overLimit, modifier = Modifier.fillMaxWidth()) {
                Text(if (isNew) "新增" else "儲存")
            }
        }
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

@Composable
private fun ReadOnlyField(
    label: String,
    value: String,
    supporting: String? = null,
    isError: Boolean = false,
    valueColor: Color = Color.Unspecified,
) {
    OutlinedTextField(
        value = value,
        onValueChange = {},
        readOnly = true,
        label = { Text(label) },
        isError = isError,
        singleLine = true,
        supportingText = supporting?.let { { Text(it) } },
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = valueColor, fontWeight = FontWeight.Medium),
        modifier = Modifier.fillMaxWidth(),
    )
}

/** 年、月、日三個下拉選單；改月份時日期自動不超過該月天數 */
@Composable
private fun DateDropdowns(epochDay: Long, onChange: (Long) -> Unit) {
    val current = LocalDate.ofEpochDay(epochDay)
    val thisYear = LocalDate.now().year
    val years = (thisYear - 10..thisYear + 1).toList().reversed()

    fun pick(year: Int = current.year, month: Int = current.monthValue, day: Int = current.dayOfMonth) {
        val d = minOf(day, YearMonth.of(year, month).lengthOfMonth())
        onChange(LocalDate.of(year, month, d).toEpochDay())
    }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        DropdownField("年", "${current.year}", years.map { "$it" }, Modifier.weight(1.3f)) { pick(year = years[it]) }
        DropdownField("月", "${current.monthValue}", (1..12).map { "$it" }, Modifier.weight(1f)) { pick(month = it + 1) }
        val days = YearMonth.of(current.year, current.monthValue).lengthOfMonth()
        DropdownField("日", "${current.dayOfMonth}", (1..days).map { "$it" }, Modifier.weight(1f)) { pick(day = it + 1) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DropdownField(
    label: String,
    value: String,
    options: List<String>,
    modifier: Modifier,
    onSelect: (Int) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEachIndexed { i, text ->
                DropdownMenuItem(
                    text = { Text(text) },
                    onClick = { onSelect(i); expanded = false },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                )
            }
        }
    }
}
