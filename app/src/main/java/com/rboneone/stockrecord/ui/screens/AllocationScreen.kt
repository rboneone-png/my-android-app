package com.rboneone.stockrecord.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rboneone.stockrecord.data.AssetCategory
import com.rboneone.stockrecord.data.Market
import com.rboneone.stockrecord.domain.AllocationItem
import com.rboneone.stockrecord.domain.AllocationView
import com.rboneone.stockrecord.ui.AppViewModel
import com.rboneone.stockrecord.ui.formatAmount

private val CoreColor = Color(0xFF1E88E5)
private val LeveragedColor = Color(0xFFFB8C00)
private val CashLikeColor = Color(0xFF26A69A)
private val CashColor = Color(0xFF9E9E9E)
private val OtherColor = Color(0xFF8E24AA)

private fun AssetCategory.color() = when (this) {
    AssetCategory.CORE -> CoreColor
    AssetCategory.LEVERAGED_2X -> LeveragedColor
    AssetCategory.CASH_LIKE -> CashLikeColor
    AssetCategory.OTHER -> OtherColor
}

private fun twd(v: Double) = formatAmount(Market.TW, v)
private fun pctText(v: Double?) = v?.let { "%.1f%%".format(it) } ?: "--"

@Composable
fun AllocationScreen(vm: AppViewModel) {
    val allocation by vm.allocation.collectAsStateWithLifecycle()
    // 已展開的群組；預設全部收合
    var expandedGroups by rememberSaveable { mutableStateOf(setOf<String>()) }
    fun toggle(key: String) {
        expandedGroups = if (key in expandedGroups) expandedGroups - key else expandedGroups + key
    }

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar("資產配置")
        val a = allocation
        if (a == null) return@Column
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "summary") { SummaryCard(a) }

            val uncategorized = a.uncategorized
            if (uncategorized.isNotEmpty()) {
                item(key = "uncat-header") {
                    GroupHeader(
                        "尚未分類（${uncategorized.size}）",
                        "請為下列持股選擇分類，未分類的持股不會計入總資產",
                        MaterialTheme.colorScheme.error,
                        expanded = "uncat" in expandedGroups,
                        total = null,
                        onToggle = { toggle("uncat") },
                    )
                }
                if ("uncat" in expandedGroups) {
                    items(uncategorized, key = { "u-${it.position.holding.market}-${it.position.holding.symbol}" }) {
                        ItemCard(it, highlight = true) { c -> vm.setCategory(it.position.holding.market, it.position.holding.symbol, c) }
                    }
                }
            }
            AssetCategory.entries.forEach { category ->
                val list = a.items.filter { it.category == category }
                if (list.isEmpty()) return@forEach
                val groupKey = "cat-${category.name}"
                item(key = "header-$category") {
                    GroupHeader(
                        "${category.label}（${list.size}）", null, category.color(),
                        expanded = groupKey in expandedGroups,
                        total = list.sumOf { it.valueTwd ?: 0.0 },
                        onToggle = { toggle(groupKey) },
                    )
                }
                if (groupKey in expandedGroups) {
                    items(list, key = { "c-${it.position.holding.market}-${it.position.holding.symbol}" }) {
                        ItemCard(it, highlight = false) { c -> vm.setCategory(it.position.holding.market, it.position.holding.symbol, c) }
                    }
                }
            }
            if (a.items.isEmpty()) {
                item(key = "empty") { EmptyHint("目前沒有持股") }
            }
        }
    }
}

@Composable
private fun SummaryCard(a: AllocationView) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Text("總資產（台幣）", style = MaterialTheme.typography.bodyMedium)
                    Text(twd(a.total), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("曝險", style = MaterialTheme.typography.bodyMedium)
                    Text(twd(a.exposure), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(
                        "曝險比例 ${pctText(a.exposurePct)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            AllocationBar(a)
            AllocationRow("A", "原型市值", a.core, a.pct(a.core), CoreColor)
            AllocationRow("B", "正二市值", a.leveraged, a.pct(a.leveraged), LeveragedColor)
            AllocationRow("C", "類現金市值", a.cashLike, a.pct(a.cashLike), CashLikeColor)
            AllocationRow("D", "現金", a.cash, a.pct(a.cash), CashColor)
            AllocationRow("E", "其他市值", a.other, a.pct(a.other), OtherColor)
            HorizontalDivider()
            Text(
                "總資產 = A + B + C + D + E　　曝險 = A + 2 × B\nD 現金 = 帳務管理的當日可用餘額（持股總覽上方「帳務」）",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (a.missingFx) {
                Text(
                    "尚未取得美元匯率，美股暫不計入。請到持股總覽更新股價。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            if (a.items.any { it.position.quote == null }) {
                Text(
                    "部分持股尚無報價，以成本計算市值。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun AllocationBar(a: AllocationView) {
    val parts = listOf(
        a.core to CoreColor, a.leveraged to LeveragedColor, a.cashLike to CashLikeColor,
        a.cash to CashColor, a.other to OtherColor,
    )
        .filter { it.first > 0 }
    if (parts.isEmpty()) return
    Row(
        Modifier
            .fillMaxWidth()
            .height(14.dp)
            .clip(RoundedCornerShape(7.dp)),
    ) {
        parts.forEach { (value, color) ->
            Box(Modifier.weight(value.toFloat()).fillMaxHeight().background(color))
        }
    }
}

@Composable
private fun AllocationRow(
    letter: String,
    label: String,
    value: Double,
    pct: Double?,
    color: Color,
    trailing: @Composable () -> Unit = {},
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Text("  $letter. $label", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        trailing()
        Text(twd(value), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
        Text(
            pctText(pct),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

@Composable
private fun GroupHeader(
    title: String,
    subtitle: String?,
    color: Color,
    expanded: Boolean,
    /** 群組市值小計（台幣）；沒有時不顯示 */
    total: Double?,
    onToggle: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = if (expanded) "收合" else "展開", onClick = onToggle)
            .padding(top = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(width = 4.dp, height = 18.dp).background(color))
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 8.dp).weight(1f))
            total?.let {
                Text(twd(it), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(
                if (expanded) Icons.Filled.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = if (expanded) "收合" else "展開",
            )
        }
        subtitle?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ItemCard(item: AllocationItem, highlight: Boolean, onSelect: (AssetCategory?) -> Unit) {
    val h = item.position.holding
    Card(
        colors = if (highlight) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
        else CardDefaults.cardColors(),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(item.position.displayName, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${h.symbol}・${h.market.label}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    item.valueTwd?.let { twd(it) } ?: "--",
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            CategoryChips(item.category, onSelect)
        }
    }
}

/** 原型／正二／類現金單選；再按一次已選的項目可取消 */
@Composable
fun CategoryChips(selected: AssetCategory?, onSelect: (AssetCategory?) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AssetCategory.entries.forEach { c ->
            FilterChip(
                selected = selected == c,
                onClick = { onSelect(if (selected == c) null else c) },
                label = { Text(c.label) },
            )
        }
    }
}

