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
    val months by vm.addonMonths.collectAsStateWithLifecycle()
    val monthly by vm.addonMonthly.collectAsStateWithLifecycle()
    val tranches by vm.addonTranches.collectAsStateWithLifecycle()
    val investedOverride by vm.addonInvested.collectAsStateWithLifecycle()
    var investedText by rememberSaveable { mutableStateOf(investedOverride?.let { plainNumber(it) } ?: "") }
    var monthsText by rememberSaveable { mutableStateOf(if (months > 0) plainNumber(months) else "") }
    var monthlyText by rememberSaveable { mutableStateOf(if (monthly > 0) plainNumber(monthly) else "") }
    var tranchesText by rememberSaveable { mutableStateOf(if (tranches > 0) plainNumber(tranches) else "") }
    val r = remember(allocation, methodCode, investedOverride, months, monthly, tranches) {
        AddOn.compute(allocation, methodCode, investedOverride, months, monthly, tranches)
    }

    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("加減碼策略", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                // A：預設自動（持股成本），可手動輸入，按「自動」恢復
                NumberField(
                    if (r.investedManual) investedText.ifEmpty { Math.round(r.invested).toString() } else Math.round(r.invested).toString(),
                    {
                        investedText = it
                        vm.setAddonInvested(it.toAmountOrNull()?.coerceAtLeast(0.0) ?: 0.0)
                    },
                    "A. 投入資金（元）",
                    Modifier.fillMaxWidth(),
                    supporting = if (r.investedManual) "手動（自動值 ${twd(allocation.holdingCost)}）" else "自動：持股成本",
                    onResetAuto = if (r.investedManual) ({ investedText = ""; vm.setAddonInvested(null) }) else null,
                )
                ReadOnlyField("B. 持股市值", twd(r.marketValue), supporting = "= 持股市值（不含現金）")
                DropdownField(
                    "C. 配置方式",
                    "${r.method.code}　${r.method.mixText}",
                    Clec.PLANS.map { "${it.code}　${it.mixText}" },
                    Modifier.fillMaxWidth(),
                ) { vm.setAddonMethod(Clec.PLANS[it].code) }
                Text("D. 預留生活費", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                NumberField(
                    monthsText,
                    {
                        monthsText = it
                        vm.setAddonMonths(it.toAmountOrNull()?.takeIf { v -> v > 0 } ?: 0.0)
                    },
                    "1. 預留月數",
                    Modifier.fillMaxWidth(),
                    supporting = "尚在工作建議3~6個月，退休3年以上",
                )
                StepNumberField(
                    monthlyText,
                    { monthlyText = it; vm.setAddonMonthly(it.toAmountOrNull()?.takeIf { v -> v > 0 } ?: 0.0) },
                    "2. 每月生活費（元）",
                    step = 10_000.0,
                    supporting = "按 ▲▼ 每次增減 1 萬，按住不放可快速增減",
                )
                ReadOnlyField("預留生活費", twd(r.months * r.monthly), supporting = "= 預留月數 × 每月生活費")
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
                StepNumberField(
                    tranchesText,
                    { tranchesText = it; vm.setAddonTranches(it.toAmountOrNull()?.takeIf { v -> v > 0 } ?: 0.0) },
                    "a. 加碼等份",
                    step = 1.0,
                    supporting = "整數，例如 5；按 ▲▼ 每次增減 1，按住不放可快速增減",
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

/** 數字輸入框，右側有上下鍵（按一次增減 step，按住不放連續增減，最小 0） */
@Composable
private fun StepNumberField(value: String, onChange: (String) -> Unit, label: String, step: Double, supporting: String) {
    val current = value.toAmountOrNull() ?: 0.0
    androidx.compose.material3.OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        supportingText = { Text(supporting) },
        trailingIcon = {
            StepButtons(
                onUp = { onChange(plainNumber(current + step)) },
                onDown = { onChange(plainNumber(maxOf(0.0, current - step))) },
            )
        },
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth(),
    )
}
