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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.rboneone.stockrecord.data.Quote
import com.rboneone.stockrecord.domain.Accounting
import com.rboneone.stockrecord.ui.AppViewModel
import com.rboneone.stockrecord.ui.formatAmount
import com.rboneone.stockrecord.ui.formatDate
import com.rboneone.stockrecord.ui.formatSignedAmount
import java.time.LocalDate

private fun twd(v: Long) = formatAmount(Market.TW, v.toDouble())
private fun signedTwd(v: Long) = formatSignedAmount(Market.TW, v.toDouble())

/** 帳務管理：單一台幣帳戶（美股以當日匯率換算），存入／領出現金與當日帳務 */
@Composable
fun AccountingScreen(
    vm: AppViewModel,
    onBack: () -> Unit,
    onAdd: (CashFlowType) -> Unit,
    onEdit: (Long) -> Unit,
) {
    val cashFlows by vm.cashFlows.collectAsStateWithLifecycle()
    val trades by vm.trades.collectAsStateWithLifecycle()
    val pledges by vm.pledges.collectAsStateWithLifecycle()
    val quotes by vm.quotes.collectAsStateWithLifecycle()
    // 存入與領出紀錄預設收合
    var expanded by rememberSaveable { mutableStateOf(false) }
    val today = LocalDate.now().toEpochDay()
    val usdTwd = quotes[Market.US to Quote.USD_TWD]?.price
    val account = remember(cashFlows, trades, pledges, usdTwd, today) {
        Accounting.dayAccount(cashFlows, trades, pledges, usdTwd, today)
    }

    // 美股買賣要用匯率換算，進來時順便更新報價
    LaunchedEffect(Unit) { vm.refreshQuotes(force = false) }

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(
            "帳務管理",
            navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回") }
            },
        )
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = { onAdd(CashFlowType.DEPOSIT) }, modifier = Modifier.weight(1f)) {
                    Text(CashFlowType.DEPOSIT.label)
                }
                OutlinedButton(onClick = { onAdd(CashFlowType.WITHDRAW) }, modifier = Modifier.weight(1f)) {
                    Text(CashFlowType.WITHDRAW.label)
                }
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "${formatDate(today)}・新台幣",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    // A. 當日可用餘額
                    Text("當日可用餘額", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        twd(account.available),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "= 存入現金 ${twd(account.deposits)} − 領出現金 ${twd(account.withdrawals)}" +
                            " + 當日帳務 ${signedTwd(account.dayNet)} + 當日質押金額 ${twd(account.pledged)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    HorizontalDivider()
                    Item("當日賣出金額", twd(account.sells))
                    Item("當日買進金額", twd(account.buys))
                    Item("當日帳務（賣出 − 買進）", signedTwd(account.dayNet), bold = true)
                    Item("當日質押金額", twd(account.pledged))
                    when {
                        account.usMissingRate -> Text(
                            "尚未取得美元匯率，今天的美股買賣未計入，請到持股總覽更新股價。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                        account.usdTwd != null -> Text(
                            "美股買賣以當日匯率 %.4f 換算成台幣".format(account.usdTwd),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // 存入與領出紀錄：可展開／收合，預設收合
            Card(Modifier.fillMaxWidth()) {
                Column {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable(onClickLabel = if (expanded) "收合" else "展開") { expanded = !expanded }
                            .padding(horizontal = 12.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            if (expanded) Icons.Filled.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                        )
                        Text(
                            "存入與領出紀錄（${cashFlows.size}）",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(start = 4.dp).weight(1f),
                        )
                    }
                    if (expanded) {
                        HorizontalDivider()
                        if (cashFlows.isEmpty()) {
                            EmptyHint("還沒有存入或領出紀錄\n按上方「存入現金」或「領出現金」")
                        }
                        cashFlows.forEachIndexed { i, r ->
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
                                    signedTwd(Accounting.round(r.signedAmount)),
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
                "當日可用餘額 = 存入現金 − 領出現金 + 當日帳務 + 當日質押金額；存入、領出為截至今天的累計。\n" +
                    "當日賣出／買進金額為今天所有賣出／買進股票的成交金額（股數 × 成交價，未含手續費與證交稅），" +
                    "美股以當日匯率換算成台幣。所有金額四捨五入到整數。",
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
