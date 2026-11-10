package com.rboneone.stockrecord.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rboneone.stockrecord.data.Market
import com.rboneone.stockrecord.data.Pledge
import com.rboneone.stockrecord.domain.PledgeCalculator
import com.rboneone.stockrecord.ui.AppViewModel
import com.rboneone.stockrecord.ui.formatAmount
import com.rboneone.stockrecord.ui.formatDate
import com.rboneone.stockrecord.ui.formatPrice
import com.rboneone.stockrecord.ui.formatShares
import java.time.LocalDate

/**
 * 質借歸還設定：帶入這筆質押的參數（股票、質押股數、質借金額、利率、質押日期，與質押設定同步），
 * 選歸還日期後，系統依質押天數算利息（質借金額 × 年利率 × 天數 ÷ 365）。
 * 歸還後質借金額與利息都會從帳務管理的可用餘額扣除，這筆質押不再計入質借總金額、維持率與已質押股數。
 */
@Composable
fun PledgeReturnScreen(vm: AppViewModel, pledgeId: Long, onBack: () -> Unit, onDone: () -> Unit) {
    val quotes by vm.quotes.collectAsStateWithLifecycle()
    var pledge by remember { mutableStateOf<Pledge?>(null) }
    var missing by remember { mutableStateOf(false) }
    var returnDate by rememberSaveable { mutableLongStateOf(LocalDate.now().toEpochDay()) }
    var loaded by rememberSaveable { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmCancel by remember { mutableStateOf(false) }

    LaunchedEffect(pledgeId) {
        val p = vm.getPledge(pledgeId)
        if (p == null) { missing = true; return@LaunchedEffect }
        pledge = p
        if (!loaded) {
            // 已經歸還過的，帶入原本的歸還日；質押中的預設今天
            returnDate = p.returnDate ?: maxOf(LocalDate.now().toEpochDay(), p.date)
            loaded = true
        }
    }

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(
            title = if (pledge?.isActive == false) "編輯質借歸還" else "質借歸還設定",
            navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回") }
            },
        )
        val p = pledge
        if (p == null) {
            EmptyHint(if (missing) "找不到這筆質押" else "載入中…")
            return@Column
        }
        val today = LocalDate.now().toEpochDay()
        val price = quotes[Market.TW to p.symbol]?.price
        val days = p.daysTo(returnDate)
        val interest = p.interestForDays(days)
        val total = Math.round(p.amount) + interest
        val maintenance = PledgeCalculator.maintenancePct(p.shares, price, p.amount)

        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // 與質押設定相同的參數（唯讀）
            ReadOnlyField("股票代碼", p.symbol)
            ReadOnlyField("股票名稱", p.name.ifBlank { "--" })
            ReadOnlyField("股價", price?.let { formatPrice(it) } ?: "--")
            ReadOnlyField("質押股數", "${formatShares(p.shares)} 股")
            ReadOnlyField("質借金額", formatAmount(Market.TW, p.amount) + " 元")
            ReadOnlyField("利率（年）", "%.2f%%".format(p.ratePct))
            ReadOnlyField("維持率", maintenance?.let { "%.2f%%".format(it) } ?: "--")
            ReadOnlyField("質押日期", formatDate(p.date))
            HorizontalDivider()
            // 歸還日期：使用者選，不早於質押日、不晚於今天
            Text("歸還日期", style = MaterialTheme.typography.labelLarge)
            DateDropdowns(returnDate) { returnDate = it; error = null }
            ReadOnlyField("質押天數", "$days 天", supporting = "質押日 ${formatDate(p.date)} 到歸還日 ${formatDate(returnDate)}（質押當天歸還為 0 天）")
            ReadOnlyField(
                "利息（元）", "%,d".format(interest),
                supporting = "= 質借金額 × 年利率 ${"%.2f".format(p.ratePct)}% × $days 天 ÷ 365，四捨五入到整數",
            )
            ReadOnlyField("歸還合計（元）", "%,d".format(total), supporting = "= 質借金額 + 利息")
            Text(
                "歸還後，質借金額與利息會一起從帳務管理的可用餘額扣除（歸還日當天顯示為「當日質借歸還金額」與「當日質借歸還利息」）；" +
                    "這筆質押不再計入質借總金額與維持率，質押的股數也回到可質押股數。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(
                onClick = {
                    error = when {
                        returnDate < p.date -> "歸還日期不能早於質押日期 ${formatDate(p.date)}"
                        returnDate > today -> "歸還日期不能晚於今天"
                        else -> null
                    }
                    if (error == null) {
                        vm.savePledge(p.copy(returnDate = returnDate))
                        onDone()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (p.isActive) "確認歸還" else "儲存歸還日期") }
            if (!p.isActive) {
                OutlinedButton(onClick = { confirmCancel = true }, modifier = Modifier.fillMaxWidth()) { Text("取消歸還（恢復為質押中）") }
            }
        }

        if (confirmCancel) {
            AlertDialog(
                onDismissRequest = { confirmCancel = false },
                title = { Text("取消歸還？") },
                text = { Text("這筆質押會恢復為質押中，已扣掉的質借金額與利息會加回可用餘額。") },
                confirmButton = {
                    TextButton(onClick = {
                        confirmCancel = false
                        vm.savePledge(p.copy(returnDate = null))
                        onDone()
                    }) { Text("取消歸還", color = MaterialTheme.colorScheme.error) }
                },
                dismissButton = { TextButton(onClick = { confirmCancel = false }) { Text("返回") } },
            )
        }
    }
}
