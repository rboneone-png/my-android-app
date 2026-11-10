package com.rboneone.stockrecord.export

import com.rboneone.stockrecord.data.Dividend
import com.rboneone.stockrecord.data.Market
import com.rboneone.stockrecord.data.TaxType
import com.rboneone.stockrecord.data.Trade
import com.rboneone.stockrecord.data.TradeType
import com.rboneone.stockrecord.domain.FeeCalculator
import com.rboneone.stockrecord.domain.FeeSettings
import com.rboneone.stockrecord.domain.Portfolio
import com.rboneone.stockrecord.domain.Valuation
import java.time.LocalDate

data class ImportResult(
    val trades: List<Trade>,
    val dividends: List<Dividend>,
    /** 讀不懂的列：「工作表 第 N 列：原因」 */
    val errors: List<String>,
    /** 有讀到的資料工作表（交易紀錄、股利） */
    val sheetsFound: List<String>,
)

enum class ImportMode { ADD, REPLACE }

/** 套用到資料庫之前先算好的結果 */
data class ImportPlan(
    val newTrades: List<Trade>,
    val newDividends: List<Dividend>,
    val duplicatesSkipped: Int,
    /** 匯入後某檔股票賣出超過持有股數；非 null 時不可匯入 */
    val shortage: String?,
)

/**
 * 從 Excel 匯入買賣與股利。讀「交易紀錄」「股利」兩個工作表（格式同匯出），依標題列的名稱對應欄位，
 * 所以欄位順序可以不同、選填欄位可以省略。手續費、交易稅、扣稅留空時依目前的費率設定自動計算。
 */
object ExcelImporter {
    const val TRADE_SHEET = "交易紀錄"
    const val DIVIDEND_SHEET = "股利"

    fun parse(sheets: Map<String, List<List<String>>>, settings: FeeSettings): ImportResult {
        val errors = mutableListOf<String>()
        val found = mutableListOf<String>()
        val trades = sheets[TRADE_SHEET]?.let { found += TRADE_SHEET; parseTrades(it, settings, errors) }.orEmpty()
        val dividends = sheets[DIVIDEND_SHEET]?.let { found += DIVIDEND_SHEET; parseDividends(it, settings, errors) }.orEmpty()
        return ImportResult(trades, dividends, errors, found)
    }

    // ---- 欄位對應 ----

    private class Columns(header: List<String>) {
        private val index = header.mapIndexed { i, h -> h.trim().replace("　", "") to i }.toMap()
        fun find(vararg names: String): Int? = names.firstNotNullOfOrNull { index[it] }
        fun missing(vararg names: String) = names.filter { index[it] == null }
    }

    private fun List<String>.cell(col: Int?): String = col?.let { getOrNull(it) }?.trim().orEmpty()

    // ---- 數字、日期、代號 ----

    fun parseNumber(text: String): Double? =
        text.trim().replace(",", "").replace("，", "").takeIf { it.isNotEmpty() }?.toDoubleOrNull()?.takeIf { !it.isNaN() && !it.isInfinite() }

    /** yyyy-MM-dd、yyyy/M/d、yyyy.M.d、yyyyMMdd，或 Excel 的日期序號 */
    fun parseDate(text: String): LocalDate? {
        val s = text.trim()
        if (s.isEmpty()) return null
        Regex("""^(\d{4})[-/.](\d{1,2})[-/.](\d{1,2})""").find(s)?.let {
            return runCatching { LocalDate.of(it.groupValues[1].toInt(), it.groupValues[2].toInt(), it.groupValues[3].toInt()) }.getOrNull()
        }
        Regex("""^(\d{4})(\d{2})(\d{2})$""").find(s)?.let {
            return runCatching { LocalDate.of(it.groupValues[1].toInt(), it.groupValues[2].toInt(), it.groupValues[3].toInt()) }.getOrNull()
        }
        // Excel 序號（1900 日期系統）：25569 = 1970-01-01
        s.toDoubleOrNull()?.takeIf { it in 20_000.0..80_000.0 }?.let { return LocalDate.of(1899, 12, 30).plusDays(it.toLong()) }
        return null
    }

    fun parseMarket(text: String): Market? = when (text.trim().uppercase()) {
        "台股", "TW", "TWSE", "TPEX" -> Market.TW
        "美股", "US", "USA" -> Market.US
        else -> null
    }

    fun parseType(text: String): TradeType? = when (text.trim().uppercase()) {
        "買進", "買", "BUY", "B" -> TradeType.BUY
        "賣出", "賣", "SELL", "S" -> TradeType.SELL
        else -> null
    }

    private fun parseTaxType(text: String): TaxType? = when {
        text.isBlank() -> null
        text.contains("ETF", ignoreCase = true) -> TaxType.ETF
        text.contains("當沖") -> TaxType.DAY_TRADE
        text.contains("一般") -> TaxType.STOCK
        else -> null
    }

    /** 代號：去空白、轉大寫；Excel 把「0050」存成數字 50 時，台股補回 4 位數 */
    fun normalizeSymbol(raw: String, market: Market): String {
        val s = raw.trim().uppercase()
        return if (market == Market.TW && s.length in 1..3 && s.all { it.isDigit() }) s.padStart(4, '0') else s
    }

    // ---- 交易紀錄 ----

    private fun parseTrades(rows: List<List<String>>, settings: FeeSettings, errors: MutableList<String>): List<Trade> {
        if (rows.isEmpty()) return emptyList()
        val cols = Columns(rows.first())
        val missing = cols.missing("日期", "市場", "代號", "買賣", "股數", "成交價")
        if (missing.isNotEmpty()) {
            errors += "$TRADE_SHEET 缺少必要欄位：${missing.joinToString("、")}"
            return emptyList()
        }
        val cDate = cols.find("日期"); val cMarket = cols.find("市場"); val cSymbol = cols.find("代號")
        val cName = cols.find("名稱"); val cType = cols.find("買賣"); val cShares = cols.find("股數")
        val cPrice = cols.find("成交價"); val cFee = cols.find("手續費"); val cTax = cols.find("交易稅")
        val cTaxType = cols.find("稅別"); val cNote = cols.find("備註")

        val out = mutableListOf<Trade>()
        rows.drop(1).forEachIndexed { i, row ->
            val line = i + 2
            if (row.all { it.isBlank() }) return@forEachIndexed
            fun err(msg: String) { errors += "$TRADE_SHEET 第 $line 列：$msg" }
            val date = parseDate(row.cell(cDate)) ?: run { err("日期看不懂（請用 2026-11-10 這種格式）"); return@forEachIndexed }
            val market = parseMarket(row.cell(cMarket)) ?: run { err("市場請填「台股」或「美股」"); return@forEachIndexed }
            val symbol = normalizeSymbol(row.cell(cSymbol), market).ifEmpty { err("沒有代號"); return@forEachIndexed; "" }
            val type = parseType(row.cell(cType)) ?: run { err("買賣請填「買進」或「賣出」"); return@forEachIndexed }
            val shares = parseNumber(row.cell(cShares))?.takeIf { it > 0 } ?: run { err("股數要是大於 0 的數字"); return@forEachIndexed }
            val price = parseNumber(row.cell(cPrice))?.takeIf { it > 0 } ?: run { err("成交價要是大於 0 的數字"); return@forEachIndexed }
            val taxType = parseTaxType(row.cell(cTaxType)) ?: Valuation.defaultTaxType(market, symbol)
            val auto = FeeCalculator.calculate(market, type, shares, price, taxType, settings)
            val feeText = row.cell(cFee); val taxText = row.cell(cTax)
            val fee = if (feeText.isEmpty()) auto.fee else parseNumber(feeText)?.takeIf { it >= 0 } ?: run { err("手續費不是數字"); return@forEachIndexed }
            val tax = if (taxText.isEmpty()) auto.tax else parseNumber(taxText)?.takeIf { it >= 0 } ?: run { err("交易稅不是數字"); return@forEachIndexed }
            out += Trade(
                market = market, symbol = symbol, name = row.cell(cName).ifEmpty { symbol }, type = type,
                date = date.toEpochDay(), shares = shares, price = price, fee = fee, tax = tax,
                taxType = taxType, note = row.cell(cNote),
            )
        }
        return out
    }

    // ---- 股利 ----

    private fun parseDividends(rows: List<List<String>>, settings: FeeSettings, errors: MutableList<String>): List<Dividend> {
        if (rows.isEmpty()) return emptyList()
        val cols = Columns(rows.first())
        val cDate = cols.find("發放日", "日期")
        val cMarket = cols.find("市場"); val cSymbol = cols.find("代號")
        val missing = buildList {
            if (cDate == null) add("發放日"); if (cMarket == null) add("市場"); if (cSymbol == null) add("代號")
        }
        if (missing.isNotEmpty()) {
            errors += "$DIVIDEND_SHEET 缺少必要欄位：${missing.joinToString("、")}"
            return emptyList()
        }
        val cName = cols.find("名稱"); val cCash = cols.find("現金股利"); val cTax = cols.find("扣稅／健保", "扣稅")
        val cFee = cols.find("匯費"); val cStock = cols.find("配股股數"); val cNote = cols.find("備註")

        val out = mutableListOf<Dividend>()
        rows.drop(1).forEachIndexed { i, row ->
            val line = i + 2
            if (row.all { it.isBlank() }) return@forEachIndexed
            fun err(msg: String) { errors += "$DIVIDEND_SHEET 第 $line 列：$msg" }
            val date = parseDate(row.cell(cDate)) ?: run { err("發放日看不懂（請用 2026-11-10 這種格式）"); return@forEachIndexed }
            val market = parseMarket(row.cell(cMarket)) ?: run { err("市場請填「台股」或「美股」"); return@forEachIndexed }
            val symbol = normalizeSymbol(row.cell(cSymbol), market).ifEmpty { err("沒有代號"); return@forEachIndexed; "" }
            val cashText = row.cell(cCash); val stockText = row.cell(cStock)
            val cash = if (cashText.isEmpty()) 0.0 else parseNumber(cashText)?.takeIf { it >= 0 } ?: run { err("現金股利不是數字"); return@forEachIndexed }
            val stock = if (stockText.isEmpty()) 0.0 else parseNumber(stockText)?.takeIf { it >= 0 } ?: run { err("配股股數不是數字"); return@forEachIndexed }
            if (cash <= 0 && stock <= 0) { err("現金股利與配股股數都是 0"); return@forEachIndexed }
            val taxText = row.cell(cTax); val feeText = row.cell(cFee)
            val tax = if (taxText.isEmpty()) FeeCalculator.dividendTax(market, cash, settings)
            else parseNumber(taxText)?.takeIf { it >= 0 } ?: run { err("扣稅不是數字"); return@forEachIndexed }
            val fee = if (feeText.isEmpty()) 0.0 else parseNumber(feeText)?.takeIf { it >= 0 } ?: run { err("匯費不是數字"); return@forEachIndexed }
            out += Dividend(
                market = market, symbol = symbol, name = row.cell(cName).ifEmpty { symbol },
                date = date.toEpochDay(), cashAmount = cash, tax = tax, fee = fee, stockShares = stock, note = row.cell(cNote),
            )
        }
        return out
    }

    // ---- 合併計畫 ----

    private fun tradeKey(t: Trade) = listOf(t.date, t.market, t.symbol, t.type, Math.round(t.shares * 1e6), Math.round(t.price * 1e6))
    private fun dividendKey(d: Dividend) =
        listOf(d.date, d.market, d.symbol, Math.round(d.cashAmount * 100), Math.round(d.tax * 100), Math.round(d.fee * 100), Math.round(d.stockShares * 1e6))

    /** 去掉和既有紀錄重複的（既有一筆最多抵掉檔案裡的一筆）；回傳留下的與略過的筆數 */
    private fun <T> dropDuplicates(existing: List<T>, incoming: List<T>, key: (T) -> Any): Pair<List<T>, Int> {
        val counts = HashMap<Any, Int>()
        existing.forEach { counts.merge(key(it), 1, Int::plus) }
        val kept = mutableListOf<T>()
        var skipped = 0
        for (item in incoming) {
            val k = key(item)
            val left = counts[k] ?: 0
            if (left > 0) { counts[k] = left - 1; skipped++ } else kept += item
        }
        return kept to skipped
    }

    /**
     * ADD：加到現有資料，和現有紀錄完全相同的略過（同一個檔案匯入兩次不會重複）；
     * REPLACE：整批取代現有的買賣與股利。匯入後若有賣出超過持有股數，shortage 會有說明，不可匯入。
     */
    fun plan(
        existingTrades: List<Trade>,
        existingDividends: List<Dividend>,
        parsed: ImportResult,
        mode: ImportMode,
    ): ImportPlan {
        val (newTrades, skipT, newDividends, skipD) = when (mode) {
            ImportMode.REPLACE -> listOf(parsed.trades, 0, parsed.dividends, 0)
            ImportMode.ADD -> {
                val (t, st) = dropDuplicates(existingTrades, parsed.trades, ::tradeKey)
                val (d, sd) = dropDuplicates(existingDividends, parsed.dividends, ::dividendKey)
                listOf(t, st, d, sd)
            }
        }.let {
            @Suppress("UNCHECKED_CAST")
            Quad(it[0] as List<Trade>, it[1] as Int, it[2] as List<Dividend>, it[3] as Int)
        }
        val allTrades = (if (mode == ImportMode.REPLACE) emptyList() else existingTrades) + newTrades
        val allDividends = (if (mode == ImportMode.REPLACE) emptyList() else existingDividends) + newDividends
        val shortage = allTrades.map { it.market to it.symbol }.distinct().firstNotNullOfOrNull { (m, s) ->
            Portfolio.firstShortage(allTrades, allDividends, m, s)?.let {
                "匯入後 $s 在 ${LocalDate.ofEpochDay(it.date)} 賣出 %,.0f 股，但當時只有 %,.0f 股（請確認買進紀錄都在檔案裡）".format(it.sold, it.available)
            }
        }
        return ImportPlan(newTrades, newDividends, skipT + skipD, shortage)
    }

    private data class Quad(val a: List<Trade>, val b: Int, val c: List<Dividend>, val d: Int)
    private operator fun Quad.component1() = a
    private operator fun Quad.component2() = b
    private operator fun Quad.component3() = c
    private operator fun Quad.component4() = d
}
