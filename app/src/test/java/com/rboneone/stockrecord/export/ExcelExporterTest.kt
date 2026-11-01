package com.rboneone.stockrecord.export

import com.rboneone.stockrecord.data.AssetCategory
import com.rboneone.stockrecord.data.Dividend
import com.rboneone.stockrecord.data.Market
import com.rboneone.stockrecord.data.Pledge
import com.rboneone.stockrecord.data.Quote
import com.rboneone.stockrecord.data.Trade
import com.rboneone.stockrecord.data.TradeType
import com.rboneone.stockrecord.domain.FeeSettings
import com.rboneone.stockrecord.domain.MonthlyIncome
import com.rboneone.stockrecord.domain.PledgeCalculator
import com.rboneone.stockrecord.domain.Portfolio
import com.rboneone.stockrecord.domain.Valuation
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.time.LocalDate
import java.time.YearMonth
import java.util.zip.ZipFile

class ExcelExporterTest {
    private val d = { y: Int, m: Int, day: Int -> LocalDate.of(y, m, day).toEpochDay() }
    private val trades = listOf(
        Trade(1, Market.TW, "2330", "台積電", TradeType.BUY, d(2026, 1, 5), 1000.0, 1000.0, 399.0, 0.0),
        Trade(2, Market.TW, "2330", "台積電", TradeType.SELL, d(2026, 8, 3), 400.0, 1500.0, 239.0, 1800.0, note = "部分 & <賣出>"),
        Trade(3, Market.US, "AAPL", "Apple Inc.", TradeType.BUY, d(2026, 2, 2), 10.0, 230.5, 0.0, 0.0),
        Trade(4, Market.US, "AAPL", "Apple Inc.", TradeType.SELL, d(2026, 9, 9), 5.0, 300.0, 1.0, 0.0),
    )
    private val dividends = listOf(
        Dividend(1, Market.TW, "2330", "台積電", d(2026, 7, 10), 5_000.0, 0.0, 10.0, 0.0),
        Dividend(2, Market.US, "AAPL", "Apple Inc.", d(2026, 8, 15), 2.5, 0.75, 0.0, 0.0),
    )

    @Test
    fun realizedPerSale() {
        val events = Portfolio.realizedEvents(trades, dividends)
        assertEquals(2, events.size)
        // 台積電：平均成本 (1,000,000 + 399) / 1000 = 1000.399；賣 400 股
        val tw = events.first { it.market == Market.TW }
        assertEquals(1000.399 * 400, tw.cost, 1e-6)
        assertEquals(600_000.0 - 239 - 1800 - 1000.399 * 400, tw.pnl, 1e-6)
        val us = events.first { it.market == Market.US }
        assertEquals(1500.0 - 1.0 - 230.5 * 5, us.pnl, 1e-9)
    }

    @Test
    fun monthly_last12Months() {
        val events = Portfolio.realizedEvents(trades, dividends)
        val months = MonthlyIncome.build(Market.TW, events, dividends, endMonth = YearMonth.of(2026, 10))
        assertEquals(12, months.size)
        assertEquals(YearMonth.of(2025, 11), months.first().month)
        assertEquals(YearMonth.of(2026, 10), months.last().month)
        assertEquals(4_990.0, months.first { it.month == YearMonth.of(2026, 7) }.dividends, 1e-9)
        assertEquals(events.first { it.market == Market.TW }.pnl, months.first { it.month == YearMonth.of(2026, 8) }.realized, 1e-9)
        assertEquals(0.0, months.first { it.month == YearMonth.of(2026, 10) }.total, 0.0)
    }

    @Test
    fun columnNames() {
        assertEquals("A", XlsxWriter.columnName(0))
        assertEquals("Z", XlsxWriter.columnName(25))
        assertEquals("AA", XlsxWriter.columnName(26))
        assertEquals("AZ", XlsxWriter.columnName(51))
    }

    @Test
    fun writesValidWorkbook() {
        val holdings = Portfolio.build(trades, dividends)
        val quotes = listOf(
            Quote(Market.TW, "2330", 2365.0, 2415.0, 0, 0),
            Quote(Market.US, "AAPL", 348.91, 343.83, 0, 0),
        ).associateBy { it.market to it.symbol }
        val portfolio = Valuation.build(holdings, quotes, emptyMap(), FeeSettings())
        val pledges = PledgeCalculator.build(
            listOf(Pledge(1, "2330", "台積電", 500.0, 600_000.0, 2.5, d(2026, 9, 1))), quotes, portfolio,
        )
        val sheets = ExcelExporter.buildSheets(
            trades, dividends, portfolio, mapOf((Market.TW to "2330") to AssetCategory.OTHER), pledges,
        )
        assertEquals(listOf("交易紀錄", "持股", "股利", "已實現損益", "每月損益", "質押"), sheets.map { it.name })
        val dir = File(System.getProperty("xlsxDir") ?: "build/test-xlsx").apply { mkdirs() }
        val file = File(dir, "sample.xlsx")
        file.outputStream().use { XlsxWriter.write(sheets, it) }
        ZipFile(file).use { zip ->
            assertEquals(5 + sheets.size, zip.size())
        }
        println("XLSX ${file.absolutePath}")
    }
}
