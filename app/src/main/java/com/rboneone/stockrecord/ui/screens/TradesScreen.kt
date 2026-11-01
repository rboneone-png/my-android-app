package com.rboneone.stockrecord.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.remember
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rboneone.stockrecord.data.Dividend
import com.rboneone.stockrecord.data.Market
import com.rboneone.stockrecord.data.Trade
import com.rboneone.stockrecord.data.TradeType
import com.rboneone.stockrecord.ui.AppViewModel
import com.rboneone.stockrecord.ui.formatDate
import com.rboneone.stockrecord.ui.formatMoney
import com.rboneone.stockrecord.ui.formatPrice
import com.rboneone.stockrecord.ui.formatShares
import com.rboneone.stockrecord.ui.theme.LossGreen
import com.rboneone.stockrecord.ui.theme.ProfitRed

@Composable
fun TradesScreen(
    vm: AppViewModel,
    showDividends: Boolean,
    onShowDividendsChange: (Boolean) -> Unit,
    onTradeClick: (Trade) -> Unit,
    onDividendClick: (Dividend) -> Unit,
    onBack: () -> Unit,
    onAddTrade: () -> Unit,
    onAddDividend: () -> Unit,
) {
    var addMenu by remember { mutableStateOf(false) }
    val trades by vm.trades.collectAsStateWithLifecycle()
    val dividends by vm.dividends.collectAsStateWithLifecycle()
    var filter by rememberSaveable { mutableStateOf<Market?>(null) }

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(
            "紀錄",
            navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回") }
            },
            actions = {
                Box {
                    TextButton(onClick = { addMenu = true }) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Text("新增")
                    }
                    DropdownMenu(expanded = addMenu, onDismissRequest = { addMenu = false }) {
                        DropdownMenuItem(text = { Text("新增買賣") }, onClick = { addMenu = false; onAddTrade() })
                        DropdownMenuItem(text = { Text("新增股利") }, onClick = { addMenu = false; onAddDividend() })
                    }
                }
            },
        )
        TabRow(selectedTabIndex = if (showDividends) 1 else 0) {
            Tab(selected = !showDividends, onClick = { onShowDividendsChange(false) }, text = { Text("買賣紀錄") })
            Tab(selected = showDividends, onClick = { onShowDividendsChange(true) }, text = { Text("股利紀錄") })
        }
        Row(
            Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(selected = filter == null, onClick = { filter = null }, label = { Text("全部") })
            Market.entries.forEach { m ->
                FilterChip(selected = filter == m, onClick = { filter = m }, label = { Text(m.label) })
            }
        }
        if (showDividends) {
            val shown = dividends.filter { filter == null || it.market == filter }
            if (shown.isEmpty()) {
                EmptyHint("沒有股利紀錄\n按右下角的 + 新增")
                return@Column
            }
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 88.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(shown, key = { "d${it.id}" }) { DividendRow(it, onClick = { onDividendClick(it) }) }
            }
        } else {
            val shown = trades.filter { filter == null || it.market == filter }
            if (shown.isEmpty()) {
                EmptyHint("沒有交易紀錄")
                return@Column
            }
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 88.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(shown, key = { it.id }) { TradeRow(it, onClick = { onTradeClick(it) }) }
            }
        }
    }
}

@Composable
private fun DividendRow(d: Dividend, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("股利", color = ProfitRed, fontWeight = FontWeight.Bold)
                Text(
                    "  ${d.symbol} ${d.name.takeIf { it != d.symbol } ?: ""}",
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(d.market.label, style = MaterialTheme.typography.labelMedium)
            }
            Row(Modifier.padding(top = 4.dp)) {
                Text(formatDate(d.date), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                if (d.cashAmount > 0) {
                    Text("入帳 ${formatMoney(d.market, d.netCash)}", style = MaterialTheme.typography.bodyMedium)
                }
            }
            val extra = buildList {
                if (d.cashAmount > 0) add("現金股利 ${formatMoney(d.market, d.cashAmount)}")
                if (d.tax > 0) add("扣稅 ${formatMoney(d.market, d.tax)}")
                if (d.fee > 0) add("匯費 ${formatMoney(d.market, d.fee)}")
                if (d.stockShares > 0) add("配股 ${formatShares(d.stockShares)} 股")
            }.joinToString("　")
            Text(extra, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (d.note.isNotBlank()) {
                Text(d.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun TradeRow(t: Trade, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    t.type.label,
                    color = if (t.type == TradeType.BUY) ProfitRed else LossGreen,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "  ${t.symbol} ${t.name.takeIf { it != t.symbol } ?: ""}",
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(t.market.label, style = MaterialTheme.typography.labelMedium)
            }
            Row(Modifier.padding(top = 4.dp)) {
                Text(
                    "${formatDate(t.date)}　${formatShares(t.shares)} 股 × ${formatPrice(t.price)}",
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(formatMoney(t.market, t.amount), style = MaterialTheme.typography.bodyMedium)
            }
            val extra = buildString {
                append("手續費 ${formatMoney(t.market, t.fee)}")
                if (t.tax > 0) append("　交易稅 ${formatMoney(t.market, t.tax)}")
            }
            Text(extra, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (t.note.isNotBlank()) {
                Text(t.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
