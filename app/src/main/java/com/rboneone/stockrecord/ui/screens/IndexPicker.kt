package com.rboneone.stockrecord.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.rboneone.stockrecord.data.Market
import com.rboneone.stockrecord.data.MarketIndex
import com.rboneone.stockrecord.data.MarketIndices

/** 觀察清單的副標題：指數顯示「指數・台股」，股票顯示「2330・台股」 */
fun watchSubtitle(market: Market, symbol: String): String =
    if (MarketIndices.isIndex(symbol)) "指數・${market.label}" else "$symbol・${market.label}"

/** 新增觀察時選擇大盤指數 */
@Composable
fun IndexPicker(selected: MarketIndex?, onSelect: (MarketIndex) -> Unit) {
    Column {
        MarketIndices.ALL.forEach { index ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .selectable(selected = index == selected, role = Role.RadioButton, onClick = { onSelect(index) })
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = index == selected, onClick = null)
                Text(index.name, Modifier.weight(1f).padding(start = 8.dp))
                Text(
                    index.market.label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
