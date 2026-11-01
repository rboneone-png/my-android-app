package com.rboneone.stockrecord.ui.screens

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable

/** 外層 Scaffold 已處理系統列間距，這裡不再重複套用 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenTopBar(
    title: String,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable () -> Unit = {},
) {
    TopAppBar(
        title = { Text(title) },
        navigationIcon = navigationIcon,
        actions = { actions() },
        windowInsets = WindowInsets(0, 0, 0, 0),
    )
}
