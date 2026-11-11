package com.rboneone.stockrecord.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    @Test fun versionOfAssetName() {
        assertEquals("2.12.0", ReleaseNotes.versionOf("v2.12.0.md"))
        assertEquals("1.0.0", ReleaseNotes.versionOf("v1.0.0.md"))
        assertNull(ReleaseNotes.versionOf("manual.pdf"))
        assertNull(ReleaseNotes.versionOf("README.md"))
    }

    @Test fun sortsNewestFirstNumerically() {
        val sorted = listOf("2.9.0", "2.10.0", "1.16.0", "2.2.1", "2.2.0").sortedWith(ReleaseNotes.newestFirst)
        assertEquals(listOf("2.10.0", "2.9.0", "2.2.1", "2.2.0", "1.16.0"), sorted)
    }
}
