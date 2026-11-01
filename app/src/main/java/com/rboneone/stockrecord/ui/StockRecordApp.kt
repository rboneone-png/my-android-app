package com.rboneone.stockrecord.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import com.rboneone.stockrecord.R
import com.rboneone.stockrecord.ui.screens.AlertEditScreen
import com.rboneone.stockrecord.ui.screens.AlertsScreen
import com.rboneone.stockrecord.ui.screens.AllocationScreen
import com.rboneone.stockrecord.ui.screens.ChartsScreen
import com.rboneone.stockrecord.ui.screens.PledgeEditScreen
import com.rboneone.stockrecord.ui.screens.PledgeScreen
import com.rboneone.stockrecord.ui.theme.pledgeColor
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.rboneone.stockrecord.ui.screens.DividendEditScreen
import com.rboneone.stockrecord.ui.screens.HoldingsScreen
import com.rboneone.stockrecord.ui.screens.MonthlyReturnScreen
import com.rboneone.stockrecord.ui.screens.SettingsScreen
import com.rboneone.stockrecord.ui.screens.TradeEditScreen
import com.rboneone.stockrecord.ui.screens.TradesScreen
import com.rboneone.stockrecord.ui.screens.WatchlistScreen

private enum class Tab(val route: String, val label: String, val icon: ImageVector? = null, val iconRes: Int = 0) {
    HOLDINGS("holdings", "持股", Icons.Filled.Home),
    WATCH("watch", "觀察", Icons.Filled.Star),
    ALLOCATION("allocation", "配置", iconRes = R.drawable.ic_pie_chart),
    PLEDGE("pledge", "質押", iconRes = R.drawable.ic_pledge),
    MONTHLY("monthly", "月報酬", Icons.Filled.DateRange),
    ALERTS("alerts", "通知", Icons.Filled.Notifications),
    SETTINGS("settings", "設定", Icons.Filled.Settings),
}

private const val RECORDS_ROUTE = "records"
private fun tradeRoute(id: Long = 0) = "trade/$id"
private fun dividendRoute(id: Long = 0) = "dividend/$id"

@Composable
fun StockRecordApp(vm: AppViewModel) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val currentTab = Tab.entries.firstOrNull { it.route == route }
    var showDividends by rememberSaveable { mutableStateOf(false) }
    var addMenu by remember { mutableStateOf(false) }
    val pledgeView by vm.pledgeView.collectAsStateWithLifecycle()
    // 質押分頁的文字跟著質押維持率變色
    val pledgeTabColor = pledgeColor(pledgeView?.ratio)

    Scaffold(
        bottomBar = {
            if (currentTab != null) {
                NavigationBar {
                    Tab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = tab == currentTab,
                            onClick = {
                                nav.navigate(tab.route) {
                                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(tab.icon ?: ImageVector.vectorResource(tab.iconRes), contentDescription = null)
                            },
                            label = { Text(tab.label) },
                            colors = if (tab == Tab.PLEDGE && pledgeTabColor != Color.Unspecified) {
                                NavigationBarItemDefaults.colors(
                                    selectedIconColor = pledgeTabColor,
                                    selectedTextColor = pledgeTabColor,
                                    unselectedIconColor = pledgeTabColor,
                                    unselectedTextColor = pledgeTabColor,
                                )
                            } else {
                                NavigationBarItemDefaults.colors()
                            },
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            if (currentTab == Tab.HOLDINGS) {
                Box {
                    FloatingActionButton(onClick = { addMenu = true }) {
                        Icon(Icons.Filled.Add, contentDescription = "新增")
                    }
                    DropdownMenu(expanded = addMenu, onDismissRequest = { addMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("新增買賣") },
                            onClick = { addMenu = false; nav.navigate(tradeRoute()) },
                        )
                        DropdownMenuItem(
                            text = { Text("新增股利") },
                            onClick = { addMenu = false; nav.navigate(dividendRoute()) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = Tab.HOLDINGS.route,
            modifier = Modifier.padding(padding),
        ) {
            composable(Tab.HOLDINGS.route) {
                HoldingsScreen(
                    vm,
                    onCategorize = {
                        nav.navigate(Tab.ALLOCATION.route) {
                            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                        }
                    },
                    onOpenCharts = { nav.navigate("charts") },
                    onOpenRecords = { nav.navigate(RECORDS_ROUTE) },
                )
            }
            composable("charts") { ChartsScreen(vm, onBack = { nav.popBackStack() }) }
            composable(Tab.ALLOCATION.route) { AllocationScreen(vm) }
            composable(Tab.PLEDGE.route) { PledgeScreen(vm, onEdit = { nav.navigate("pledge/$it") }) }
            composable(
                "pledge/{id}",
                arguments = listOf(navArgument("id") { type = NavType.LongType }),
            ) { entry ->
                PledgeEditScreen(
                    vm = vm,
                    pledgeId = entry.arguments?.getLong("id") ?: 0,
                    onDone = { nav.popBackStack() },
                )
            }
            composable(Tab.MONTHLY.route) { MonthlyReturnScreen(vm) }
            // 紀錄由持股總覽上方的按鈕進入，不在下方導覽列
            composable(RECORDS_ROUTE) {
                TradesScreen(
                    vm,
                    showDividends = showDividends,
                    onShowDividendsChange = { showDividends = it },
                    onTradeClick = { nav.navigate(tradeRoute(it.id)) },
                    onDividendClick = { nav.navigate(dividendRoute(it.id)) },
                    onBack = { nav.popBackStack() },
                    onAddTrade = { nav.navigate(tradeRoute()) },
                    onAddDividend = { nav.navigate(dividendRoute()) },
                )
            }
            composable(Tab.WATCH.route) { WatchlistScreen(vm) }
            composable(Tab.SETTINGS.route) { SettingsScreen(vm) }
            composable(Tab.ALERTS.route) {
                AlertsScreen(vm, onEdit = { nav.navigate("alert/$it") })
            }
            composable(
                "alert/{id}",
                arguments = listOf(navArgument("id") { type = NavType.LongType }),
            ) { entry ->
                AlertEditScreen(
                    vm = vm,
                    alertId = entry.arguments?.getLong("id") ?: 0,
                    onDone = { nav.popBackStack() },
                )
            }
            composable(
                "trade/{id}",
                arguments = listOf(navArgument("id") { type = NavType.LongType }),
            ) { entry ->
                TradeEditScreen(
                    vm = vm,
                    tradeId = entry.arguments?.getLong("id") ?: 0,
                    onDone = { nav.popBackStack() },
                )
            }
            composable(
                "dividend/{id}",
                arguments = listOf(navArgument("id") { type = NavType.LongType }),
            ) { entry ->
                DividendEditScreen(
                    vm = vm,
                    dividendId = entry.arguments?.getLong("id") ?: 0,
                    onDone = { nav.popBackStack() },
                )
            }
        }
    }
}
