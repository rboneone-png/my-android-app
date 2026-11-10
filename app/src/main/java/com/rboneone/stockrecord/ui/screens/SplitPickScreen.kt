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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rboneone.stockrecord.data.Market
import com.rboneone.stockrecord.ui.AppViewModel
import com.rboneone.stockrecord.ui.formatDate
import com.rboneone.stockrecord.ui.formatShares
import com.rboneone.stockrecord.ui.theme.AppCard as Card

/** 股票分割：先列出庫存股清單，選要分割的股票；下方是已設定的分割（待執行／已執行） */
@Composable
fun SplitPickScreen(
    vm: AppViewModel,
    onPick: (Market, String) -> Unit,
    onOpenSplit: (Long) -> Unit,
    onBack: () -> Unit,
) {
    val holdings by vm.holdings.collectAsStateWithLifecycle()
    val splits by vm.splits.collectAsStateWithLifecycle()
    val open = holdings.filter { it.isOpen }.sortedWith(compareBy({ it.market }, { it.symbol }))

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(
            "股票分割",
            navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回") }
            },
        )
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "h1") {
                Text("選擇要分割的庫存股", style = MaterialTheme.typography.titleMedium)
            }
            if (open.isEmpty()) {
                item(key = "empty") { Text("目前沒有庫存股", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            items(open, key = { "h-${it.market}-${it.symbol}" }) { h ->
                Card(Modifier.fillMaxWidth().clickable { onPick(h.market, h.symbol) }) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(h.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "${h.symbol}・${h.market.label}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text("${formatShares(h.shares)} 股", style = MaterialTheme.typography.titleSmall)
                    }
                }
            }
            if (splits.isNotEmpty()) {
                item(key = "h2") {
                    Text("已設定的股票分割（${splits.size}）", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp))
                }
                items(splits, key = { "s-${it.id}" }) { s ->
                    Card(Modifier.fillMaxWidth().clickable { onOpenSplit(s.id) }) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("${s.name}（${s.symbol}）", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "1 分 ${s.ratio}・暫停 ${formatDate(s.suspendDate)} → 恢復 ${formatDate(s.resumeDate)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(
                                if (s.applied) "已執行" else "待執行",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = if (s.applied) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
        }
    }
}
