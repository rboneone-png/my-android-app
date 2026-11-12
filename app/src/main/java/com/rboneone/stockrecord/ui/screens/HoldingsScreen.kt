package com.rboneone.stockrecord.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Refresh
import com.rboneone.stockrecord.ui.theme.AppCard as Card
import androidx.compose.material3.Surface
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import com.rboneone.stockrecord.R
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.rboneone.stockrecord.data.Market
import com.rboneone.stockrecord.domain.MarketSummary
import com.rboneone.stockrecord.domain.PortfolioView
import com.rboneone.stockrecord.domain.Position
import com.rboneone.stockrecord.domain.inCustomOrder
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import com.rboneone.stockrecord.ui.AppViewModel
import com.rboneone.stockrecord.ui.formatAmount
import com.rboneone.stockrecord.ui.formatClock
import com.rboneone.stockrecord.ui.formatMoney
import com.rboneone.stockrecord.ui.formatPct
import com.rboneone.stockrecord.ui.formatPrice
import com.rboneone.stockrecord.ui.formatShares
import com.rboneone.stockrecord.ui.formatSignedWhole
import com.rboneone.stockrecord.ui.roundHalfAway
import com.rboneone.stockrecord.ui.theme.HeroCard
import com.rboneone.stockrecord.ui.theme.LossGreen
import com.rboneone.stockrecord.ui.theme.ProfitRed
import com.rboneone.stockrecord.ui.formatSignedAmount
import com.rboneone.stockrecord.ui.theme.pnlColor
import kotlinx.coroutines.delay

private const val HIDDEN = "***"
private const val OPEN_KEY = "open-"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HoldingsScreen(
    vm: AppViewModel,
    onCategorize: () -> Unit,
    onOpenCharts: () -> Unit,
    onOpenRecords: () -> Unit,
) {
    val swipe by vm.swipeNav.collectAsStateWithLifecycle()
    val portfolio by vm.portfolio.collectAsStateWithLifecycle()
    val refreshing by vm.refreshing.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val refreshSeconds by vm.refreshSeconds.collectAsStateWithLifecycle()
    val uncategorized by vm.uncategorizedCount.collectAsStateWithLifecycle()
    val holdingOrder by vm.holdingOrder.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var market by rememberSaveable { mutableStateOf(Market.TW) }
    var hidden by rememberSaveable { mutableStateOf(false) }

    // 畫面顯示時，每隔設定的秒數自動更新股價
    LaunchedEffect(refreshSeconds) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            vm.refreshQuotes(force = false)
            while (true) {
                delay(refreshSeconds * 1000L)
                vm.refreshQuotes(quiet = true)
            }
        }
    }
    LaunchedEffect(message) {
        message?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            vm.clearMessage()
        }
    }

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(
            "持股總覽",
            actions = {
                TextButton(onClick = onOpenRecords) { Text("紀錄") }
                TextButton(onClick = { hidden = !hidden }) { Text(if (hidden) "顯示" else "隱藏") }
                IconButton(onClick = onOpenCharts) {
                    Icon(ImageVector.vectorResource(R.drawable.ic_bar_chart), contentDescription = "圖表")
                }
                IconButton(onClick = { vm.refreshQuotes() }, enabled = !refreshing) {
                    Icon(Icons.Filled.Refresh, contentDescription = "更新股價")
                }
            },
        )
        TabRow(selectedTabIndex = market.ordinal) {
            Market.entries.forEach { m ->
                Tab(selected = market == m, onClick = { market = m }, text = { Text(m.label) })
            }
        }
        // 左滑／右滑切換台股、美股（設定可關閉）
        Column(
            Modifier.fillMaxSize().swipeToSwitch(swipe) { d ->
                market = Market.entries[(market.ordinal + d).coerceIn(0, Market.entries.size - 1)]
            },
        ) {
        if (uncategorized > 0) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).clickable(onClick = onCategorize),
            ) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "有 $uncategorized 檔持股尚未設定分類（原型／正二／類現金）",
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text("設定 ›", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = { vm.refreshQuotes() },
            modifier = Modifier.fillMaxSize(),
        ) {
            val view = portfolio
            val summary = view?.summary(market)
            if (view == null || summary == null) {
                LazyColumn(Modifier.fillMaxSize()) {
                    if (view != null) item { EmptyHint("${market.label}還沒有任何紀錄\n按右下角的 + 新增") }
                }
            } else {
                MarketList(
                    view, summary, hidden, refreshSeconds,
                    savedOrder = holdingOrder[market].orEmpty(),
                    onOrderChanged = { vm.setHoldingOrder(market, it) },
                )
            }
        }
        }
    }
}

@Composable
private fun MarketList(
    view: PortfolioView,
    summary: MarketSummary,
    hidden: Boolean,
    refreshSeconds: Int,
    savedOrder: List<String>,
    onOrderChanged: (List<String>) -> Unit,
) {
    val market = summary.market
    val positions = view.positions.filter { it.holding.market == market }
    // 拖曳中先用畫面上的暫存順序，放開後才存檔
    var order by remember(market, savedOrder) { mutableStateOf(savedOrder) }
    val open = positions.filter { it.holding.isOpen }.inCustomOrder(order)
    val closed = positions.filter { !it.holding.isOpen }
    // 庫存股清單可展開／收合（預設展開）；台股、美股各自記住
    var inventoryOpen by rememberSaveable(market) { mutableStateOf(true) }
    val listState = rememberLazyListState()
    val haptic = LocalHapticFeedback.current
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        val fromSymbol = (from.key as? String)?.removePrefix(OPEN_KEY) ?: return@rememberReorderableLazyListState
        val toSymbol = (to.key as? String)?.takeIf { it.startsWith(OPEN_KEY) }?.removePrefix(OPEN_KEY)
            ?: return@rememberReorderableLazyListState
        val symbols = open.map { it.holding.symbol }.toMutableList()
        val toIndex = symbols.indexOf(toSymbol)
        symbols.remove(fromSymbol)
        symbols.add(toIndex, fromSymbol)
        order = symbols
    }
    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(bottom = 88.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(key = "summary") { SummaryHeader(view, summary, hidden) }
        item(key = "inventory") {
            SectionHeader("庫存股(${open.size})", expanded = inventoryOpen, onToggle = { inventoryOpen = !inventoryOpen }) {
                Text("合計: ", style = MaterialTheme.typography.bodyMedium)
                Text(
                    if (hidden) HIDDEN else formatSignedAmount(market, summary.totalPnl),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (hidden) Color.Unspecified else pnlColor(summary.totalPnl),
                )
            }
        }
        if (inventoryOpen) item(key = "table-header") { TableHeader() }
        if (inventoryOpen) items(open, key = { OPEN_KEY + it.holding.symbol }) { p ->
            ReorderableItem(reorderState, key = OPEN_KEY + p.holding.symbol) { dragging ->
                Surface(
                    shadowElevation = if (dragging) 8.dp else 0.dp,
                    color = if (dragging) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surfaceContainerLowest,
                ) {
                    Column {
                        PositionRow(
                            p,
                            hidden,
                            Modifier.longPressDraggableHandle(
                                onDragStarted = { haptic.performHapticFeedback(HapticFeedbackType.LongPress) },
                                // 讀取狀態中的最新順序，避免拿到拖曳前的舊清單
                                onDragStopped = { onOrderChanged(order) },
                            ),
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
        if (closed.isNotEmpty()) {
            item(key = "closed") { SectionHeader("已出清(${closed.size})") {} }
            items(closed, key = { "closed-${it.holding.symbol}" }) {
                ClosedRow(it, hidden)
                HorizontalDivider()
            }
        }
        item(key = "note") {
            val updated = open.mapNotNull { it.quote?.updatedAt }.maxOrNull()
            Text(
                buildString {
                    if (updated != null) append("報價更新 ${formatClock(updated)}　每 $refreshSeconds 秒自動刷新\n")
                    append("總損益 = 市值 − 預估賣出費用 − 成本 + 持有期間股息\n")
                    append("成本均已扣除持有期間股息，買均為實際買進成本。報價來源 Yahoo Finance。\n")
                    append("長按庫存股可拖曳調整順序。")
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            )
        }
    }
}

@Composable
private fun SummaryHeader(view: PortfolioView, s: MarketSummary, hidden: Boolean) {
    val m = s.market
    fun money(v: Double) = if (hidden) HIDDEN else formatAmount(m, v)
    fun signed(v: Double) = if (hidden) HIDDEN else formatSignedAmount(m, v)
    fun color(v: Double) = if (hidden) Color.Unspecified else pnlColor(v)

    HeroCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
      Column(Modifier.padding(16.dp)) {
        Text("累積損益", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                signed(s.cumulativePnl),
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                color = color(s.cumulativePnl),
            )
            s.cumulativePct?.takeIf { !hidden }?.let {
                Text(
                    "  ${formatPct(it)}",
                    style = MaterialTheme.typography.titleMedium,
                    color = pnlColor(it),
                    modifier = Modifier.padding(bottom = 6.dp),
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Row {
            SummaryCell("持股市值", money(s.marketValue), Modifier.weight(1f))
            SummaryCell("持股成本", money(s.cost), Modifier.weight(1f))
            SummaryCell("股息收入", money(s.dividendIncome), Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))
        Row {
            SummaryCell("持股日損益", signed(s.dayPnl), Modifier.weight(1f), color(s.dayPnl))
            SummaryCell("未實現損益", signed(s.unrealizedPnl), Modifier.weight(1f), color(s.unrealizedPnl))
            SummaryCell("已實現損益", signed(s.realizedPnl), Modifier.weight(1f), color(s.realizedPnl))
        }
        if (m == Market.US) {
            val fx = view.usdTwd
            Text(
                if (fx == null) "尚未取得美元匯率"
                else "約當台幣 ${if (hidden) HIDDEN else formatMoney(Market.TW, s.marketValue * fx.price)}" +
                    "　匯率 ${"%.3f".format(fx.price)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
        if (s.missingQuotes > 0) {
            Text(
                "有 ${s.missingQuotes} 檔尚無報價，市值以成本計算",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
      }
    }
}

@Composable
private fun SummaryCell(label: String, value: String, modifier: Modifier, color: Color = Color.Unspecified) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium, color = color)
    }
}

@Composable
private fun SectionHeader(
    title: String,
    expanded: Boolean? = null,
    onToggle: (() -> Unit)? = null,
    trailing: @Composable () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .then(if (onToggle != null) Modifier.clickable(onClickLabel = if (expanded == true) "收合" else "展開", onClick = onToggle) else Modifier)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(4.dp).height(18.dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp)))
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(start = 8.dp).weight(1f),
        )
        trailing()
        // 可展開／收合時，右側顯示箭頭（展開＝向下、收合＝向右）
        if (onToggle != null) {
            Icon(
                if (expanded == true) Icons.Filled.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
    }
}

private val W_STOCK = 1.25f
private val W_PRICE = 1.2f
private val W_COST = 1f
private val W_PNL = 1.25f

@Composable
private fun TableHeader() {
    val style = MaterialTheme.typography.bodyMedium
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerLowest).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text("股票", Modifier.weight(W_STOCK), style = style, color = color)
        Text("股價", Modifier.weight(W_PRICE), style = style, color = color, textAlign = TextAlign.End)
        Text("成本均/買均", Modifier.weight(W_COST), style = style, color = color, textAlign = TextAlign.End)
        Column(Modifier.weight(W_PNL), horizontalAlignment = Alignment.End) {
            Text("總損益", style = style, color = color)
            Text("(預扣賣出費用)", style = MaterialTheme.typography.labelSmall, color = color)
        }
    }
    HorizontalDivider()
}

@Composable
private fun PositionRow(p: Position, hidden: Boolean, dragModifier: Modifier = Modifier) {
    val h = p.holding
    var expanded by rememberSaveable(h.market, h.symbol) { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxWidth()
            .then(dragModifier)
            .clickable { expanded = !expanded }
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(W_STOCK)) {
                Text(p.displayName, style = MaterialTheme.typography.titleMedium, maxLines = 2)
                Text(h.symbol, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(Modifier.weight(W_PRICE), horizontalAlignment = Alignment.End) {
                val q = p.quote
                if (q == null) {
                    Text("--", style = MaterialTheme.typography.titleMedium)
                } else {
                    val change = p.dayChange ?: 0.0
                    val arrow = when {
                        change > 0.0001 -> "▲"
                        change < -0.0001 -> "▼"
                        else -> ""
                    }
                    Text(
                        formatPrice(q.price),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                        color = pnlColor(change),
                    )
                    Text(
                        "$arrow${formatPrice(kotlin.math.abs(change))}(${formatPct(p.dayChangePct ?: 0.0)})",
                        style = MaterialTheme.typography.bodySmall,
                        color = pnlColor(change),
                        maxLines = 1,
                    )
                }
            }
            Column(Modifier.weight(W_COST), horizontalAlignment = Alignment.End) {
                Text(formatPrice(h.adjustedAvgCost), style = MaterialTheme.typography.titleMedium)
                Text(formatPrice(h.avgCost), style = MaterialTheme.typography.bodyMedium)
            }
            Column(Modifier.weight(W_PNL), horizontalAlignment = Alignment.End) {
                val total = p.totalPnl
                if (total == null) {
                    Text("--", style = MaterialTheme.typography.titleMedium)
                } else {
                    Text(
                        if (hidden) HIDDEN else formatSignedAmount(h.market, total),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                        color = if (hidden) Color.Unspecified else pnlColor(total),
                    )
                    p.totalPnlPct?.let {
                        Text(formatPct(it), style = MaterialTheme.typography.bodyMedium, color = pnlColor(it))
                    }
                }
            }
        }
        if (expanded) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .padding(12.dp),
            ) {
                fun money(v: Double) = if (hidden) HIDDEN else formatAmount(h.market, v)
                fun signed(v: Double) = if (hidden) HIDDEN else formatSignedAmount(h.market, v)
                LabelValue("持有股數", formatShares(h.shares))
                LabelValue("市值", p.marketValue?.let { money(it) } ?: "--")
                LabelValue("總成本", money(h.totalCost))
                p.unrealizedPnl?.let { LabelValue("未實現損益", signed(it), pnl = it.takeIf { !hidden }) }
                LabelValue("持有期間股息", money(h.dividendsSinceOpen))
                // 今日損益 = 持有股數 × 今日漲跌，四捨五入到整數；上漲紅、下跌綠、平盤灰
                val dayPnl = p.dayPnl?.let { roundHalfAway(it) }
                LabelValue(
                    "今日損益",
                    when {
                        dayPnl == null -> "--"
                        hidden -> HIDDEN
                        else -> formatSignedWhole(dayPnl)
                    },
                    valueColor = when {
                        dayPnl == null || hidden -> null
                        dayPnl > 0 -> ProfitRed
                        dayPnl < 0 -> LossGreen
                        else -> Color.Gray
                    },
                )
                if (h.realizedPnl != 0.0) {
                    LabelValue("已實現損益", signed(h.realizedPnl), pnl = h.realizedPnl.takeIf { !hidden })
                }
            }
        }
    }
}

@Composable
private fun ClosedRow(p: Position, hidden: Boolean) {
    val h = p.holding
    Row(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerLowest).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(p.displayName, style = MaterialTheme.typography.titleMedium)
            Text(h.symbol, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Column(horizontalAlignment = Alignment.End) {
            val pnl = h.realizedPnl + h.dividendIncome
            Text("已實現（含股息）", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                if (hidden) HIDDEN else formatSignedAmount(h.market, pnl),
                style = MaterialTheme.typography.titleMedium,
                color = if (hidden) Color.Unspecified else pnlColor(pnl),
            )
        }
    }
}

@Composable
fun LabelValue(label: String, value: String, pnl: Double? = null, valueColor: Color? = null) {
    Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = valueColor ?: pnl?.let { pnlColor(it) } ?: MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
fun EmptyHint(text: String) {
    Text(
        text,
        modifier = Modifier.fillMaxWidth().padding(32.dp),
        textAlign = TextAlign.Center,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
