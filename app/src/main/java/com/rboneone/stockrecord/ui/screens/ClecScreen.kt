package com.rboneone.stockrecord.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rboneone.stockrecord.data.Market
import com.rboneone.stockrecord.domain.AllocationView
import com.rboneone.stockrecord.domain.Clec
import com.rboneone.stockrecord.ui.AppViewModel
import com.rboneone.stockrecord.ui.formatAmount
import com.rboneone.stockrecord.ui.plainNumber
import com.rboneone.stockrecord.ui.toAmountOrNull

private fun twd(v: Double) = formatAmount(Market.TW, v)

/** 資產配置頁的「CLEC退休投資策略」分頁 */
@Composable
fun ClecTab(vm: AppViewModel, allocation: AllocationView) {
    val livingCost by vm.clecLivingCost.collectAsStateWithLifecycle()
    val methodCode by vm.clecMethod.collectAsStateWithLifecycle()
    // 輸入框自己保留文字，避免輸入「12.」這種中間狀態被數字格式洗掉
    var livingText by rememberSaveable { mutableStateOf(if (livingCost > 0) plainNumber(livingCost) else "") }
    val overrides by vm.clecOverrides.collectAsStateWithLifecycle()
    val r = remember(allocation, livingCost, methodCode, overrides) { Clec.compute(allocation, livingCost, methodCode, overrides) }
    // 手動輸入時各自保留輸入框文字（切回自動就不用）
    var fText by rememberSaveable { mutableStateOf(overrides.indexEtf?.let { plainNumber(it) } ?: "") }
    var gText by rememberSaveable { mutableStateOf(overrides.leveragedEtf?.let { plainNumber(it) } ?: "") }
    var hText by rememberSaveable { mutableStateOf(overrides.cashLike?.let { plainNumber(it) } ?: "") }

    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("CLEC 退休投資策略", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                // A. 一年生活費：使用者輸入
                NumberField(
                    livingText,
                    {
                        livingText = it
                        vm.setClecLivingCost(it.toAmountOrNull()?.takeIf { v -> v > 0 } ?: 0.0)
                    },
                    "A. 一年生活費（元）",
                    Modifier.fillMaxWidth(),
                )
                // B. 配置方式：只能從下拉選單選
                DropdownField(
                    "B. 配置方式",
                    "${r.method.code}　${r.method.mixText}",
                    Clec.PLANS.map { "${it.code}　${it.mixText}" },
                    Modifier.fillMaxWidth(),
                ) { vm.setClecMethod(Clec.PLANS[it].code) }
                HorizontalDivider()
                ClecRow("C", "曝險（Beta）", "%.1f".format(r.beta), "= 第一碼 ${r.method.corePct} + 2 × 第二碼 ${r.method.leveragedPct}")
                // F、G、H：預設自動帶入資產配置頁的數字；直接修改就變成手動，按「自動」恢復
                AutoNumberField(
                    "F. 指數ETF（元）", r.indexEtf, r.indexEtfManual, fText,
                    onText = { fText = it; vm.setClecOverrides(overrides.copy(indexEtf = parseManual(it))) },
                    onAuto = { fText = ""; vm.setClecOverrides(overrides.copy(indexEtf = null)) },
                    note = if (r.indexEtfManual) "手動輸入（自動值：${twd(allocation.core)}）" else "自動：資產配置頁的原型市值",
                )
                AutoNumberField(
                    "G. 正二ETF（元）", r.leveragedEtf, r.leveragedEtfManual, gText,
                    onText = { gText = it; vm.setClecOverrides(overrides.copy(leveragedEtf = parseManual(it))) },
                    onAuto = { gText = ""; vm.setClecOverrides(overrides.copy(leveragedEtf = null)) },
                    note = if (r.leveragedEtfManual) "手動輸入（自動值：${twd(allocation.leveraged)}）" else "自動：資產配置頁的正二市值",
                )
                AutoNumberField(
                    "H. 類現金（元）", r.cashLike, r.cashLikeManual, hText,
                    onText = { hText = it; vm.setClecOverrides(overrides.copy(cashLike = parseManual(it))) },
                    onAuto = { hText = ""; vm.setClecOverrides(overrides.copy(cashLike = null)) },
                    note = if (r.cashLikeManual) "手動輸入（自動值：${twd(allocation.cashLike + allocation.cash)}）" else "自動：資產配置頁的類現金市值 + 現金",
                )
                ClecRow("D", "金融資產", twd(r.financialAssets), "= F + G + H")
                ClecRow(
                    "E", "曝險金額", twd(r.exposure),
                    if (r.indexEtfManual || r.leveragedEtfManual) "= F + 2 × G（F 或 G 已手動修改）" else "資產配置頁的曝險（原型 + 2 × 正二）",
                )
                HorizontalDivider()
                ClecRow(
                    "I", "幾倍年生活費",
                    r.years?.let { "%.1f 年".format(it) } ?: "--",
                    if (r.years == null) "請先輸入一年生活費" else "= D ÷ A，小數點一位（第二位四捨五入）",
                )
                val s = r.suggested
                ClecRow(
                    "J", "現況建議配置",
                    s?.code ?: "--",
                    s?.mixText ?: "請先輸入一年生活費",
                    emphasize = true,
                )
                // 依 J 的建議配置，把金融資產 D 分成三項建議數值
                HorizontalDivider()
                val mix = s?.let { listOf((it.code[0] - '0') * 10, (it.code[1] - '0') * 10, (it.code[2] - '0') * 10) }
                ClecRow(
                    "", "建議指數ETF", r.suggestedIndexEtf?.let { twd(it) } ?: "--",
                    if (mix != null) "= 金融資產 × 原型 ${mix[0]}%" else "請先輸入一年生活費",
                )
                ClecRow(
                    "", "建議正二ETF", r.suggestedLeveragedEtf?.let { twd(it) } ?: "--",
                    if (mix != null) "= 金融資產 × 正二 ${mix[1]}%" else "請先輸入一年生活費",
                )
                ClecRow(
                    "", "建議類現金", r.suggestedCashLike?.let { twd(it) } ?: "--",
                    if (mix != null) "= 金融資產 × 類現金 ${mix[2]}%" else "請先輸入一年生活費",
                )
                if (s != null && s.code != r.method.code) {
                    Text(
                        "目前選的配置方式是 ${r.method.code}，現況建議是 ${s.code}。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }

        // N 年生活費對照表：現況落在哪一列就標黃色
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("N 年生活費對照表", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Text("N 年生活費", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
                    Text("配置", Modifier.weight(0.5f), style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
                    Text("原型：正二：類現金", Modifier.weight(1.2f), style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.End)
                }
                Clec.PLANS.forEach { p ->
                    val current = r.suggested?.code == p.code
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(if (current) Color(0xFFFFEB3B).copy(alpha = 0.55f) else Color.Transparent)
                            .padding(horizontal = 4.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(p.rangeLabel, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        Text(
                            p.code, Modifier.weight(0.5f), style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                        )
                        Text(
                            "${(p.code[0] - '0') * 10}：${(p.code[1] - '0') * 10}：${(p.code[2] - '0') * 10}",
                            Modifier.weight(1.2f), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.End,
                        )
                    }
                }
                Text(
                    "配置三碼依序是 原型指數：正二槓桿：類現金 的佔比（各碼是幾成）。例如 703 → 70% 原型指數、0% 正二槓桿、30% 類現金。\n" +
                        "區間邊界：剛好 50 年算 40～50；其餘區間含下限（例如剛好 30 年算 30～40）。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

/** 空白（或無法解讀）時視為 0；有數字就用那個數字 */
private fun parseManual(text: String): Double = text.toAmountOrNull()?.coerceAtLeast(0.0) ?: 0.0

/** 自動帶入的數字欄位：沒手動修改時顯示自動值，改了就變手動並出現「自動」按鈕 */
@Composable
private fun AutoNumberField(
    label: String,
    value: Double,
    manual: Boolean,
    manualText: String,
    onText: (String) -> Unit,
    onAuto: () -> Unit,
    note: String,
) {
    NumberField(
        value = if (manual) manualText.ifEmpty { if (value != 0.0) Math.round(value).toString() else "" } else Math.round(value).toString(),
        onValueChange = onText,
        label = label,
        modifier = Modifier.fillMaxWidth(),
        supporting = note,
        onResetAuto = if (manual) onAuto else null,
    )
}

@Composable
private fun ClecRow(code: String, label: String, value: String, note: String, emphasize: Boolean = false) {
    Column {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(if (code.isEmpty()) label else "$code. $label", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            Text(
                value,
                style = if (emphasize) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
                fontWeight = if (emphasize) FontWeight.Bold else FontWeight.Medium,
            )
        }
        Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
