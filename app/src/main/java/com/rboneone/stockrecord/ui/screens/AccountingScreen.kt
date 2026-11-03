package com.rboneone.stockrecord.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rboneone.stockrecord.data.CashFlowType
import com.rboneone.stockrecord.data.Market
import com.rboneone.stockrecord.domain.Accounting
import com.rboneone.stockrecord.ui.AppViewModel
import com.rboneone.stockrecord.ui.formatAmount
import com.rboneone.stockrecord.ui.formatDate
import com.rboneone.stockrecord.ui.formatSignedAmount
import java.time.LocalDate

/** 帳務管理：存入／領出現金，以及當日的賣出、買進、帳務與可用餘額 */
@Composable
fun AccountingScreen(
    vm: AppViewModel,
    onBack: () -> Unit,
    onAdd: (CashFlowType, Market) -> Unit,
    onEdit: (Long) -> Unit,
) {
    val cashFlows by vm.cashFlows.collectAsStateWithLifecycle()
    val trades by vm.trades.collectAsStateWithLifecycle()
    var market by rememberSaveable { mutableStateOf(Market.TW) }
    val today = LocalDate.now().toEpochDay()
    val account = remember(cashFlows, trades, market, today) { Accounting.dayAccount(cashFlows, trades, market, today) }
    val records = remember(cashFlows, market) { cashFlows.filter { it.market == market } }

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(
            "帳務管理",
            navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回") }
            },
        )
        TabRow(selectedTabIndex = market.ordinal) {
            Market.entries.forEach { m ->
                Tab(selected = market == m, onClick = { market = m }, text = { Text(m.label) })
            }
        }
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = { onAdd(CashFlowType.DEPOSIT, market) }, modifier = Modifier.weight(1f)) {
                    Text(CashFlowType.DEPOSIT.label)
                }
                OutlinedButton(onClick = { onAdd(CashFlowType.WITHDRAW, market) }, modifier = Modifier.weight(1f)) {
                    Text(CashFlowType.WITHDRAW.label)
                }
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(formatDate(today), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    // A. 當日可用餘額
                    Text("當日可用餘額", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        formatAmount(market, account.available),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "= 存入現金 ${formatAmount(market, account.deposits)} − 領出現金 ${formatAmount(market, account.withdrawals)} + 當日帳務",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    HorizontalDivider()
                    // B、C、D
                    Item("當日賣出金額", formatAmount(market, account.sells))
                    Item("當日買進金額", formatAmount(market, account.buys))
                    Item("當日帳務（賣出 − 買進）", formatSignedAmount(market, account.dayNet), bold = true)
                }
            }

            Text("存入與領出紀錄", style = MaterialTheme.typography.titleMedium)
            if (records.isEmpty()) {
                EmptyHint("還沒有${market.label}的存入或領出紀錄\n按上方「存入現金」或「領出現金」")
            } else {
                Card(Modifier.fillMaxWidth()) {
                    Column {
                        records.forEachIndexed { i, r ->
                            if (i > 0) HorizontalDivider()
                            Row(
                                Modifier.fillMaxWidth().clickable { onEdit(r.id) }.padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(r.type.label, style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        formatDate(r.date) + if (r.note.isNotBlank()) "・${r.note}" else "",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Text(
                                    formatSignedAmount(market, r.signedAmount),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Medium,
                                    textAlign = TextAlign.End,
                                )
                            }
                        }
                    }
                }
            }
            Text(
                "當日可用餘額 = 存入現金 − 領出現金 + 當日帳務；存入、領出為截至今天的累計。\n" +
                    "當日賣出／買進金額為今天所有賣出／買進股票的成交金額（股數 × 成交價，未含手續費與證交稅）。\n" +
                    "台股以台幣、美股以美元計算。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 16.dp),
            )
        }
    }
}

@Composable
private fun Item(label: String, value: String, bold: Boolean = false) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium,
        )
    }
}
