package com.rboneone.stockrecord.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rboneone.stockrecord.StockApp
import com.rboneone.stockrecord.auth.Account
import com.rboneone.stockrecord.auth.AuthMethod
import com.rboneone.stockrecord.data.AssetCategory
import com.rboneone.stockrecord.data.Dividend
import com.rboneone.stockrecord.data.LookupResult
import com.rboneone.stockrecord.data.Market
import com.rboneone.stockrecord.data.Pledge
import com.rboneone.stockrecord.data.PriceAlert
import com.rboneone.stockrecord.data.StockCategory
import com.rboneone.stockrecord.data.SyncConfig
import com.rboneone.stockrecord.data.Trade
import com.rboneone.stockrecord.data.WatchItem
import com.rboneone.stockrecord.data.HighWatchItem
import com.rboneone.stockrecord.domain.Allocation
import com.rboneone.stockrecord.domain.AllocationView
import com.rboneone.stockrecord.domain.DailyReturns
import com.rboneone.stockrecord.domain.MonthIncome
import com.rboneone.stockrecord.domain.MonthlyIncome
import com.rboneone.stockrecord.export.ExcelExporter
import com.rboneone.stockrecord.export.XlsxWriter
import com.rboneone.stockrecord.domain.DayReturn
import com.rboneone.stockrecord.domain.FeeSettings
import com.rboneone.stockrecord.domain.Holding
import com.rboneone.stockrecord.domain.PeriodReturn
import com.rboneone.stockrecord.domain.PledgeCalculator
import com.rboneone.stockrecord.domain.PledgeView
import com.rboneone.stockrecord.domain.Portfolio
import com.rboneone.stockrecord.domain.PortfolioView
import com.rboneone.stockrecord.domain.Valuation
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

data class MonthState(
    val market: Market = Market.TW,
    val month: YearMonth = YearMonth.now(),
    val loading: Boolean = false,
    val days: List<DayReturn> = emptyList(),
    val total: PeriodReturn? = null,
    val error: String? = null,
)

/** 每個登入的帳號各自一個 ViewModel，資料彼此分開 */
class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as StockApp
    val account: StateFlow<Account?> = app.session
    private val userId: String = requireNotNull(app.session.value) { "尚未登入" }.id

    private val db = app.database(userId)
    private val tradeDao = db.tradeDao()
    private val dividendDao = db.dividendDao()
    private val categoryDao = db.categoryDao()
    private val alertDao = db.alertDao()
    private val pledgeDao = db.pledgeDao()
    private val watchDao = db.watchDao()
    private val highWatchDao = db.highWatchDao()
    private val settingsRepository = app.settings(userId)
    private val marketRepository = app.marketRepository(userId)

    val trades: StateFlow<List<Trade>> = tradeDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val dividends: StateFlow<List<Dividend>> = dividendDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val holdings: StateFlow<List<Holding>> = combine(trades, dividends) { t, d -> Portfolio.build(t, d) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val settings: StateFlow<FeeSettings> = settingsRepository.settings
    val refreshSeconds: StateFlow<Int> = settingsRepository.refreshSeconds
    val sync: StateFlow<SyncConfig> = settingsRepository.sync

    val portfolio: StateFlow<PortfolioView?> = combine(
        holdings,
        marketRepository.observeQuotes(),
        marketRepository.observeInfo(),
        settings,
    ) { holdings, quotes, infos, settings ->
        Valuation.build(
            holdings = holdings,
            quotes = quotes.associateBy { it.market to it.symbol },
            names = infos.associate { (it.market to it.symbol) to it.name },
            settings = settings,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    // ---- 資產配置 ----

    val categories: StateFlow<Map<Pair<Market, String>, AssetCategory>> = categoryDao.observeAll()
        .map { list -> list.associate { (it.market to it.symbol) to it.category } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val cash: StateFlow<Double> = settingsRepository.cash

    val holdingOrder: StateFlow<Map<Market, List<String>>> = settingsRepository.holdingOrder

    fun setHoldingOrder(market: Market, symbols: List<String>) = settingsRepository.setHoldingOrder(market, symbols)

    val allocation: StateFlow<AllocationView?> = combine(
        portfolio.filterNotNull(),
        categories,
        cash,
    ) { view, categories, cash -> Allocation.build(view, categories, cash) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** 尚未分類的持股數量，持股總覽用來提醒 */
    val uncategorizedCount: StateFlow<Int> = combine(holdings, categories) { holdings, categories ->
        holdings.count { it.isOpen && (it.market to it.symbol) !in categories }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** category 為 null 時清除分類 */
    fun setCategory(market: Market, symbol: String, category: AssetCategory?) {
        viewModelScope.launch {
            if (category == null) categoryDao.delete(market, symbol)
            else categoryDao.upsert(StockCategory(market, symbol, category))
        }
    }

    suspend fun getCategory(market: Market, symbol: String): AssetCategory? = categoryDao.get(market, symbol)?.category

    fun setCash(amount: Double) = settingsRepository.setCash(amount)

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    /** 給畫面顯示一次的提示訊息 */
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private var lastRefresh = 0L

    init {
        val s = sync.value
        if (s.auto && s.googleLinked) app.syncManager.schedule(userId, s.intervalHours, replace = false)
    }

    /** force = false 時，10 秒內不重複更新 */
    fun refreshQuotes(force: Boolean = true, quiet: Boolean = false) {
        if (_refreshing.value) return
        if (!force && System.currentTimeMillis() - lastRefresh < 10_000) return
        viewModelScope.launch {
            _refreshing.value = !quiet
            try {
                val open = Portfolio.build(tradeDao.getAll(), dividendDao.getAll())
                    .filter { it.isOpen }
                    .map { it.market to it.symbol }
                // 通知清單裡的股票不一定有持有，也一起更新
                val alertStocks = alertDao.getEnabled().map { it.market to it.symbol }
                val pledgeStocks = pledgeDao.getAll().map { Market.TW to it.symbol }
                val watchStocks = watchDao.getAll().map { it.market to it.symbol } +
                    highWatchDao.getAll().map { it.market to it.symbol }
                val result = marketRepository.refreshQuotes((open + alertStocks + pledgeStocks + watchStocks).distinct())
                lastRefresh = System.currentTimeMillis()
                app.alertChecker.check(userId, refresh = false)
                // 自動刷新時不重複跳出錯誤訊息
                if (!quiet) {
                    _message.value = when {
                        result.offline -> "無法連線，顯示的是上次的報價"
                        result.failed.isNotEmpty() -> "部分報價更新失敗：${result.failed.joinToString("、")}"
                        else -> null
                    }
                }
            } finally {
                _refreshing.value = false
            }
        }
    }

    fun clearMessage() {
        _message.value = null
    }

    // ---- 月報酬 ----

    private val _month = MutableStateFlow(MonthState())
    val month: StateFlow<MonthState> = _month.asStateFlow()
    private var monthJob: Job? = null

    fun loadMonth(market: Market, month: YearMonth) {
        monthJob?.cancel()
        _month.value = MonthState(market, month, loading = true)
        monthJob = viewModelScope.launch {
            val trades = tradeDao.getAll().filter { it.market == market }
            val dividends = dividendDao.getAll().filter { it.market == market }
            val today = LocalDate.now().toEpochDay()
            val from = month.atDay(1).toEpochDay()
            val to = minOf(month.atEndOfMonth().toEpochDay(), today)
            if (from > today) {
                _month.value = MonthState(market, month)
                return@launch
            }
            // 往前多抓幾天，才能算出月初第一天的損益
            val windowStart = from - 15
            val symbols = (trades.map { it.symbol } + dividends.map { it.symbol }).distinct().filter { s ->
                val t = trades.filter { it.symbol == s }
                val d = dividends.filter { it.symbol == s }
                Portfolio.sharesOn(t, d, windowStart) > 1e-9 ||
                    t.any { it.date in windowStart..to } || d.any { it.date in windowStart..to }
            }
            val cacheable = month.atEndOfMonth().toEpochDay() < today
            val bars = symbols.map { s ->
                async { s to runCatching { marketRepository.history(market, s, windowStart, to, cacheable) }.getOrNull() }
            }.awaitAll()
            val failed = bars.filter { it.second == null }.map { it.first }
            val days = DailyReturns.compute(
                trades.filter { it.symbol in symbols },
                dividends.filter { it.symbol in symbols },
                bars.mapNotNull { (s, b) -> b?.let { s to it } }.toMap(),
                from,
                to,
            )
            _month.value = MonthState(
                market = market,
                month = month,
                days = days,
                total = DailyReturns.combine(days),
                error = if (failed.isEmpty()) null else "無法取得歷史股價：${failed.joinToString("、")}",
            )
        }
    }

    /** 最早一筆紀錄的月份，用來限制往前翻的範圍 */
    fun earliestMonth(): YearMonth {
        val first = (trades.value.map { it.date } + dividends.value.map { it.date }).minOrNull()
        return first?.let { YearMonth.from(LocalDate.ofEpochDay(it)) } ?: YearMonth.now()
    }

    // ---- 股價通知 ----

    val alerts: StateFlow<List<PriceAlert>> = alertDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 給通知列表顯示目前漲跌用 */
    val quotes = marketRepository.observeQuotes()
        .map { list -> list.associateBy { it.market to it.symbol } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    suspend fun getAlert(id: Long): PriceAlert? = alertDao.get(id)

    fun saveAlert(alert: PriceAlert) {
        viewModelScope.launch {
            alertDao.upsert(alert)
            // 新增後馬上抓一次報價並檢查
            marketRepository.refreshQuotes(listOf(alert.market to alert.symbol))
            app.alertChecker.check(userId, refresh = false)
        }
    }

    fun setAlertEnabled(alert: PriceAlert, enabled: Boolean) {
        viewModelScope.launch { alertDao.upsert(alert.copy(enabled = enabled)) }
    }

    fun deleteAlert(alert: PriceAlert) {
        viewModelScope.launch { alertDao.delete(alert) }
    }

    // ---- 質押 ----

    val pledges: StateFlow<List<Pledge>> = pledgeDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val pledgeIncludeLeveraged: StateFlow<Boolean> = settingsRepository.pledgeIncludeLeveraged

    fun setPledgeIncludeLeveraged(include: Boolean) = settingsRepository.setPledgeIncludeLeveraged(include)

    val pledgeView: StateFlow<PledgeView?> = combine(
        pledges, quotes, portfolio, categories, pledgeIncludeLeveraged,
    ) { pledges, quotes, portfolio, categories, includeLev ->
        PledgeCalculator.build(pledges, quotes, portfolio, categories, includeLev)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    suspend fun getPledge(id: Long): Pledge? = pledgeDao.get(id)

    fun savePledge(pledge: Pledge) {
        viewModelScope.launch {
            pledgeDao.upsert(pledge)
            // 新增後馬上抓股價，維持率才會立即顯示
            marketRepository.refreshQuotes(listOf(Market.TW to pledge.symbol))
            app.alertChecker.check(userId, refresh = false)
        }
    }

    fun deletePledge(pledge: Pledge) {
        viewModelScope.launch { pledgeDao.delete(pledge) }
    }

    // ---- 觀察股票 ----

    val watchlist: StateFlow<List<WatchItem>> = watchDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 回傳 false 表示已經在觀察清單中 */
    suspend fun addWatch(market: Market, symbol: String, name: String): Boolean {
        if (watchDao.count(market, symbol) > 0) return false
        watchDao.insert(WatchItem(market = market, symbol = symbol, name = name, position = watchDao.maxPosition() + 1))
        viewModelScope.launch { marketRepository.refreshQuotes(listOf(market to symbol)) }
        return true
    }

    fun removeWatch(item: WatchItem) {
        viewModelScope.launch { watchDao.delete(item) }
    }

    fun reorderWatch(ids: List<Long>) {
        viewModelScope.launch { watchDao.reorder(ids) }
    }

    // ---- 股票漲跌（現價與近 2 年最高價） ----

    val highWatch: StateFlow<List<HighWatchItem>> = highWatchDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private var highJob: Job? = null

    /**
     * 抓近 2 年最高價。最高價一天抓一次就夠（盤中創新高由現價補上）；force = true 時全部重抓。
     */
    fun refreshHighs(force: Boolean = false) {
        if (highJob?.isActive == true) return
        highJob = viewModelScope.launch {
            val today = LocalDate.now()
            val zone = java.time.ZoneId.systemDefault()
            val stale = highWatchDao.getAll().filter {
                force || it.highPrice == null ||
                    java.time.Instant.ofEpochMilli(it.highUpdatedAt).atZone(zone).toLocalDate() != today
            }
            val failed = coroutineScope {
                stale.map { item ->
                    async {
                        val high = marketRepository.periodHigh(item.market, item.symbol, HighWatchItem.PERIOD_YEARS)
                        if (high != null) highWatchDao.updateHigh(item.id, high.price, high.date, System.currentTimeMillis())
                        item.takeIf { high == null }
                    }
                }.awaitAll().filterNotNull()
            }
            if (force && failed.isNotEmpty()) {
                _message.value = "最高價更新失敗：${failed.joinToString("、") { it.symbol }}"
            }
        }
    }

    /** 回傳 false 表示已經在清單中 */
    suspend fun addHighWatch(market: Market, symbol: String, name: String): Boolean {
        if (highWatchDao.count(market, symbol) > 0) return false
        val id = highWatchDao.insert(
            HighWatchItem(
                market = market, symbol = symbol, name = name,
                position = highWatchDao.maxPosition() + 1,
            )
        )
        viewModelScope.launch {
            marketRepository.refreshQuotes(listOf(market to symbol))
            marketRepository.periodHigh(market, symbol, HighWatchItem.PERIOD_YEARS)?.let {
                highWatchDao.updateHigh(id, it.price, it.date, System.currentTimeMillis())
            }
        }
        return true
    }

    fun removeHighWatch(item: HighWatchItem) {
        viewModelScope.launch { highWatchDao.delete(item) }
    }

    fun reorderHighWatch(ids: List<Long>) {
        viewModelScope.launch { highWatchDao.reorder(ids) }
    }

    // ---- 圖表與匯出 ----

    /** 最近 12 個月的已實現損益與股利（依市場） */
    val monthlyIncome: StateFlow<Map<Market, List<MonthIncome>>> = combine(trades, dividends) { t, d ->
        val events = Portfolio.realizedEvents(t, d)
        Market.entries.associateWith { MonthlyIncome.build(it, events, d) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    fun exportFileName(): String {
        val username = account.value?.username ?: "export"
        val day = LocalDate.now().toString().replace("-", "")
        return "股票記帳_${username}_$day.xlsx"
    }

    /** 直接從資料庫讀最新資料產生 Excel，不依賴畫面是否開著 */
    fun exportExcel(uri: android.net.Uri) {
        viewModelScope.launch {
            _message.value = runCatching {
                val trades = tradeDao.getAll()
                val dividends = dividendDao.getAll()
                val quotes = db.marketDao().getQuotes().associateBy { it.market to it.symbol }
                val names = marketRepository.observeInfo().first().associate { (it.market to it.symbol) to it.name }
                val portfolio = Valuation.build(Portfolio.build(trades, dividends), quotes, names, settings.value)
                val categories = categoryDao.getAll().associate { (it.market to it.symbol) to it.category }
                val pledges = PledgeCalculator.build(
                    pledgeDao.getAll(), quotes, portfolio, categories, pledgeIncludeLeveraged.value,
                )
                val sheets = ExcelExporter.buildSheets(trades, dividends, portfolio, categories, pledges)
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    val out = app.contentResolver.openOutputStream(uri, "wt") ?: error("無法寫入檔案")
                    out.use { XlsxWriter.write(sheets, it) }
                }
                "已匯出 Excel（${sheets.size} 個工作表）"
            }.getOrElse { "匯出失敗：${it.message}" }
        }
    }

    // ---- 交易與股利 ----

    suspend fun lookupStock(market: Market, symbol: String): LookupResult =
        marketRepository.lookup(market, symbol)

    suspend fun getTrade(id: Long): Trade? = tradeDao.get(id)

    fun saveTrade(trade: Trade) {
        viewModelScope.launch { tradeDao.upsert(trade) }
    }

    fun deleteTrade(trade: Trade) {
        viewModelScope.launch { tradeDao.delete(trade) }
    }

    suspend fun getDividend(id: Long): Dividend? = dividendDao.get(id)

    fun saveDividend(dividend: Dividend) {
        viewModelScope.launch { dividendDao.upsert(dividend) }
    }

    fun deleteDividend(dividend: Dividend) {
        viewModelScope.launch { dividendDao.delete(dividend) }
    }

    // ---- 設定 ----

    fun updateSettings(settings: FeeSettings) = settingsRepository.update(settings)

    fun setRefreshSeconds(seconds: Int) = settingsRepository.setRefreshSeconds(seconds)

    fun setAuthMethod(method: AuthMethod) {
        account.value?.let { app.updateSessionAccount(it.copy(method = method)) }
    }

    fun logout() = app.logout()

    // ---- Google 雲端同步 ----

    private val _syncing = MutableStateFlow(false)
    val syncing: StateFlow<Boolean> = _syncing.asStateFlow()

    fun onGoogleLinked() {
        settingsRepository.updateSync { it.copy(googleLinked = true, lastResult = "已連結 Google 帳號") }
        val s = sync.value
        if (s.auto) app.syncManager.schedule(userId, s.intervalHours, replace = true)
    }

    fun onGoogleLinkFailed(reason: String) {
        // 連結失敗時自動同步無法運作，開關關回去
        settingsRepository.updateSync { it.copy(auto = false, lastResult = "連結失敗：$reason") }
    }

    fun unlinkGoogle() {
        app.syncManager.cancel(userId)
        settingsRepository.updateSync { it.copy(googleLinked = false, auto = false, lastResult = null) }
    }

    fun setAutoSync(auto: Boolean, hours: Int) {
        settingsRepository.updateSync { it.copy(auto = auto, intervalHours = hours) }
        if (auto && sync.value.googleLinked) app.syncManager.schedule(userId, hours, replace = true)
        else app.syncManager.cancel(userId)
    }

    fun backupNow() = runSync { app.syncManager.backup(userId).map { "已備份到 Google 雲端" } }

    fun restoreNow() = runSync { app.syncManager.restore(userId).map { "已從雲端還原 $it 筆紀錄" } }

    fun backupFileName(): String = app.syncManager.suggestedFileName(userId)

    fun backupToFile(uri: android.net.Uri) =
        runSync { app.syncManager.backupToFile(userId, uri).map { "備份完成" } }

    fun restoreFromFile(uri: android.net.Uri) =
        runSync { app.syncManager.restoreFromFile(userId, uri).map { "已還原 $it 筆紀錄" } }

    private fun runSync(block: suspend () -> Result<String>) {
        if (_syncing.value) return
        viewModelScope.launch {
            _syncing.value = true
            try {
                _message.value = block().getOrElse { "同步失敗：${it.message}" }
            } finally {
                _syncing.value = false
            }
        }
    }
}
