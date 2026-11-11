package com.rboneone.stockrecord.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ManualZoomTest {
    private val w = 1000f
    private val h = 2000f
    private val content = 10_000f

    @Test fun scaleIsClamped() {
        assertEquals(1f, ManualZoom.clampScale(0.3f), 0f)
        assertEquals(2.5f, ManualZoom.clampScale(2.5f), 0f)
        assertEquals(4f, ManualZoom.clampScale(9f), 0f)
    }

    @Test fun offsetsStayInsideContent() {
        assertEquals(0f, ManualZoom.clampOffsetX(50f, 2f, w), 0f)
        assertEquals(-400f, ManualZoom.clampOffsetX(-400f, 2f, w), 0f)
        assertEquals(-1000f, ManualZoom.clampOffsetX(-1500f, 2f, w), 0f)
        assertEquals(0f, ManualZoom.clampOffsetX(-200f, 1f, w), 0f)
        // 上下：內容 10000，畫面 2000，最多往上捲 8000
        assertEquals(-8000f, ManualZoom.clampOffsetY(-9000f, 1f, h, content), 0f)
        assertEquals(0f, ManualZoom.clampOffsetY(300f, 1f, h, content), 0f)
        // 內容比畫面短：固定在頂端
        assertEquals(0f, ManualZoom.clampOffsetY(-50f, 1f, h, 1500f), 0f)
    }

    @Test fun pinchKeepsPointUnderFingersFixed() {
        // 已捲到 y = -3000；在畫面 (400, 600) 放大 2 倍：該點的內容座標 (400, 3600) 縮放後仍在 (400, 600)
        val start = ManualZoom.View(1f, 0f, -3000f)
        val end = ManualZoom.transform(start, 2f, 400f, 600f, 0f, 0f, w, h, content)
        assertEquals(2f, end.scale, 0f)
        val contentX = (400f - end.x) / end.scale
        val contentY = (600f - end.y) / end.scale
        assertEquals(400f, contentX, 0.01f)
        assertEquals(3600f, contentY, 0.01f)
    }

    @Test fun zoomOutBackToOneResetsHorizontalOffset() {
        val zoomed = ManualZoom.transform(ManualZoom.View(), 3f, 800f, 500f, 0f, 0f, w, h, content)
        assertTrue(zoomed.x < 0f)
        val back = ManualZoom.transform(zoomed, 1f / 3f, 800f, 500f, 0f, 0f, w, h, content)
        assertEquals(1f, back.scale, 0.0001f)
        assertEquals(0f, back.x, 0f)
    }

    @Test fun panCannotLeaveContent() {
        val v = ManualZoom.transform(ManualZoom.View(2f, -500f, -100f), 1f, 0f, 0f, -9999f, 9999f, w, h, content)
        assertEquals(-1000f, v.x, 0f)
        assertEquals(0f, v.y, 0f)
    }

    @Test fun doubleTapTogglesBetweenOneAndTwo() {
        val a = ManualZoom.toggle(ManualZoom.View(), 500f, 500f, w, h, content)
        assertEquals(2f, a.scale, 0.0001f)
        val b = ManualZoom.toggle(a, 500f, 500f, w, h, content)
        assertEquals(1f, b.scale, 0.0001f)
        assertEquals(0f, b.x, 0f)
    }

    @Test fun renderScaleStepsAndCaps() {
        assertEquals(1f, ManualZoom.renderScaleFor(1f), 0f)
        assertEquals(1.5f, ManualZoom.renderScaleFor(1.2f), 0f)
        assertEquals(2f, ManualZoom.renderScaleFor(2f), 0f)
        assertEquals(2f, ManualZoom.renderScaleFor(4f), 0f)
    }

    @Test fun visibilityUsesScaleAndOffset() {
        val v = ManualZoom.View(2f, 0f, -4000f)
        // 內容 2000～3000 → 畫面 0～2000，看得到
        assertTrue(ManualZoom.isVisible(2000f, 3000f, v, h, 0f))
        // 內容 0～1000 → 畫面 -4000～-2000，看不到
        assertFalse(ManualZoom.isVisible(0f, 1000f, v, h, 0f))
        // 加上 margin 後，剛好在畫面外的也算
        assertTrue(ManualZoom.isVisible(0f, 1000f, ManualZoom.View(1f, 0f, -1100f), h, 200f))
    }
}
