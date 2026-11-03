package com.rboneone.stockrecord.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import com.rboneone.stockrecord.data.CashFlow
import com.rboneone.stockrecord.data.CashFlowType
import com.rboneone.stockrecord.data.Market
import com.rboneone.stockrecord.ui.AppViewModel
import com.rboneone.stockrecord.ui.plainNumber
import com.rboneone.stockrecord.ui.toAmountOrNull
import java.time.LocalDate

/** 存入現金／領出現金。新增時帶入類型與市場，編輯既有紀錄時從資料庫讀取 */
@Composable
fun CashFlowEditScreen(
    vm: AppViewModel,
    flowId: Long,
    initialType: CashFlowType,
    initialMarket: Market,
    onDone: () -> Unit,
) {
    val isNew = flowId == 0L
    var loaded by rememberSaveable { mutableStateOf(isNew) }
    var type by rememberSaveable { mutableStateOf(initialType) }
    var market by rememberSaveable { mutableStateOf(initialMarket) }
    var amountText by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    var date by rememberSaveable { mutableLongStateOf(LocalDate.now().toEpochDay()) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var original by remember { mutableStateOf<CashFlow?>(null) }

    LaunchedEffect(flowId) {
        if (isNew) return@LaunchedEffect
        val f = vm.getCashFlow(flowId) ?: return@LaunchedEffect
        original = f
        if (!loaded) {
            type = f.type; market = f.market; amountText = plainNumber(f.amount); note = f.note; date = f.date
            loaded = true
        }
    }

    val amount = amountText.toAmountOrNull()
    val currency = if (market == Market.TW) "元" else "美元"

    fun save() {
        if (amount == null || amount <= 0) {
            error = "請輸入大於 0 的金額"
            return
        }
        vm.saveCashFlow(CashFlow(id = flowId, market = market, type = type, date = date, amount = amount, note = note.trim()))
        onDone()
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        ScreenTopBar(
            title = type.label,
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
            Text(
                "${market.label}帳戶（${if (market == Market.TW) "台幣" else "美元"}）",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            NumberField(
                amountText, { amountText = it; error = null }, "金額（$currency）",
                modifier = Modifier.fillMaxWidth(),
            )
            DateDropdowns(date) { date = it }
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

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("刪除這筆紀錄？") },
            text = { Text("刪除後無法復原。") },
            confirmButton = {
                TextButton(onClick = {
                    original?.let { vm.deleteCashFlow(it) }
                    confirmDelete = false
                    onDone()
                }) { Text("刪除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("取消") } },
        )
    }
}
