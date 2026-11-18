package com.rboneone.stockrecord.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
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
import com.rboneone.stockrecord.data.Market
import com.rboneone.stockrecord.domain.AddOn
import com.rboneone.stockrecord.domain.AllocationView
import com.rboneone.stockrecord.domain.Clec
import com.rboneone.stockrecord.ui.AppViewModel
import com.rboneone.stockrecord.ui.formatAmount
import com.rboneone.stockrecord.ui.plainNumber
import com.rboneone.stockrecord.ui.theme.AppCard as Card
import com.rboneone.stockrecord.ui.toAmountOrNull

private fun twd(v: Double) = formatAmount(Market.TW, v)
private fun pct(v: Double?) = v?.let { "%.1f%%".format(it) } ?: "--"

/** 資產配置頁的「加減碼策略」分頁 */
@Composable
fun AddOnTab(vm: AppViewModel, allocation: AllocationView) {
    val methodCode by vm.addonMethod.collectAsStateWithLifecycle()
    val reserve by vm.addonReserve.collectAsStateWithLifecycle()
    val tranches by vm.addonTranches.collectAsStateWithLifecycle()
    var reserveText by rememberSaveable { mutableStateOf(if (reserve > 0) plainNumber(reserve) else "") }
    var tranchesText by rememberSaveable { mutableStateOf(if (tranches > 0) plainNumber(tranches) else "") }
    val r = remember(allocation, methodCode, reserve, tranches) { AddOn.compute(allocation, methodCode, reserve, tranches) }

    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("加減碼策略", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                ReadOnlyField("A. 投入資金", twd(r.invested), supporting = "= 持股成本")
                ReadOnlyField("B. 持股市值", twd(r.marketValue), supporting = "= 持股市值（不含現金）")
                DropdownField(
                    "C. 配置方式",
                    "${r.method.code}　${r.method.mixText}",
                    Clec.PLANS.map { "${it.code}　${it.mixText}" },
                    Modifier.fillMaxWidth(),
                ) { vm.setAddonMethod(Clec.PLANS[it].code) }
                NumberField(
                    reserveText,
                    {
                        reserveText = it
                        vm.setAddonReserve(it.toAmountOrNull()?.takeIf { v -> v > 0 } ?: 0.0)
                    },
                    "D. 預留生活費（元）",
                    Modifier.fillMaxWidth(),
                    supporting = "尚在工作建議3~6個月，退休3年以上",
                )
            }
        }

        // E. 策略 VS 實際
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("E. 策略 VS 實際", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Row(Modifier.fillMaxWidth()) {
                    HeadCell("項目", 0.8f); HeadCell("實際", 1f); HeadCell("策略佔比", 1f); HeadCell("策略金額", 1.4f)
                }
                HorizontalDivider()
                CompareRow("原型", allocation.pct(allocation.core), r.method.corePct * 100, r.coreAmount)
                HorizontalDivider()
                CompareRow("正二", allocation.pct(allocation.leveraged), r.method.leveragedPct * 100, r.leveragedAmount)
                HorizontalDivider()
                // 類現金與現金合併：共用「類現金%」的策略值
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1.8f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.fillMaxWidth()) {
                            Cell("類現金", 0.8f); Cell(pct(allocation.pct(allocation.cashLike)), 1f)
                        }
                        Row(Modifier.fillMaxWidth()) {
                            Cell("現金", 0.8f); Cell(pct(allocation.pct(allocation.cash)), 1f)
                        }
                    }
                    Cell(pct(r.method.cashLikePct * 100), 1f)
                    Cell(twd(r.cashLikeAmount), 1.4f)
                }
                Text(
                    "實際 = 資產配置中各項佔總資產的比例；策略金額 = 投入資金 × 策略佔比（類現金與現金合併計算）。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // F. 加碼設定
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("F. 加碼設定", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                NumberField(
                    tranchesText,
                    {
                        tranchesText = it
                        vm.setAddonTranches(it.toAmountOrNull()?.takeIf { v -> v > 0 } ?: 0.0)
                    },
                    "a. 加碼等份",
                    Modifier.fillMaxWidth(),
                    supporting = "整數，例如 5",
                )
                ReadOnlyField(
                    "加碼金總額",
                    twd(r.addCapital),
                    supporting = if (r.addCapital <= 0) "預留生活費已超過投入資金的 30%，沒有可加碼的金額" else "= A × 0.3 − D",
                    isError = r.addCapital <= 0,
                )
                ReadOnlyField(
                    "b. 每次加碼金額",
                    r.perTranche?.let { twd(it) } ?: "--",
                    supporting = if (r.tranches == null) "請先輸入加碼等份（整數）" else "= (A × 0.3 − D) ÷ a",
                )
            }
        }

        // G. 減碼策略
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("G. 減碼策略", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text("加碼金減碼策略，當加碼每漲回10%，賣出加碼10%放回現金(類現金)，直到加碼金全部取回為止。", style = MaterialTheme.typography.bodyMedium)
                Text(
                    if (r.perReduce != null) "每次賣出金額 = 加碼金 × 10% = ${twd(r.perReduce)}，共 10 次取回 ${twd(r.addCapital)}。"
                    else "目前沒有可加碼的金額，所以沒有減碼金額。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.HeadCell(text: String, weight: Float) {
    Text(text, Modifier.weight(weight), style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.Cell(text: String, weight: Float) {
    Text(text, Modifier.weight(weight), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
}

@Composable
private fun CompareRow(label: String, actualPct: Double?, strategyPct: Double, amount: Double) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Cell(label, 0.8f); Cell(pct(actualPct), 1f); Cell(pct(strategyPct), 1f); Cell(twd(amount), 1.4f)
    }
}
