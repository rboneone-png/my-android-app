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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rboneone.stockrecord.data.HighWatchItem
import com.rboneone.stockrecord.data.Market
import com.rboneone.stockrecord.data.Quote
import com.rboneone.stockrecord.domain.HighWatch
import com.rboneone.stockrecord.ui.AppViewModel
import com.rboneone.stockrecord.ui.formatPrice
import com.rboneone.stockrecord.ui.theme.ProfitRed
import com.rboneone.stockrecord.ui.theme.pnlColor
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val HIGH_DATE = DateTimeFormatter.ofPattern("yyyy/MM/dd")

/** 「股票漲跌」：現價與近 2 年最高價的比較，漲跌幅 = 現價 ÷ 最高價 × 100% */
@Composable
fun HighWatchTab(vm: AppViewModel, refreshSeconds: Int) {
    val list by vm.highWatch.collectAsStateWithLifecycle()
    val quotes by vm.quotes.collectAsStateWithLifecycle()
    val haptic = LocalHapticFeedback.current
    var adding by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf<HighWatchItem?>(null) }

    // 最高價一天抓一次
    LaunchedEffect(Unit) { vm.refreshHighs(force = false) }

    var order by remember(list) { mutableStateOf(list) }
    val listState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        val fromIndex = order.indexOfFirst { it.id == from.key }
        val toIndex = order.indexOfFirst { it.id == to.key }
        if (fromIndex < 0 || toIndex < 0) return@rememberReorderableLazyListState
        order = order.toMutableList().apply { add(toIndex, removeAt(fromIndex)) }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            if (order.isEmpty()) {
                EmptyHint("還沒有設定股票\n按右下角「新增設定」\n比較現價與近 2 年最高價")
                return@Column
            }
            HighHeader()
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
                                HighRow(
                                    item,
                                    quotes[item.market to item.symbol],
                                    Modifier
                                        .longPressDraggableHandle(
                                            onDragStarted = { haptic.performHapticFeedback(HapticFeedbackType.LongPress) },
                                            onDragStopped = { vm.reorderHighWatch(order.map { it.id }) },
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
                        "漲跌幅 = 現價 ÷ 最高價 × 100%\n" +
                            "最高價為近 2 年的最高股價，每天更新一次；現價每 $refreshSeconds 秒更新。\n" +
                            "長按可拖曳調整順序，點一下可刪除。",
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
            text = { Text("新增設定") },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        )
    }

    if (adding) AddHighDialog(vm, onDismiss = { adding = false })
    removing?.let { item ->
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text("${item.name} ${item.symbol}") },
            text = { Text("要從股票漲跌清單中移除嗎？") },
            confirmButton = {
                TextButton(onClick = { vm.removeHighWatch(item); removing = null }) {
                    Text("移除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { removing = null }) { Text("取消") } },
        )
    }
}

private const val W_STOCK = 1.3f
private const val W_NUM = 1f

@Composable
private fun HighHeader() {
    val style = MaterialTheme.typography.bodySmall
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text("股票", Modifier.weight(W_STOCK), style = style, color = color)
        Text("2年最高價", Modifier.weight(W_NUM), style = style, color = color, textAlign = TextAlign.End)
        Text("現價", Modifier.weight(W_NUM), style = style, color = color, textAlign = TextAlign.End)
        Text("漲跌幅", Modifier.weight(W_NUM), style = style, color = color, textAlign = TextAlign.End)
    }
    HorizontalDivider()
}

@Composable
private fun HighRow(item: HighWatchItem, quote: Quote?, modifier: Modifier) {
    val price = quote?.price
    val high = HighWatch.effectiveHigh(item.highPrice, price)
    val ratio = HighWatch.ratio(price, high)
    val newHigh = price != null && item.highPrice != null && price >= item.highPrice
    val sub = MaterialTheme.typography.bodySmall
    val subColor = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(W_STOCK)) {
            Text(item.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${item.symbol}・${item.market.label}", style = sub, color = subColor)
        }
        Column(Modifier.weight(W_NUM), horizontalAlignment = Alignment.End) {
            Text(high?.let { formatPrice(it) } ?: "--", style = MaterialTheme.typography.bodyLarge)
            val date = when {
                newHigh -> "今天"
                item.highDate != null -> LocalDate.ofEpochDay(item.highDate).format(HIGH_DATE)
                else -> "抓取中"
            }
            Text(date, style = sub, color = subColor)
        }
        Column(Modifier.weight(W_NUM), horizontalAlignment = Alignment.End) {
            val change = quote?.let { it.price - it.previousClose } ?: 0.0
            Text(
                price?.let { formatPrice(it) } ?: "--",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = pnlColor(change),
            )
        }
        Column(Modifier.weight(W_NUM), horizontalAlignment = Alignment.End) {
            Text(
                ratio?.let { "%.2f%%".format(it) } ?: "--",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (newHigh) ProfitRed else Color.Unspecified,
            )
            if (newHigh) Text("創新高", style = sub, color = ProfitRed)
        }
    }
}

@Composable
private fun AddHighDialog(vm: AppViewModel, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var market by remember { mutableStateOf(Market.TW) }
    var symbol by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var nameManual by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val nameHint = rememberStockNameLookup(vm, market, symbol, enabled = !nameManual) { name = it }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新增股票漲跌設定") },
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
                    placeholder = { Text(if (market == Market.TW) "例如 0050" else "例如 QQQ") },
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
                Text(
                    "最高價自動抓取近 2 年的最高股價",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                    if (vm.addHighWatch(market, sym, name.trim().ifEmpty { sym })) onDismiss()
                    else error = "這檔股票已經在清單中"
                }
            }) { Text("新增") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
