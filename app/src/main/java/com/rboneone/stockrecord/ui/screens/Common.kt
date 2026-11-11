package com.rboneone.stockrecord.ui.screens

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.composed
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight

/** 外層 Scaffold 已處理系統列間距，這裡不再重複套用；頂欄是淡藍底、深藍粗體標題 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenTopBar(
    title: String,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable () -> Unit = {},
) {
    val scheme = MaterialTheme.colorScheme
    TopAppBar(
        title = { Text(title, fontWeight = FontWeight.Bold) },
        navigationIcon = navigationIcon,
        actions = { actions() },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = scheme.primaryContainer,
            titleContentColor = scheme.onPrimaryContainer,
            navigationIconContentColor = scheme.onPrimaryContainer,
            actionIconContentColor = scheme.onPrimaryContainer,
        ),
        windowInsets = WindowInsets(0, 0, 0, 0),
    )
}

/** 左右滑動多遠算一次切換（dp）；太小容易和上下捲動、點擊混淆 */
private const val SWIPE_THRESHOLD_DP = 72

/**
 * 手勢切換分頁：往左滑 → onSwipe(+1)（下一個），往右滑 → onSwipe(-1)（上一個）。
 * enabled 為 false（設定裡關閉）時完全不處理手勢。上下捲動、長按拖曳排序等子元件已經處理的手勢不會觸發。
 */
fun Modifier.swipeToSwitch(enabled: Boolean, onSwipe: (direction: Int) -> Unit): Modifier =
    if (!enabled) this else composed {
        val threshold = with(LocalDensity.current) { SWIPE_THRESHOLD_DP.dp.toPx() }
        val current by rememberUpdatedState(onSwipe)
        pointerInput(threshold) {
            var total = 0f
            detectHorizontalDragGestures(
                onDragStart = { total = 0f },
                onDragCancel = { total = 0f },
                onDragEnd = { if (abs(total) >= threshold) current(if (total < 0) 1 else -1) },
                onHorizontalDrag = { _, dx -> total += dx },
            )
        }
    }
