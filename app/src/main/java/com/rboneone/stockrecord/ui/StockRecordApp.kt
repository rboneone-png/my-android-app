package com.rboneone.stockrecord.ui

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.background
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
import com.rboneone.stockrecord.data.CashFlowType
import com.rboneone.stockrecord.data.Market
import com.rboneone.stockrecord.ui.screens.AccountingScreen
import com.rboneone.stockrecord.ui.screens.CashFlowEditScreen
import com.rboneone.stockrecord.ui.screens.LoanEditScreen
import com.rboneone.stockrecord.ui.screens.PledgeEditScreen
import com.rboneone.stockrecord.ui.screens.PledgePickScreen
import com.rboneone.stockrecord.ui.screens.PledgeReturnScreen
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
import com.rboneone.stockrecord.ui.screens.SplitEditScreen
import com.rboneone.stockrecord.ui.screens.SplitPickScreen
import com.rboneone.stockrecord.ui.screens.TradeEditScreen
import com.rboneone.stockrecord.ui.screens.TradesScreen
import com.rboneone.stockrecord.ui.screens.WatchlistScreen

private enum class Tab(val route: String, val label: String, val icon: ImageVector? = null, val iconRes: Int = 0) {
    HOLDINGS("holdings", "持股", Icons.Filled.Home),
    WATCH("watch", "觀察", Icons.Filled.Star),
    ALLOCATION("allocation", "配置", iconRes = R.drawable.ic_pie_chart),
    PLEDGE("pledge", "質押", iconRes = R.drawable.ic_pledge),
    MONTHLY("monthly", "月報酬", Icons.Filled.DateRange),
    ACCOUNTING("accounting", "帳務", iconRes = R.drawable.ic_wallet),
    SETTINGS("settings", "設定", Icons.Filled.Settings),
}

private const val RECORDS_ROUTE = "records"
private const val ALERTS_ROUTE = "alerts"
private const val SPLIT_PICK_ROUTE = "split-pick"
private const val PLEDGE_PICK_ROUTE = "pledge-pick"
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
        // 狀態列那一條由下面的色塊自己畫（和頂欄同色），Scaffold 只處理左右與下方
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
        bottomBar = {
            if (currentTab != null) {
                NavigationBar(tonalElevation = 0.dp, modifier = Modifier.shadow(8.dp)) {
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
                    FloatingActionButton(
                        onClick = { addMenu = true },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ) {
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
                        DropdownMenuItem(
                            text = { Text("股票分割") },
                            onClick = { addMenu = false; nav.navigate(SPLIT_PICK_ROUTE) },
                        )
                    }
                }
            }
        },
    ) { padding ->
      Column(Modifier.padding(padding)) {
        Spacer(
            Modifier.fillMaxWidth().windowInsetsTopHeight(WindowInsets.statusBars)
                .background(MaterialTheme.colorScheme.primaryContainer)
        )
        NavHost(
            navController = nav,
            startDestination = Tab.HOLDINGS.route,
            modifier = Modifier.weight(1f),
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
            // 帳務管理在底部導覽列（原本通知的位置）
            composable(Tab.ACCOUNTING.route) {
                AccountingScreen(
                    vm,
                    onAdd = { type -> nav.navigate("cash/0?type=${type.name}") },
                    onEdit = { nav.navigate("cash/$it") },
                    onAddLoan = { nav.navigate("loan/0") },
                    onEditLoan = { nav.navigate("loan/$it") },
                )
            }
            // 股票分割：先選庫存股，再進分割設定
            composable(SPLIT_PICK_ROUTE) {
                SplitPickScreen(
                    vm,
                    onPick = { market, symbol -> nav.navigate("split-edit/0?market=${market.name}&symbol=${Uri.encode(symbol)}") },
                    onOpenSplit = { nav.navigate("split-edit/$it") },
                    onBack = { nav.popBackStack() },
                )
            }
            composable(
                "split-edit/{id}?market={market}&symbol={symbol}",
                arguments = listOf(
                    navArgument("id") { type = NavType.LongType },
                    navArgument("market") { type = NavType.StringType; defaultValue = Market.TW.name },
                    navArgument("symbol") { type = NavType.StringType; defaultValue = "" },
                ),
            ) { entry ->
                SplitEditScreen(
                    vm = vm,
                    splitId = entry.arguments?.getLong("id") ?: 0,
                    initialMarket = Market.valueOf(entry.arguments?.getString("market") ?: Market.TW.name),
                    initialSymbol = entry.arguments?.getString("symbol").orEmpty(),
                    onBack = { nav.popBackStack() },
                    onDone = { nav.popBackStack(SPLIT_PICK_ROUTE, inclusive = false) },
                )
            }
            composable("loan/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
                LoanEditScreen(vm = vm, loanId = entry.arguments?.getLong("id") ?: 0, onDone = { nav.popBackStack() })
            }
            composable(
                "cash/{id}?type={type}",
                arguments = listOf(
                    navArgument("id") { type = NavType.LongType },
                    navArgument("type") { type = NavType.StringType; defaultValue = CashFlowType.DEPOSIT.name },
                ),
            ) { entry ->
                CashFlowEditScreen(
                    vm = vm,
                    flowId = entry.arguments?.getLong("id") ?: 0,
                    initialType = CashFlowType.valueOf(entry.arguments?.getString("type") ?: CashFlowType.DEPOSIT.name),
                    onDone = { nav.popBackStack() },
                )
            }
            composable("charts") { ChartsScreen(vm, onBack = { nav.popBackStack() }) }
            composable(Tab.ALLOCATION.route) { AllocationScreen(vm) }
            composable(Tab.PLEDGE.route) {
                PledgeScreen(
                    vm,
                    onEdit = { nav.navigate("pledge/$it") },
                    onAdd = { nav.navigate(PLEDGE_PICK_ROUTE) },
                    onReturn = { nav.navigate("pledge-return/$it") },
                )
            }
            // 新增質押：先選股票，再進質押設定
            composable(PLEDGE_PICK_ROUTE) {
                PledgePickScreen(
                    vm,
                    onPick = { symbol -> nav.navigate("pledge/0?symbol=${Uri.encode(symbol)}") },
                    onBack = { nav.popBackStack() },
                )
            }
            composable(
                "pledge/{id}?symbol={symbol}",
                arguments = listOf(
                    navArgument("id") { type = NavType.LongType },
                    navArgument("symbol") { type = NavType.StringType; defaultValue = "" },
                ),
            ) { entry ->
                PledgeEditScreen(
                    vm = vm,
                    pledgeId = entry.arguments?.getLong("id") ?: 0,
                    initialSymbol = entry.arguments?.getString("symbol").orEmpty(),
                    onBack = { nav.popBackStack() },
                    // 完成後回到質押頁（新增時中間還有選擇清單）
                    onDone = { if (!nav.popBackStack(Tab.PLEDGE.route, inclusive = false)) nav.popBackStack() },
                )
            }
            // 質借歸還設定（從質押頁的「質借歸還」選擇清單進入；已歸還的可再編輯）
            composable("pledge-return/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
                PledgeReturnScreen(
                    vm = vm,
                    pledgeId = entry.arguments?.getLong("id") ?: 0,
                    onBack = { nav.popBackStack() },
                    onDone = { nav.popBackStack(Tab.PLEDGE.route, inclusive = false) },
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
            composable(Tab.SETTINGS.route) { SettingsScreen(vm, onOpenAlerts = { nav.navigate(ALERTS_ROUTE) }) }
            // 股價通知：從設定頁的「股價通知」進入
            composable(ALERTS_ROUTE) {
                AlertsScreen(vm, onEdit = { nav.navigate("alert/$it") }, onBack = { nav.popBackStack() })
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
}
