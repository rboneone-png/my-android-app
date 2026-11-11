package com.rboneone.stockrecord.ui

/** 版本更新說明（docs/release-notes/v*.md）的簡單解析，給設定 → 關於顯示用 */
object ReleaseNotes {
    enum class Kind { HEADING, BULLET, TEXT }

    /** indent：0 = 第一層，1 = 縮排的第二層 */
    data class Line(val kind: Kind, val text: String, val indent: Int = 0)

    /** 去掉 Markdown 的粗體、行內程式碼符號 */
    private fun clean(s: String) = s.replace("**", "").replace("`", "").trim()

    /** 解析 Markdown：略過最上面的「# 標題」（版本號另外顯示）；「##」當小標；「-」「*」當項目；其餘當一般文字 */
    fun parse(markdown: String): List<Line> {
        val out = mutableListOf<Line>()
        for (raw in markdown.lines()) {
            if (raw.isBlank()) continue
            val trimmed = raw.trimStart()
            val leading = raw.length - trimmed.length
            when {
                trimmed.startsWith("# ") -> Unit
                trimmed.startsWith("#") -> out += Line(Kind.HEADING, clean(trimmed.trimStart('#')))
                trimmed.startsWith("- ") || trimmed.startsWith("* ") ->
                    out += Line(Kind.BULLET, clean(trimmed.drop(2)), if (leading >= 2) 1 else 0)
                else -> out += Line(Kind.TEXT, clean(trimmed))
            }
        }
        return out
    }
}
