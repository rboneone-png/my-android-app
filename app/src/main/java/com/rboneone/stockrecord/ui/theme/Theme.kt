package com.rboneone.stockrecord.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CardElevation
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// 台灣習慣：紅色代表漲 / 獲利 / 買進，綠色代表跌 / 虧損 / 賣出
val ProfitRed = Color(0xFFD32F2F)
val LossGreen = Color(0xFF2E7D32)

fun pnlColor(value: Double): Color = when {
    value > 0.0001 -> ProfitRed
    value < -0.0001 -> LossGreen
    else -> Color.Unspecified
}

/** 文字中的「上漲」標紅色、「下跌」標綠色，其餘維持原本顏色 */
fun riseFallColored(text: String): AnnotatedString = buildAnnotatedString {
    append(text)
    // 漲幅／漲到／上漲 用紅色，跌幅／跌到／下跌 用綠色
    Regex("漲幅|漲到|跌幅|跌到|上漲|下跌").findAll(text).forEach { m ->
        val color = if (m.value.contains("漲")) ProfitRed else LossGreen
        addStyle(SpanStyle(color = color, fontWeight = FontWeight.Bold), m.range.first, m.range.last + 1)
    }
}

// ---- 色彩：固定的品牌配色（深藍為主色，不隨桌布變色），亮色／暗色各一組 ----

private val LightColors = lightColorScheme(
    primary = Color(0xFF1F3F73), onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE6F7), onPrimaryContainer = Color(0xFF0B2347),
    secondary = Color(0xFF4F6B94), onSecondary = Color.White,
    secondaryContainer = Color(0xFFD6E3F7), onSecondaryContainer = Color(0xFF14284A),
    tertiary = Color(0xFFB7791F), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFE7BF), onTertiaryContainer = Color(0xFF3A2500),
    background = Color(0xFFF1F4F9), onBackground = Color(0xFF161C27),
    surface = Color(0xFFF1F4F9), onSurface = Color(0xFF161C27),
    surfaceVariant = Color(0xFFE2E9F4), onSurfaceVariant = Color(0xFF4B5567),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF7F9FD),
    surfaceContainer = Color.White, surfaceContainerHigh = Color(0xFFE9EEF6), surfaceContainerHighest = Color(0xFFE2E9F4),
    outline = Color(0xFF7C879A), outlineVariant = Color(0xFFD3DBE8),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA9C4F2), onPrimary = Color(0xFF0C2347),
    primaryContainer = Color(0xFF2A4777), onPrimaryContainer = Color(0xFFDCE6F7),
    secondary = Color(0xFFB0C4E3), onSecondary = Color(0xFF14284A),
    secondaryContainer = Color(0xFF2D4263), onSecondaryContainer = Color(0xFFDDE7F5),
    tertiary = Color(0xFFF0C070), onTertiary = Color(0xFF3A2500),
    tertiaryContainer = Color(0xFF5A3C00), onTertiaryContainer = Color(0xFFFFE7BF),
    background = Color(0xFF0F141D), onBackground = Color(0xFFE3E8F1),
    surface = Color(0xFF0F141D), onSurface = Color(0xFFE3E8F1),
    surfaceVariant = Color(0xFF222C3E), onSurfaceVariant = Color(0xFFB6C0D1),
    surfaceContainerLowest = Color(0xFF171E2B), surfaceContainerLow = Color(0xFF141A25),
    surfaceContainer = Color(0xFF182030), surfaceContainerHigh = Color(0xFF222B3B), surfaceContainerHighest = Color(0xFF2A3446),
    outline = Color(0xFF8793A8), outlineVariant = Color(0xFF334055),
)

/** 圓角加大：卡片 16、輸入框 10、對話框 28 */
private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/** 標題加粗；數字用等寬數字（tnum），金額上下對齊、跳動時不會晃動 */
private val AppTypography: Typography = Typography().let { t ->
    fun TextStyle.num() = copy(fontFeatureSettings = "tnum")
    t.copy(
        headlineMedium = t.headlineMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp, fontFeatureSettings = "tnum"),
        headlineSmall = t.headlineSmall.copy(fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum"),
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum"),
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum"),
        titleSmall = t.titleSmall.copy(fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum"),
        bodyLarge = t.bodyLarge.num(),
        bodyMedium = t.bodyMedium.num(),
        bodySmall = t.bodySmall.num(),
        labelLarge = t.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    )
}

@Composable
fun StockRecordTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        shapes = AppShapes,
        typography = AppTypography,
        content = content,
    )
}

/** 全 App 共用的卡片：白底、大圓角、輕微陰影，浮在淺灰藍的頁面背景上 */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.medium,
    colors: CardColors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
    elevation: CardElevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    content: @Composable ColumnScope.() -> Unit,
) = Card(modifier = modifier, shape = shape, colors = colors, elevation = elevation, content = content)

/** 重點摘要卡（總覽、總資產…）：淡藍到白的漸層、較大的圓角與陰影 */
@Composable
fun HeroCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
    ) {
        Column(
            Modifier.background(
                Brush.linearGradient(listOf(scheme.primaryContainer, scheme.surfaceContainerLowest)),
            ),
            content = content,
        )
    }
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
