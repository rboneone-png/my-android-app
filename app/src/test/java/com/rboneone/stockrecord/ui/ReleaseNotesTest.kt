package com.rboneone.stockrecord.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class ReleaseNotesTest {
    @Test fun parsesHeadingsBulletsAndNesting() {
        val md = """
            # 股票記帳 2.10.0

            ## 匯入

            **粗體** 說明 `code`

            - 第一項
              - 第二層
            - 第三項
        """.trimIndent()
        val lines = ReleaseNotes.parse(md)
        assertEquals(
            listOf(
                ReleaseNotes.Line(ReleaseNotes.Kind.HEADING, "匯入"),
                ReleaseNotes.Line(ReleaseNotes.Kind.TEXT, "粗體 說明 code"),
                ReleaseNotes.Line(ReleaseNotes.Kind.BULLET, "第一項", 0),
                ReleaseNotes.Line(ReleaseNotes.Kind.BULLET, "第二層", 1),
                ReleaseNotes.Line(ReleaseNotes.Kind.BULLET, "第三項", 0),
            ),
            lines,
        )
    }
}
