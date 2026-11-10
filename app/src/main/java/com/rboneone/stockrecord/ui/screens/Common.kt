package com.rboneone.stockrecord.ui.screens

import androidx.compose.foundation.layout.WindowInsets
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
