package com.rboneone.stockrecord.export

import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** 工作表的一格 */
sealed interface Cell {
    data class Text(val value: String) : Cell
    /** decimals：0 顯示整數千分位，2 顯示兩位小數 */
    data class Number(val value: Double, val decimals: Int = 0) : Cell
    data object Empty : Cell
}

data class Sheet(
    val name: String,
    val header: List<String>,
    val rows: List<List<Cell>>,
    /** 欄寬（字元數），沒給就用預設 */
    val widths: List<Int> = emptyList(),
)

/** 最精簡的 .xlsx 產生器：第一列粗體標題、凍結標題列、數字千分位 */
object XlsxWriter {
    // 樣式索引：0 一般、1 粗體標題、2 整數千分位、3 兩位小數千分位
    private const val STYLE_HEADER = 1
    private const val STYLE_INT = 2
    private const val STYLE_DEC = 3

    fun write(sheets: List<Sheet>, out: OutputStream) {
        require(sheets.isNotEmpty())
        ZipOutputStream(out).use { zip ->
            fun put(path: String, content: String) {
                zip.putNextEntry(ZipEntry(path))
                zip.write(content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            put("[Content_Types].xml", contentTypes(sheets.size))
            put("_rels/.rels", ROOT_RELS)
            put("xl/workbook.xml", workbook(sheets))
            put("xl/_rels/workbook.xml.rels", workbookRels(sheets.size))
            put("xl/styles.xml", STYLES)
            sheets.forEachIndexed { i, sheet -> put("xl/worksheets/sheet${i + 1}.xml", sheetXml(sheet)) }
        }
    }

    private fun contentTypes(count: Int) = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">""")
        append("""<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>""")
        append("""<Default Extension="xml" ContentType="application/xml"/>""")
        append("""<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>""")
        append("""<Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>""")
        for (i in 1..count) {
            append("""<Override PartName="/xl/worksheets/sheet$i.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>""")
        }
        append("</Types>")
    }

    private const val ROOT_RELS = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>"""

    private fun workbook(sheets: List<Sheet>) = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets>""")
        sheets.forEachIndexed { i, s ->
            append("""<sheet name="${escape(sheetName(s.name))}" sheetId="${i + 1}" r:id="rId${i + 1}"/>""")
        }
        append("</sheets></workbook>")
    }

    private fun workbookRels(count: Int) = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""")
        for (i in 1..count) {
            append("""<Relationship Id="rId$i" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet$i.xml"/>""")
        }
        append("""<Relationship Id="rId${count + 1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>""")
        append("</Relationships>")
    }

    private const val STYLES = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><fonts count="2"><font><sz val="11"/><name val="Calibri"/></font><font><b/><sz val="11"/><name val="Calibri"/></font></fonts><fills count="3"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill><fill><patternFill patternType="solid"><fgColor rgb="FFDDE6F5"/><bgColor indexed="64"/></patternFill></fill></fills><borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders><cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs><cellXfs count="4"><xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/><xf numFmtId="0" fontId="1" fillId="2" borderId="0" xfId="0" applyFont="1" applyFill="1"/><xf numFmtId="3" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/><xf numFmtId="4" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/></cellXfs><cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles></styleSheet>"""

    private fun sheetXml(sheet: Sheet) = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""")
        // 凍結第一列
        append("""<sheetViews><sheetView workbookViewId="0"><pane ySplit="1" topLeftCell="A2" activePane="bottomLeft" state="frozen"/></sheetView></sheetViews>""")
        if (sheet.widths.isNotEmpty()) {
            append("<cols>")
            sheet.widths.forEachIndexed { i, w ->
                append("""<col min="${i + 1}" max="${i + 1}" width="$w" customWidth="1"/>""")
            }
            append("</cols>")
        }
        append("<sheetData>")
        appendRow(1, sheet.header.map { Cell.Text(it) }, header = true)
        sheet.rows.forEachIndexed { i, row -> appendRow(i + 2, row, header = false) }
        append("</sheetData></worksheet>")
    }

    private fun StringBuilder.appendRow(r: Int, cells: List<Cell>, header: Boolean) {
        append("""<row r="$r">""")
        cells.forEachIndexed { c, cell ->
            val ref = columnName(c) + r
            when (cell) {
                is Cell.Text -> {
                    val s = if (header) """ s="$STYLE_HEADER"""" else ""
                    append("""<c r="$ref" t="inlineStr"$s><is><t xml:space="preserve">${escape(cell.value)}</t></is></c>""")
                }
                is Cell.Number -> {
                    if (cell.value.isNaN() || cell.value.isInfinite()) return@forEachIndexed
                    val style = if (cell.decimals == 0) STYLE_INT else STYLE_DEC
                    append("""<c r="$ref" s="$style"><v>${cell.value}</v></c>""")
                }
                Cell.Empty -> Unit
            }
        }
        append("</row>")
    }

    /** 0 → A、25 → Z、26 → AA */
    fun columnName(index: Int): String {
        var n = index + 1
        val sb = StringBuilder()
        while (n > 0) {
            val rem = (n - 1) % 26
            sb.append('A' + rem)
            n = (n - 1) / 26
        }
        return sb.reverse().toString()
    }

    /** Excel 工作表名稱不能超過 31 字，也不能有 : \ / ? * [ ] */
    private fun sheetName(name: String) = name.replace(Regex("""[:\\/?*\[\]]"""), "_").take(31)

    private fun escape(s: String): String = buildString {
        for (ch in s) {
            when {
                ch == '&' -> append("&amp;")
                ch == '<' -> append("&lt;")
                ch == '>' -> append("&gt;")
                ch == '"' -> append("&quot;")
                // XML 不允許的控制字元
                ch < ' ' && ch != '\t' && ch != '\n' && ch != '\r' -> Unit
                else -> append(ch)
            }
        }
    }
}
