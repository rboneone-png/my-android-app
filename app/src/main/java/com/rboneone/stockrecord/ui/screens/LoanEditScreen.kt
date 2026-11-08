package com.rboneone.stockrecord.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.rboneone.stockrecord.data.Loan
import com.rboneone.stockrecord.data.LoanType
import com.rboneone.stockrecord.domain.LoanMath
import com.rboneone.stockrecord.ui.AppViewModel
import com.rboneone.stockrecord.ui.formatDate
import com.rboneone.stockrecord.ui.plainNumber
import com.rboneone.stockrecord.ui.toAmountOrNull
import java.time.LocalDate

/**
 * 新增／編輯貸款：貸款型式、貸款金額、利率、貸款期數、已繳期數（預設 0）、每月繳款（自動計算）、繳款日。
 * 繳款日當天自動從帳務的可用餘額扣款，並顯示在「當日貸款金額」。
 */
@Composable
fun LoanEditScreen(vm: AppViewModel, loanId: Long, onDone: () -> Unit) {
    val isNew = loanId == 0L
    var loaded by rememberSaveable { mutableStateOf(isNew) }
    var type by rememberSaveable { mutableStateOf(LoanType.HOUSING) }
    var principalText by rememberSaveable { mutableStateOf("") }
    var rateText by rememberSaveable { mutableStateOf("") }
    var periodsText by rememberSaveable { mutableStateOf("") }
    var paidText by rememberSaveable { mutableStateOf("0") }
    var payDay by rememberSaveable { mutableIntStateOf(5) }
    var note by rememberSaveable { mutableStateOf("") }
    var startDate by rememberSaveable { mutableLongStateOf(LocalDate.now().toEpochDay()) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var original by remember { mutableStateOf<Loan?>(null) }

    LaunchedEffect(loanId) {
        if (isNew) return@LaunchedEffect
        val l = vm.getLoan(loanId) ?: return@LaunchedEffect
        original = l
        if (!loaded) {
            type = l.type; principalText = plainNumber(l.principal); rateText = plainNumber(l.annualRatePct)
            periodsText = l.totalPeriods.toString(); paidText = l.paidPeriods.toString(); payDay = l.payDay
            note = l.note; startDate = l.startDate
            loaded = true
        }
    }

    val principal = principalText.toAmountOrNull()
    val rate = rateText.toAmountOrNull()
    val periods = periodsText.trim().toIntOrNull()
    val paid = paidText.trim().ifEmpty { "0" }.toIntOrNull()

    fun validate(): String? = when {
        principal == null || principal <= 0 -> "請輸入大於 0 的貸款金額"
        rate == null || rate < 0 || rate > 100 -> "利率請輸入 0～100 之間的數字（單位 %）"
        periods == null || periods < 1 || periods > LoanMath.MAX_PERIODS -> "貸款期數請輸入 1～${LoanMath.MAX_PERIODS} 的整數（每月一期）"
        paid == null || paid < 0 -> "已繳期數請輸入 0 以上的整數"
        paid > periods -> "已繳期數不能超過貸款期數"
        else -> null
    }

    // F. 每月繳款：由貸款金額、利率、期數計算（本息平均攤還）
    val monthly = if (principal != null && rate != null && periods != null && principal > 0 && rate in 0.0..100.0 && periods in 1..LoanMath.MAX_PERIODS) {
        LoanMath.monthlyPayment(principal, rate, periods)
    } else null
    val remaining = if (periods != null && paid != null) (periods - paid).coerceAtLeast(0) else null

    fun save() {
        val msg = validate()
        if (msg != null) { error = msg; return }
        vm.saveLoan(
            Loan(
                id = loanId, type = type, principal = principal!!, annualRatePct = rate!!, totalPeriods = periods!!,
                paidPeriods = paid!!, payDay = payDay, startDate = startDate, note = note.trim(),
            )
        )
        onDone()
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        ScreenTopBar(
            title = if (isNew) "新增貸款" else "編輯貸款",
            navigationIcon = {
                IconButton(onClick = onDone) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回") }
            },
            actions = {
                if (!isNew) {
                    IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, contentDescription = "刪除") }
                }
            },
        )
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // A. 貸款型式
            Text("貸款型式", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LoanType.entries.forEach { t ->
                    FilterChip(selected = type == t, onClick = { type = t }, label = { Text(t.label) })
                }
            }
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("名稱／備註（選填）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            // B. 貸款金額
            NumberField(principalText, { principalText = it; error = null }, "貸款金額（元）", Modifier.fillMaxWidth())
            // C. 利率
            NumberField(rateText, { rateText = it; error = null }, "利率（年利率 %）", Modifier.fillMaxWidth())
            // D. 貸款期數
            OutlinedTextField(
                value = periodsText,
                onValueChange = { periodsText = it.filter(Char::isDigit).take(3); error = null },
                label = { Text("貸款期數（每月一期）") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                supportingText = { Text("例如 20 年 = 240 期") },
                modifier = Modifier.fillMaxWidth(),
            )
            // E. 已繳期數，預設 0
            OutlinedTextField(
                value = paidText,
                onValueChange = { paidText = it.filter(Char::isDigit).take(3); error = null },
                label = { Text("已繳期數") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                supportingText = {
                    Text(
                        if (remaining != null) "建立時已繳的期數；之後每個繳款日自動再繳一期，尚餘 $remaining 期"
                        else "建立時已繳的期數；之後每個繳款日自動再繳一期"
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            )
            // F. 每月繳款：自動計算
            OutlinedTextField(
                value = monthly?.let { "%,d".format(it) } ?: "",
                onValueChange = {},
                readOnly = true,
                label = { Text("每月繳款（自動計算，元）") },
                singleLine = true,
                supportingText = { Text("本息平均攤還：貸款金額、利率、期數算出每月固定金額，四捨五入到整數") },
                modifier = Modifier.fillMaxWidth(),
            )
            // G. 繳款日
            DropdownField("繳款日（每月幾號）", "$payDay 日", (1..31).map { "$it 日" }, Modifier.fillMaxWidth()) { payDay = it + 1 }
            Text(
                "繳款日當天會自動從「當日可用餘額」扣除每月繳款，顯示在帳務的「當日貸款金額」；" +
                    "從${formatDate(startDate)}（建立日）之後的第一個繳款日開始扣，繳滿期數後停止。" +
                    "該月沒有這一天（例如 31 日遇到 2 月）時，改在當月最後一天扣款。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(onClick = ::save, modifier = Modifier.fillMaxWidth()) { Text("儲存") }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("刪除這筆貸款？") },
            text = { Text("刪除後，這筆貸款已經扣掉的款項也會一起從可用餘額加回來，無法復原。") },
            confirmButton = {
                TextButton(onClick = {
                    original?.let { vm.deleteLoan(it) }
                    confirmDelete = false
                    onDone()
                }) { Text("刪除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("取消") } },
        )
    }
}
