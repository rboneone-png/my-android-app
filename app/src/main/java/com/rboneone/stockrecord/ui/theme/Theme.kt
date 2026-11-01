package com.rboneone.stockrecord.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// 台灣習慣：紅色代表漲 / 獲利 / 買進，綠色代表跌 / 虧損 / 賣出
val ProfitRed = Color(0xFFD32F2F)
val LossGreen = Color(0xFF2E7D32)

fun pnlColor(value: Double): Color = when {
    value > 0.0001 -> ProfitRed
    value < -0.0001 -> LossGreen
    else -> Color.Unspecified
}

@Composable
fun StockRecordTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val colors = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialTheme(colorScheme = colors, content = content)
}

// 質押維持率：≥ 250% 綠、175%～250% 黃、< 175% 紅
val PledgeSafe = Color(0xFF2E7D32)
val PledgeWarning = Color(0xFFF9A825)
val PledgeDanger = Color(0xFFD32F2F)

fun pledgeColor(level: com.rboneone.stockrecord.domain.PledgeLevel): Color = when (level) {
    com.rboneone.stockrecord.domain.PledgeLevel.SAFE -> PledgeSafe
    com.rboneone.stockrecord.domain.PledgeLevel.WARNING -> PledgeWarning
    com.rboneone.stockrecord.domain.PledgeLevel.DANGER -> PledgeDanger
}

fun pledgeColor(ratio: Double?): Color =
    ratio?.let { pledgeColor(com.rboneone.stockrecord.domain.PledgeCalculator.level(it)) } ?: Color.Unspecified
