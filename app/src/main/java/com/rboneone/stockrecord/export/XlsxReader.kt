package com.rboneone.stockrecord.export

import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

/**
 * 最精簡的 .xlsx 讀取器：把每個工作表讀成「每列的文字格」。
 * 支援 Excel／本 App 匯出的檔案（共用字串、內嵌字串、數字）；數字格以原始文字回傳（日期是序號）。
 * 不支援舊的 .xls、有密碼的檔案。
 */
object XlsxReader {
    private const val MAX_TOTAL_BYTES = 50L * 1024 * 1024

    /** 工作表名稱 → 每一列（含標題列）的格子文字，空格為空字串 */
    fun read(input: InputStream): Map<String, List<List<String>>> {
        val parts = unzip(input)
        val workbook = parts["xl/workbook.xml"] ?: throw IOException("不是有效的 Excel 檔（.xlsx）")
        val rels = parts["xl/_rels/workbook.xml.rels"]?.let { relationships(it) } ?: emptyMap()
        val shared = parts["xl/sharedStrings.xml"]?.let { sharedStrings(it) } ?: emptyList()

        val result = linkedMapOf<String, List<List<String>>>()
        val sheets = parse(workbook).getElementsByTagName("sheet")
        for (i in 0 until sheets.length) {
            val el = sheets.item(i) as Element
            val name = el.getAttribute("name")
            val rid = el.getAttributeNS("http://schemas.openxmlformats.org/officeDocument/2006/relationships", "id")
                .ifEmpty { el.getAttribute("r:id") }
            val target = rels[rid] ?: "worksheets/sheet${i + 1}.xml"
            val path = if (target.startsWith("/")) target.removePrefix("/") else "xl/" + target
            val xml = parts[path] ?: continue
            result[name] = sheetRows(xml, shared)
        }
        if (result.isEmpty()) throw IOException("找不到任何工作表")
        return result
    }

    private fun unzip(input: InputStream): Map<String, ByteArray> {
        val out = HashMap<String, ByteArray>()
        var total = 0L
        ZipInputStream(input).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.isDirectory) continue
                val bytes = zip.readBytes()
                total += bytes.size
                if (total > MAX_TOTAL_BYTES) throw IOException("檔案太大")
                out[entry.name] = bytes
            }
        }
        return out
    }

    private fun parse(bytes: ByteArray): org.w3c.dom.Document {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            // 不處理外部實體與 DOCTYPE，避免惡意檔案（不支援的設定就略過）
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
            runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
            runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
            isExpandEntityReferences = false
        }
        return factory.newDocumentBuilder().parse(ByteArrayInputStream(bytes))
    }

    private fun relationships(bytes: ByteArray): Map<String, String> {
        val list = parse(bytes).getElementsByTagName("Relationship")
        return (0 until list.length).associate {
            val e = list.item(it) as Element
            e.getAttribute("Id") to e.getAttribute("Target")
        }
    }

    private fun sharedStrings(bytes: ByteArray): List<String> {
        val list = parse(bytes).getElementsByTagName("si")
        return (0 until list.length).map { textOf(list.item(it) as Element) }
    }

    /** <si>／<is> 底下所有 <t> 的文字串起來（跳過注音 <rPh>） */
    private fun textOf(el: Element): String {
        val sb = StringBuilder()
        fun walk(n: Node) {
            var c = n.firstChild
            while (c != null) {
                if (c is Element) {
                    when (c.localName ?: c.nodeName) {
                        "t" -> sb.append(c.textContent)
                        "rPh" -> Unit
                        else -> walk(c)
                    }
                }
                c = c.nextSibling
            }
        }
        walk(el)
        return sb.toString()
    }

    private fun sheetRows(bytes: ByteArray, shared: List<String>): List<List<String>> {
        val rows = parse(bytes).getElementsByTagName("row")
        val sparse = sortedMapOf<Int, MutableMap<Int, String>>()
        for (i in 0 until rows.length) {
            val row = rows.item(i) as Element
            val rowIndex = row.getAttribute("r").toIntOrNull()?.minus(1) ?: i
            val cells = row.getElementsByTagName("c")
            var nextCol = 0
            for (j in 0 until cells.length) {
                val c = cells.item(j) as Element
                if (c.parentNode !== row) continue
                val col = columnIndex(c.getAttribute("r")) ?: nextCol
                nextCol = col + 1
                val text = cellText(c, shared)
                if (text.isNotEmpty()) sparse.getOrPut(rowIndex) { sortedMapOf() }[col] = text
            }
        }
        if (sparse.isEmpty()) return emptyList()
        val lastRow = sparse.lastKey()
        return (0..lastRow).map { r ->
            val cols = sparse[r].orEmpty()
            val width = (cols.keys.maxOrNull() ?: -1) + 1
            List(width) { cols[it].orEmpty() }
        }
    }

    private fun cellText(c: Element, shared: List<String>): String {
        val type = c.getAttribute("t")
        fun child(tag: String): Element? {
            var n = c.firstChild
            while (n != null) {
                if (n is Element && (n.localName ?: n.nodeName) == tag) return n
                n = n.nextSibling
            }
            return null
        }
        return when (type) {
            "inlineStr" -> child("is")?.let { textOf(it) }.orEmpty()
            "s" -> child("v")?.textContent?.trim()?.toIntOrNull()?.let { shared.getOrNull(it) }.orEmpty()
            "b" -> if (child("v")?.textContent?.trim() == "1") "TRUE" else "FALSE"
            else -> child("v")?.textContent?.trim().orEmpty()
        }
    }

    /** "C5" → 2；沒有欄位資訊回傳 null */
    private fun columnIndex(ref: String): Int? {
        var n = 0
        var any = false
        for (ch in ref) {
            if (ch in 'A'..'Z') { n = n * 26 + (ch - 'A' + 1); any = true } else break
        }
        return if (any) n - 1 else null
    }
}
