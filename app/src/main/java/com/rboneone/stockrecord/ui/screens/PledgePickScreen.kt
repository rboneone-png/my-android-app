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
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rboneone.stockrecord.data.Market
import com.rboneone.stockrecord.domain.PledgeCandidate
import com.rboneone.stockrecord.ui.AppViewModel
import com.rboneone.stockrecord.ui.formatAmount
import com.rboneone.stockrecord.ui.formatPrice
import com.rboneone.stockrecord.ui.formatShares

private const val W_STOCK = 1.3f
private const val W_SHARES = 1.1f
private const val W_PRICE = 1f
private const val W_VALUE = 1.3f

/** 按「新增質押」後先選要質押的股票：列出可質押股數、股價與可質押市值 */
@Composable
fun PledgePickScreen(vm: AppViewModel, onPick: (String) -> Unit, onBack: () -> Unit) {
    val all by vm.pledgeCandidates.collectAsStateWithLifecycle()
    val includeLeveraged by vm.pledgeIncludeLeveraged.collectAsStateWithLifecycle()
    val list = remember(all, includeLeveraged) { all.filter { includeLeveraged || !it.leveraged } }
    val hidden = all.size - list.size

    LaunchedEffect(Unit) { vm.refreshQuotes(force = false) }

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(
            "選擇質押股票",
            navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回") }
            },
        )
        // 與質押頁相同的開關，這裡也可以直接切換
        Row(
            Modifier
                .fillMaxWidth()
                .toggleable(value = includeLeveraged, role = Role.Switch, onValueChange = vm::setPledgeIncludeLeveraged)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("包含正二", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Switch(checked = includeLeveraged, onCheckedChange = null)
        }
        HorizontalDivider()
        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp), modifier = Modifier.fillMaxSize()) {
            item(key = "header") { Header() }
            if (list.isEmpty()) {
                item(key = "empty") {
                    EmptyHint(
                        if (hidden > 0) "沒有可質押的股票\n已隱藏 $hidden 檔正二，開啟「包含正二」可顯示"
                        else "沒有可質押的股票\n需持有 1,000 股以上（已質押的股數會扣除）"
                    )
                }
            }
            items(list, key = { it.symbol }) { c ->
                Row(
                    Modifier.fillMaxWidth().clickable { onPick(c.symbol) }.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) { Item(c) }
                HorizontalDivider()
            }
            item(key = "note") {
                Text(
                    "可質押股數 = 持股 − 已質押股數，不足 1,000 股的零股不計（例如 23,211 股只算 23,000 股），" +
                        "扣除後不足 1,000 股的股票不列入。\n可質押市值 = 可質押股數 × 股價。點選股票進入質押設定。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
    }
}

@Composable
private fun Header() {
    val style = MaterialTheme.typography.bodySmall
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text("股票", Modifier.weight(W_STOCK), style = style, color = color)
        Text("可質押股數", Modifier.weight(W_SHARES), style = style, color = color, textAlign = TextAlign.End)
        Text("股價", Modifier.weight(W_PRICE), style = style, color = color, textAlign = TextAlign.End)
        Text("可質押市值", Modifier.weight(W_VALUE), style = style, color = color, textAlign = TextAlign.End)
    }
    HorizontalDivider()
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.Item(c: PledgeCandidate) {
    val style = MaterialTheme.typography.bodyMedium
    Column(Modifier.weight(W_STOCK)) {
        Text(c.name, style = MaterialTheme.typography.titleSmall, maxLines = 2)
        Text(
            if (c.leveraged) "${c.symbol}・正二" else c.symbol,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Text(formatShares(c.shares), Modifier.weight(W_SHARES), style = style, textAlign = TextAlign.End)
    Text(c.price?.let { formatPrice(it) } ?: "--", Modifier.weight(W_PRICE), style = style, textAlign = TextAlign.End)
    Text(
        c.marketValue?.let { formatAmount(Market.TW, it) } ?: "--",
        Modifier.weight(W_VALUE),
        style = style,
        textAlign = TextAlign.End,
    )
}
