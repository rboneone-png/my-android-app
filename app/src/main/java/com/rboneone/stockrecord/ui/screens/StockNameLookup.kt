package com.rboneone.stockrecord.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.rboneone.stockrecord.data.LookupResult
import com.rboneone.stockrecord.data.Market
import com.rboneone.stockrecord.ui.AppViewModel
import kotlinx.coroutines.delay

/**
 * 輸入股票代號後自動查詢名稱。
 * enabled 為 false（使用者自己輸入過名稱）時不查詢。回傳要顯示在名稱欄位下方的提示。
 */
@Composable
fun rememberStockNameLookup(
    vm: AppViewModel,
    market: Market,
    symbol: String,
    enabled: Boolean,
    onName: (String) -> Unit,
): String? {
    var hint by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(market, symbol, enabled) {
        val sym = symbol.trim().uppercase()
        if (!enabled || sym.isEmpty()) {
            hint = null
            return@LaunchedEffect
        }
        delay(600) // 等使用者打完字再查詢
        hint = "查詢名稱中…"
        hint = when (val r = vm.lookupStock(market, sym)) {
            is LookupResult.Found -> {
                onName(r.info.name)
                "已自動帶入名稱"
            }
            LookupResult.NotFound -> {
                onName("")
                "查不到這個代號，請確認代號或手動輸入名稱"
            }
            LookupResult.NetworkError -> "無法連線，請手動輸入名稱"
        }
    }
    return hint
}
