package com.rboneone.stockrecord.ui

import kotlin.math.ceil

/** 操作手冊縮放與捲動的計算（手勢本身在 ManualScreen）。座標單位都是像素 */
object ManualZoom {
    const val MIN_SCALE = 1f
    const val MAX_SCALE = 4f
    /** 頁面圖片的最高繪製倍率；再放大就只是拉伸，避免一頁圖片佔太多記憶體 */
    const val MAX_RENDER = 2f

    /** 整份文件（所有頁面直排成一條）目前的顯示狀態：放大倍率，與內容左上角在畫面上的位置（<= 0） */
    data class View(val scale: Float = 1f, val x: Float = 0f, val y: Float = 0f)

    fun clampScale(scale: Float): Float = scale.coerceIn(MIN_SCALE, MAX_SCALE)

    /** 放大後內容比畫面寬，左右拖曳範圍：內容邊緣不能離開畫面邊緣；沒放大時固定為 0 */
    fun clampOffsetX(x: Float, scale: Float, viewWidth: Float): Float =
        if (scale <= 1f) 0f else x.coerceIn(viewWidth - viewWidth * scale, 0f)

    /** 上下捲動範圍：內容比畫面短時固定在頂端 */
    fun clampOffsetY(y: Float, scale: Float, viewHeight: Float, contentHeight: Float): Float {
        val h = contentHeight * scale
        return if (h <= viewHeight) 0f else y.coerceIn(viewHeight - h, 0f)
    }

    /**
     * 套用一次手勢：zoom 為這一幀的縮放比例、(cx, cy) 為兩指中心、(panX, panY) 為平移量。
     * 縮放以兩指中心為準——中心下面的那個內容點縮放前後留在同一個位置。
     */
    fun transform(
        v: View, zoom: Float, cx: Float, cy: Float, panX: Float, panY: Float,
        viewWidth: Float, viewHeight: Float, contentHeight: Float,
    ): View {
        val newScale = clampScale(v.scale * zoom)
        val ratio = newScale / v.scale
        return View(
            newScale,
            clampOffsetX(cx - (cx - v.x) * ratio + panX, newScale, viewWidth),
            clampOffsetY(cy - (cy - v.y) * ratio + panY, newScale, viewHeight, contentHeight),
        )
    }

    /** 連點兩下：已放大就回到原大小，否則以點的位置為中心放大到 2 倍 */
    fun toggle(v: View, cx: Float, cy: Float, viewWidth: Float, viewHeight: Float, contentHeight: Float): View =
        if (v.scale > 1.05f) transform(v, 1f / v.scale, cx, cy, 0f, 0f, viewWidth, viewHeight, contentHeight)
        else transform(v, 2f / v.scale, cx, cy, 0f, 0f, viewWidth, viewHeight, contentHeight)

    /** 依目前倍率決定頁面圖片的解析度倍率（0.5 一級，最高 [MAX_RENDER]） */
    fun renderScaleFor(scale: Float): Float = minOf(MAX_RENDER, ceil(scale * 2f) / 2f).coerceAtLeast(MIN_SCALE)

    /** 這一頁（內容座標 top～bottom）目前有沒有在畫面上或接近畫面；margin 讓快速捲動時提早載入 */
    fun isVisible(top: Float, bottom: Float, v: View, viewHeight: Float, margin: Float): Boolean =
        top * v.scale + v.y < viewHeight + margin && bottom * v.scale + v.y > -margin
}
