package com.rboneone.stockrecord.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rboneone.stockrecord.data.Market
import com.rboneone.stockrecord.domain.PledgeCalculator
import com.rboneone.stockrecord.domain.PledgeItem
import com.rboneone.stockrecord.domain.PledgeView
import com.rboneone.stockrecord.ui.AppViewModel
import com.rboneone.stockrecord.ui.formatAmount
import com.rboneone.stockrecord.ui.formatShares
import com.rboneone.stockrecord.ui.theme.PledgeDanger
import com.rboneone.stockrecord.ui.theme.PledgeSafe
import com.rboneone.stockrecord.ui.theme.PledgeWarning
import com.rboneone.stockrecord.ui.theme.pledgeColor

private fun twd(v: Double) = formatAmount(Market.TW, v)
private fun ratioText(r: Double?) = r?.let { "%.1f%%".format(it) } ?: "--"

@Composable
fun PledgeScreen(vm: AppViewModel, onEdit: (Long) -> Unit) {
    val view by vm.pledgeView.collectAsStateWithLifecycle()
    val includeLeveraged by vm.pledgeIncludeLeveraged.collectAsStateWithLifecycle()

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            ScreenTopBar("質押")
            val v = view ?: return@Column
            LazyColumn(
                contentPadding = PaddingValues(bottom = 96.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                item(key = "summary") { SummaryCard(v, includeLeveraged, vm::setPledgeIncludeLeveraged) }
                item(key = "list-header") {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.width(4.dp).height(18.dp).background(MaterialTheme.colorScheme.primary))
                        Text(
                            "質押清單(${v.items.size})",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
                item(key = "table-header") { TableHeader() }
                if (v.items.isEmpty()) {
                    item(key = "empty") { EmptyHint("還沒有質押紀錄\n按右下角「新增質押」") }
                }
                items(v.items, key = { it.pledge.id }) {
                    PledgeRow(it, onClick = { onEdit(it.pledge.id) })
                    HorizontalDivider()
                }
                item(key = "note") {
                    Text(
                        "利息支出 = 質押利率 × 質押金額（年）\n" +
                            "維持率 = 質押股數 × 股價 ÷ 質押金額 × 100%\n" +
                            "質押維持率 = 質押總市值 ÷ 質押總金額 × 100%，低於 175% 時會發出通知",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = { onEdit(0) },
            icon = { Icon(Icons.Filled.Add, contentDescription = null) },
            text = { Text("新增質押") },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        )
    }
}

@Composable
private fun SummaryCard(v: PledgeView, includeLeveraged: Boolean, onIncludeLeveragedChange: (Boolean) -> Unit) {
    val ratio = v.ratio
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth().padding(16.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("可質押股票總市值", style = MaterialTheme.typography.bodyMedium)
                    Text(twd(v.pledgeableValue), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                }
                // 點文字或開關都可以切換
                Column(
                    Modifier.toggleable(
                        value = includeLeveraged,
                        role = Role.Switch,
                        onValueChange = onIncludeLeveragedChange,
                    ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("包含正二", style = MaterialTheme.typography.bodySmall)
                    Switch(checked = includeLeveraged, onCheckedChange = null)
                }
            }
            Row {
                Cell("質押總市值", twd(v.totalMarketValue), Modifier.weight(1f))
                Cell("質押總金額", twd(v.totalAmount), Modifier.weight(1f))
                Cell("質押維持率", ratioText(ratio), Modifier.weight(1f), pledgeColor(ratio), bold = true)
                Cell("利息總支出", twd(v.totalInterest), Modifier.weight(1f))
            }
            if (v.missingQuotes > 0) {
                Text(
                    "有 ${v.missingQuotes} 檔股票尚未取得股價，請到持股總覽更新股價。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Legend()
        }
    }
}

@Composable
private fun Cell(label: String, value: String, modifier: Modifier, color: Color = Color.Unspecified, bold: Boolean = false) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            value,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium,
            color = color,
            maxLines = 1,
        )
    }
}

@Composable
private fun Legend() {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        listOf(
            "≥ ${PledgeCalculator.SAFE_RATIO.toInt()}%" to PledgeSafe,
            "${PledgeCalculator.DANGER_RATIO.toInt()}%～${PledgeCalculator.SAFE_RATIO.toInt()}%" to PledgeWarning,
            "< ${PledgeCalculator.DANGER_RATIO.toInt()}%" to PledgeDanger,
        ).forEach { (label, color) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(10.dp).height(10.dp).background(color))
                Text(" $label", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

private const val W_STOCK = 1.3f
private const val W_SHARES = 1f
private const val W_AMOUNT = 1.2f
private const val W_INTEREST = 1f
private const val W_RATIO = 1f

@Composable
private fun TableHeader() {
    val style = MaterialTheme.typography.bodySmall
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text("股票", Modifier.weight(W_STOCK), style = style, color = color)
        Text("質押股數", Modifier.weight(W_SHARES), style = style, color = color, textAlign = TextAlign.End)
        Text("質押金額", Modifier.weight(W_AMOUNT), style = style, color = color, textAlign = TextAlign.End)
        Text("利息支出", Modifier.weight(W_INTEREST), style = style, color = color, textAlign = TextAlign.End)
        Text("維持率", Modifier.weight(W_RATIO), style = style, color = color, textAlign = TextAlign.End)
    }
    HorizontalDivider()
}

@Composable
private fun PledgeRow(item: PledgeItem, onClick: () -> Unit) {
    val p = item.pledge
    val style = MaterialTheme.typography.bodyMedium
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(W_STOCK)) {
            Text(p.name, style = MaterialTheme.typography.titleSmall, maxLines = 2)
            Text(p.symbol, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(formatShares(p.shares), Modifier.weight(W_SHARES), style = style, textAlign = TextAlign.End)
        Text(twd(p.amount), Modifier.weight(W_AMOUNT), style = style, textAlign = TextAlign.End)
        Text(twd(p.interest), Modifier.weight(W_INTEREST), style = style, textAlign = TextAlign.End)
        Text(
            ratioText(item.ratio),
            Modifier.weight(W_RATIO),
            style = style,
            fontWeight = FontWeight.Bold,
            color = pledgeColor(item.ratio),
            textAlign = TextAlign.End,
        )
    }
}
