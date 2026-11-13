package com.rboneone.stockrecord.export

import com.rboneone.stockrecord.data.AssetCategory
import com.rboneone.stockrecord.data.Dividend
import com.rboneone.stockrecord.data.Market
import com.rboneone.stockrecord.data.TaxType
import com.rboneone.stockrecord.data.Trade
import com.rboneone.stockrecord.data.TradeType
import com.rboneone.stockrecord.domain.FeeSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ExcelImportTest {
    private val settings = FeeSettings(twDiscount = 0.28, twMinFee = 20.0, usCommission = 0.0)
    private fun d(y: Int, m: Int, day: Int) = LocalDate.of(y, m, day).toEpochDay()

    private fun exportBytes(trades: List<Trade>, dividends: List<Dividend>): ByteArray {
        val sheets = ExcelExporter.buildSheets(trades, dividends, null, emptyMap<Pair<Market, String>, AssetCategory>(), null)
        return ByteArrayOutputStream().also { XlsxWriter.write(sheets, it) }.toByteArray()
    }

    private fun import(bytes: ByteArray) = ExcelImporter.parse(XlsxReader.read(ByteArrayInputStream(bytes)), settings)

    // ---- 匯出再匯入 ----

    @Test
    fun roundTrip_exportThenImport_restoresTradesAndDividends() {
        val trades = listOf(
            Trade(1, Market.TW, "0050", "元大台灣50", TradeType.BUY, d(2026, 1, 5), 2000.0, 150.55, 85.0, 0.0, TaxType.ETF, "第一筆"),
            Trade(2, Market.TW, "0050", "元大台灣50", TradeType.SELL, d(2026, 3, 2), 1000.0, 160.0, 45.0, 160.0, TaxType.ETF),
            Trade(3, Market.TW, "2330", "台積電", TradeType.SELL, d(2026, 4, 1), 100.0, 900.0, 25.0, 270.0, TaxType.DAY_TRADE),
            Trade(4, Market.US, "AAPL", "Apple", TradeType.BUY, d(2026, 2, 3), 1.5, 190.123, 0.0, 0.0, TaxType.STOCK, "零股 & <特殊>字元"),
        )
        val dividends = listOf(
            Dividend(1, Market.TW, "0050", "元大台灣50", d(2026, 2, 20), 6_000.0, 0.0, 10.0, 0.0, "現金"),
            Dividend(2, Market.TW, "0050", "元大台灣50", d(2026, 3, 1), 0.0, 0.0, 0.0, 100.0, "配股"),
        )
        val result = import(exportBytes(trades, dividends))
        assertEquals(emptyList<String>(), result.errors)
        assertEquals(listOf("交易紀錄", "股利"), result.sheetsFound)
        // 匯出依日期排序，id 不保留
        val expected = trades.sortedBy { it.date }.map { it.copy(id = 0) }
        assertEquals(expected, result.trades)
        assertEquals(dividends.sortedBy { it.date }.map { it.copy(id = 0) }, result.dividends)
    }

    @Test
    fun roundTrip_keepsUsFxRate_andMissingStaysNull() {
        val trades = listOf(
            Trade(1, Market.US, "AAPL", "Apple", TradeType.BUY, d(2026, 2, 3), 2.0, 190.0, 0.0, 0.0, fxRate = 31.4567),
            Trade(2, Market.US, "AAPL", "Apple", TradeType.BUY, d(2026, 2, 4), 1.0, 191.0, 0.0, 0.0),   // 還沒補匯率
            Trade(3, Market.TW, "0050", "元大台灣50", TradeType.BUY, d(2026, 2, 5), 1000.0, 150.0, 20.0, 0.0, TaxType.ETF),
        )
        val dividends = listOf(
            Dividend(1, Market.US, "AAPL", "Apple", d(2026, 2, 20), 10.0, 3.0, 0.0, 0.0, "", fxRate = 32.0),
        )
        val result = import(exportBytes(trades, dividends))
        assertEquals(emptyList<String>(), result.errors)
        assertEquals(31.4567, result.trades[0].fxRate!!, 1e-9)
        assertNull(result.trades[1].fxRate)
        assertNull(result.trades[2].fxRate)   // 台股沒有匯率
        assertEquals(32.0, result.dividends[0].fxRate!!, 1e-9)
    }

    @Test
    fun export_hasFxColumn_forUsOnly() {
        val sheets = ExcelExporter.buildSheets(
            listOf(
                Trade(1, Market.TW, "0050", "x", TradeType.BUY, 1, 1000.0, 100.0, 1.0, 0.0),
                Trade(2, Market.US, "AAPL", "y", TradeType.BUY, 2, 1.0, 100.0, 0.0, 0.0, fxRate = 31.5),
            ),
            emptyList(), null, emptyMap<Pair<Market, String>, AssetCategory>(), null,
        )
        val sheet = sheets.first { it.name == "交易紀錄" }
        val col = sheet.header.indexOf("匯率")
        assertTrue(col >= 0)
        assertTrue(sheet.rows[0][col] is Cell.Empty)
        assertEquals(31.5, (sheet.rows[1][col] as Cell.Number).value, 1e-9)
    }

    @Test
    fun export_hasTaxTypeColumn_forTwOnly() {
        val sheets = ExcelExporter.buildSheets(
            listOf(
                Trade(1, Market.TW, "0050", "x", TradeType.SELL, 1, 1000.0, 100.0, 1.0, 1.0, TaxType.ETF),
                Trade(2, Market.US, "AAPL", "y", TradeType.BUY, 2, 1.0, 100.0, 0.0, 0.0),
            ),
            emptyList(), null, emptyMap(), null,
        )
        val s = sheets.first { it.name == "交易紀錄" }
        val col = s.header.indexOf("稅別")
        assertTrue(col >= 0)
        assertEquals("ETF", (s.rows[0][col] as Cell.Text).value)
        assertEquals("", (s.rows[1][col] as Cell.Text).value)
    }

    // ---- 手寫的 Excel（共用字串、日期序號、數字代號）----

    /** 模擬 Excel 另存的檔：字串放共用字串表，日期是序號，代號 50 是數字 */
    private fun excelLikeBytes(): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            fun put(path: String, content: String) { zip.putNextEntry(ZipEntry(path)); zip.write(content.toByteArray()); zip.closeEntry() }
            put("xl/workbook.xml", """<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="交易紀錄" sheetId="1" r:id="rId1"/></sheets></workbook>""")
            put("xl/_rels/workbook.xml.rels", """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="x" Target="worksheets/sheet1.xml"/></Relationships>""")
            // 共用字串：0 日期 1 市場 2 代號 3 買賣 4 股數 5 成交價 6 台股 7 買進 8 賣出 9 美股
            put("xl/sharedStrings.xml", """<sst xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><si><t>日期</t></si><si><t>市場</t></si><si><t>代號</t></si><si><t>買賣</t></si><si><t>股數</t></si><si><t>成交價</t></si><si><t>台股</t></si><si><t>買進</t></si><si><t>賣出</t></si><si><r><t>美</t></r><r><t>股</t></r></si></sst>""")
            // 46000 = 2025-12-09（Excel 序號）；第 2 列代號是數字 50（原本是 0050）
            put("xl/worksheets/sheet1.xml", """<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>
                <row r="1"><c r="A1" t="s"><v>0</v></c><c r="B1" t="s"><v>1</v></c><c r="C1" t="s"><v>2</v></c><c r="D1" t="s"><v>3</v></c><c r="E1" t="s"><v>4</v></c><c r="F1" t="s"><v>5</v></c></row>
                <row r="2"><c r="A2"><v>46000</v></c><c r="B2" t="s"><v>6</v></c><c r="C2"><v>50</v></c><c r="D2" t="s"><v>7</v></c><c r="E2"><v>1000</v></c><c r="F2"><v>150.5</v></c></row>
                <row r="3"><c r="A3" t="inlineStr"><is><t>2026/1/5</t></is></c><c r="B3" t="s"><v>9</v></c><c r="C3" t="inlineStr"><is><t>aapl</t></is></c><c r="D3" t="s"><v>7</v></c><c r="E3"><v>2.5</v></c><c r="F3"><v>190</v></c></row>
                <row r="4"><c r="A4" t="inlineStr"><is><t>2026-02-01</t></is></c><c r="B4" t="s"><v>6</v></c><c r="C4" t="inlineStr"><is><t>0050</t></is></c><c r="D4" t="s"><v>8</v></c><c r="E4"><v>500</v></c><c r="F4"><v>160</v></c></row>
                </sheetData></worksheet>""")
        }
        return out.toByteArray()
    }

    @Test
    fun excelLikeFile_sharedStrings_serialDates_numericSymbolAndAutoFees() {
        val r = import(excelLikeBytes())
        assertEquals(emptyList<String>(), r.errors)
        assertEquals(3, r.trades.size)
        val buy = r.trades[0]
        assertEquals(d(2025, 12, 9), buy.date)             // 序號 46000
        assertEquals("0050", buy.symbol)                    // 數字 50 補回 0050
        assertEquals(Market.TW, buy.market)
        assertEquals(TradeType.BUY, buy.type)
        assertEquals(1000.0, buy.shares, 0.0)
        assertEquals(150.5, buy.price, 0.0)
        // 手續費留空 → 依費率自動：150,500 × 0.1425% × 0.28 = 60.05 → 60（未達最低 20 以上）
        assertEquals(60.0, buy.fee, 0.0)
        assertEquals(0.0, buy.tax, 0.0)
        val us = r.trades[1]
        assertEquals(Market.US, us.market)                  // 共用字串的 rich text「美」+「股」
        assertEquals("AAPL", us.symbol)                     // 轉大寫
        assertEquals(d(2026, 1, 5), us.date)                // 2026/1/5
        assertEquals(2.5, us.shares, 0.0)
        // 賣出 0050（ETF 代號）→ 預設 ETF 稅率 0.1%：80,000 × 0.1% = 80
        val sell = r.trades[2]
        assertEquals(TaxType.ETF, sell.taxType)
        assertEquals(80.0, sell.tax, 0.0)
        assertEquals("0050", sell.name)                     // 沒有名稱時用代號
    }

    @Test
    fun columnsMatchedByHeaderName_notPosition() {
        val sheets = mapOf(
            "交易紀錄" to listOf(
                listOf("成交價", "股數", "買賣", "代號", "市場", "日期", "手續費", "交易稅"),
                listOf("100", "1,000", "買進", "2330", "台股", "2026-01-02", "0", "0"),
            ),
        )
        val r = ExcelImporter.parse(sheets, settings)
        assertEquals(emptyList<String>(), r.errors)
        val t = r.trades.single()
        assertEquals(1000.0, t.shares, 0.0)                 // 千分位逗號
        assertEquals(0.0, t.fee, 0.0)                       // 明確填 0 就是 0，不自動算
    }

    // ---- 錯誤處理 ----

    @Test
    fun badRows_areReportedWithRowNumbers_andGoodRowsKept() {
        val header = listOf("日期", "市場", "代號", "買賣", "股數", "成交價")
        val sheets = mapOf(
            "交易紀錄" to listOf(
                header,
                listOf("2026-01-02", "台股", "2330", "買進", "1000", "100"),   // 好
                listOf("不是日期", "台股", "2330", "買進", "1000", "100"),     // 第 3 列
                listOf("2026-01-02", "日股", "2330", "買進", "1000", "100"),   // 第 4 列
                listOf("2026-01-02", "台股", "", "買進", "1000", "100"),       // 第 5 列
                listOf("2026-01-02", "台股", "2330", "持有", "1000", "100"),   // 第 6 列
                listOf("2026-01-02", "台股", "2330", "買進", "-5", "100"),     // 第 7 列
                listOf("2026-01-02", "台股", "2330", "買進", "1000", "abc"),   // 第 8 列
                listOf("", "", "", "", "", ""),                                 // 空列：略過不算錯
            ),
        )
        val r = ExcelImporter.parse(sheets, settings)
        assertEquals(1, r.trades.size)
        assertEquals(6, r.errors.size)
        assertTrue(r.errors[0].startsWith("交易紀錄 第 3 列：日期"))
        assertTrue(r.errors[1].startsWith("交易紀錄 第 4 列：市場"))
        assertTrue(r.errors[2].startsWith("交易紀錄 第 5 列：沒有代號"))
        assertTrue(r.errors[3].startsWith("交易紀錄 第 6 列：買賣"))
        assertTrue(r.errors[4].startsWith("交易紀錄 第 7 列：股數"))
        assertTrue(r.errors[5].startsWith("交易紀錄 第 8 列：成交價"))
    }

    @Test
    fun missingRequiredColumns_isReported() {
        val r = ExcelImporter.parse(mapOf("交易紀錄" to listOf(listOf("日期", "代號"))), settings)
        assertEquals(0, r.trades.size)
        assertTrue(r.errors.single().contains("缺少必要欄位"))
    }

    @Test
    fun dividends_cashOrStockRequired_taxAutoWhenBlank() {
        val sheets = mapOf(
            "股利" to listOf(
                listOf("發放日", "市場", "代號", "現金股利"),
                listOf("2026-02-20", "台股", "0050", "30000"),    // 達 2 萬 → 二代健保 2.11% = 633
                listOf("2026-02-21", "台股", "0050", ""),         // 現金與配股都沒有
            ),
        )
        val r = ExcelImporter.parse(sheets, settings)
        assertEquals(1, r.dividends.size)
        assertEquals(633.0, r.dividends.single().tax, 0.0)
        assertTrue(r.errors.single().contains("第 3 列"))
    }

    @Test
    fun notAnXlsx_throws() {
        val failed = runCatching { XlsxReader.read(ByteArrayInputStream("hello".toByteArray())) }.isFailure
        assertTrue(failed)
    }

    // ---- 合併計畫 ----

    private fun t(id: Long, type: TradeType, date: Long, shares: Double, price: Double = 100.0) =
        Trade(id, Market.TW, "2330", "台積電", type, date, shares, price, 0.0, 0.0)

    private fun result(trades: List<Trade>, dividends: List<Dividend> = emptyList()) =
        ImportResult(trades, dividends, emptyList(), listOf("交易紀錄"))

    @Test
    fun add_skipsRecordsIdenticalToExisting_asAMultiset() {
        val existing = listOf(t(1, TradeType.BUY, 10, 1000.0))
        // 檔案裡有 2 筆一模一樣的：現有只抵掉 1 筆，另 1 筆是新的
        val plan = ExcelImporter.plan(existing, emptyList(), result(listOf(t(0, TradeType.BUY, 10, 1000.0), t(0, TradeType.BUY, 10, 1000.0))), ImportMode.ADD)
        assertEquals(1, plan.newTrades.size)
        assertEquals(1, plan.duplicatesSkipped)
        assertNull(plan.shortage)
    }

    @Test
    fun add_sameFileTwice_addsNothingTheSecondTime() {
        val file = result(listOf(t(0, TradeType.BUY, 10, 1000.0), t(0, TradeType.SELL, 20, 400.0)))
        val first = ExcelImporter.plan(emptyList(), emptyList(), file, ImportMode.ADD)
        assertEquals(2, first.newTrades.size)
        val second = ExcelImporter.plan(first.newTrades, emptyList(), file, ImportMode.ADD)
        assertEquals(0, second.newTrades.size)
        assertEquals(2, second.duplicatesSkipped)
    }

    @Test
    fun replace_ignoresExisting_andKeepsAllParsed() {
        val existing = listOf(t(1, TradeType.BUY, 10, 5000.0))
        val plan = ExcelImporter.plan(existing, emptyList(), result(listOf(t(0, TradeType.BUY, 10, 1000.0))), ImportMode.REPLACE)
        assertEquals(1, plan.newTrades.size)
        assertEquals(0, plan.duplicatesSkipped)
        assertNull(plan.shortage)
    }

    @Test
    fun shortage_blocksImport_whenSellExceedsHoldings() {
        val plan = ExcelImporter.plan(emptyList(), emptyList(), result(listOf(t(0, TradeType.BUY, 10, 1000.0), t(0, TradeType.SELL, 20, 2000.0))), ImportMode.ADD)
        assertNotNull(plan.shortage)
        assertTrue(plan.shortage!!.contains("2330"))
        // 現有資料有足夠的買進時，單獨匯入賣出是可以的
        val ok = ExcelImporter.plan(listOf(t(1, TradeType.BUY, 10, 5000.0)), emptyList(), result(listOf(t(0, TradeType.SELL, 20, 2000.0))), ImportMode.ADD)
        assertNull(ok.shortage)
    }

    @Test
    fun parseHelpers() {
        assertEquals(d(2026, 11, 10), ExcelImporter.parseDate("2026-11-10")?.toEpochDay())
        assertEquals(d(2026, 11, 10), ExcelImporter.parseDate("2026/11/10")?.toEpochDay())
        assertEquals(d(2026, 11, 10), ExcelImporter.parseDate("20261110")?.toEpochDay())
        assertNull(ExcelImporter.parseDate("2026-13-40"))
        assertNull(ExcelImporter.parseDate("abc"))
        assertEquals(1234.5, ExcelImporter.parseNumber("1,234.5")!!, 0.0)
        assertNull(ExcelImporter.parseNumber("NaN"))
        assertEquals(Market.US, ExcelImporter.parseMarket("us"))
        assertEquals(TradeType.SELL, ExcelImporter.parseType("賣"))
        assertEquals("0050", ExcelImporter.normalizeSymbol("50", Market.TW))
        assertEquals("AAPL", ExcelImporter.normalizeSymbol(" aapl ", Market.US))
        assertEquals("123", ExcelImporter.normalizeSymbol("123", Market.US))   // 美股不補零
    }
}
