package com.rboneone.stockrecord.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.rboneone.stockrecord.data.Market
import com.rboneone.stockrecord.data.Quote
import com.rboneone.stockrecord.data.WatchItem
import com.rboneone.stockrecord.ui.AppViewModel
import com.rboneone.stockrecord.ui.formatPct
import com.rboneone.stockrecord.ui.formatPrice
import com.rboneone.stockrecord.ui.theme.pnlColor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@Composable
fun WatchlistScreen(vm: AppViewModel) {
    val watchlist by vm.watchlist.collectAsStateWithLifecycle()
    val quotes by vm.quotes.collectAsStateWithLifecycle()
    val refreshing by vm.refreshing.collectAsStateWithLifecycle()
    val refreshSeconds by vm.refreshSeconds.collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val haptic = LocalHapticFeedback.current
    var adding by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf<WatchItem?>(null) }

    // 畫面顯示時依設定秒數自動更新報價
    LaunchedEffect(refreshSeconds) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            vm.refreshQuotes(force = false)
            while (true) {
                delay(refreshSeconds * 1000L)
                vm.refreshQuotes(quiet = true)
            }
        }
    }

    // 拖曳中先改畫面上的順序，放開後才寫回資料庫
    var order by remember(watchlist) { mutableStateOf(watchlist) }
    val listState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        val fromIndex = order.indexOfFirst { it.id == from.key }
        val toIndex = order.indexOfFirst { it.id == to.key }
        // 拖到提示文字等非股票列時忽略
        if (fromIndex < 0 || toIndex < 0) return@rememberReorderableLazyListState
        order = order.toMutableList().apply { add(toIndex, removeAt(fromIndex)) }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            ScreenTopBar(
                "觀察股票",
                actions = {
                    IconButton(onClick = { vm.refreshQuotes() }, enabled = !refreshing) {
                        Icon(Icons.Filled.Refresh, contentDescription = "更新股價")
                    }
                },
            )
            if (order.isEmpty()) {
                EmptyHint("還沒有觀察的股票\n按右下角「新增觀察」")
                return@Column
            }
            WatchHeader()
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(bottom = 96.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(order, key = { it.id }) { item ->
                    ReorderableItem(reorderState, key = item.id) { dragging ->
                        Surface(
                            shadowElevation = if (dragging) 8.dp else 0.dp,
                            color = if (dragging) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface,
                        ) {
                            Column {
                                WatchRow(
                                    item,
                                    quotes[item.market to item.symbol],
                                    Modifier
                                        .longPressDraggableHandle(
                                            onDragStarted = { haptic.performHapticFeedback(HapticFeedbackType.LongPress) },
                                            onDragStopped = { vm.reorderWatch(order.map { it.id }) },
                                        )
                                        .clickable { removing = item },
                                )
                                HorizontalDivider()
                            }
                        }
                    }
                }
                item(key = "hint") {
                    Text(
                        "長按可拖曳調整順序，點一下可刪除。每 $refreshSeconds 秒自動更新報價。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                    )
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = { adding = true },
            icon = { Icon(Icons.Filled.Add, contentDescription = null) },
            text = { Text("新增觀察") },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        )
    }

    if (adding) AddWatchDialog(vm, onDismiss = { adding = false })

    removing?.let { item ->
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text("${item.name} ${item.symbol}") },
            text = { Text("要從觀察清單中移除嗎？") },
            confirmButton = {
                TextButton(onClick = { vm.removeWatch(item); removing = null }) {
                    Text("移除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { removing = null }) { Text("取消") } },
        )
    }
}

@Composable
private fun WatchHeader() {
    val style = MaterialTheme.typography.bodySmall
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text("股票", Modifier.weight(1.4f), style = style, color = color)
        Text("股價", Modifier.weight(1f), style = style, color = color, textAlign = TextAlign.End)
        Text("漲跌", Modifier.weight(1f), style = style, color = color, textAlign = TextAlign.End)
        Text("漲跌幅", Modifier.weight(1f), style = style, color = color, textAlign = TextAlign.End)
    }
    HorizontalDivider()
}

@Composable
private fun WatchRow(item: WatchItem, quote: Quote?, modifier: Modifier) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1.4f)) {
            Text(item.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${item.symbol}・${item.market.label}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (quote == null) {
            Text("--", Modifier.weight(3f), textAlign = TextAlign.End)
            return@Row
        }
        val change = quote.price - quote.previousClose
        val pct = if (quote.previousClose > 0) change / quote.previousClose * 100 else 0.0
        val color = pnlColor(change)
        val arrow = when {
            change > 0.0001 -> "▲"
            change < -0.0001 -> "▼"
            else -> ""
        }
        Text(
            formatPrice(quote.price),
            Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = color,
            textAlign = TextAlign.End,
        )
        Text(
            "$arrow${formatPrice(kotlin.math.abs(change))}",
            Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = color,
            textAlign = TextAlign.End,
        )
        Text(
            formatPct(pct),
            Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = color,
            textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun AddWatchDialog(vm: AppViewModel, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var market by remember { mutableStateOf(Market.TW) }
    var symbol by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var nameManual by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val nameHint = rememberStockNameLookup(vm, market, symbol, enabled = !nameManual) { name = it }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新增觀察股票") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Market.entries.forEach { m ->
                        FilterChip(
                            selected = market == m,
                            onClick = { market = m; nameManual = false },
                            label = { Text(m.label) },
                        )
                    }
                }
                OutlinedTextField(
                    value = symbol,
                    onValueChange = { symbol = it; nameManual = false; error = null },
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
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val sym = symbol.trim().uppercase()
                if (sym.isEmpty()) {
                    error = "請輸入股票代號"
                    return@TextButton
                }
                scope.launch {
                    if (vm.addWatch(market, sym, name.trim().ifEmpty { sym })) onDismiss()
                    else error = "這檔股票已經在觀察清單中"
                }
            }) { Text("新增") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
