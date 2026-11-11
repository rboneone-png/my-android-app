package com.rboneone.stockrecord.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class ManualZoomTest {
    @Test fun scaleIsClamped() {
        assertEquals(1f, ManualZoom.clampScale(0.3f), 0f)
        assertEquals(2.5f, ManualZoom.clampScale(2.5f), 0f)
        assertEquals(4f, ManualZoom.clampScale(9f), 0f)
    }

    @Test fun offsetStaysInsideContent() {
        // 畫面寬 1000、放大 2 倍：內容寬 2000，只能往左拖 0～1000
        assertEquals(0f, ManualZoom.clampOffset(50f, 2f, 1000f), 0f)
        assertEquals(-400f, ManualZoom.clampOffset(-400f, 2f, 1000f), 0f)
        assertEquals(-1000f, ManualZoom.clampOffset(-1500f, 2f, 1000f), 0f)
        // 沒放大時不能拖
        assertEquals(0f, ManualZoom.clampOffset(-200f, 1f, 1000f), 0f)
    }

    @Test fun renderScaleStepsAndCaps() {
        assertEquals(1f, ManualZoom.renderScaleFor(1f), 0f)
        assertEquals(1.5f, ManualZoom.renderScaleFor(1.2f), 0f)
        assertEquals(2f, ManualZoom.renderScaleFor(2f), 0f)
        assertEquals(2f, ManualZoom.renderScaleFor(4f), 0f)
    }
}
