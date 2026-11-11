package com.rboneone.stockrecord.ui

import kotlin.math.ceil

/** 操作手冊縮放的計算（手勢本身在 ManualScreen） */
object ManualZoom {
    const val MIN_SCALE = 1f
    const val MAX_SCALE = 4f
    /** 頁面圖片的最高繪製倍率；再放大就只是拉伸，避免一頁圖片佔太多記憶體 */
    const val MAX_RENDER = 2f

    fun clampScale(scale: Float): Float = scale.coerceIn(MIN_SCALE, MAX_SCALE)

    /** 放大後內容比畫面寬，左右拖曳範圍：右緣不能離開畫面右緣、左緣不能離開畫面左緣 */
    fun clampOffset(offsetX: Float, scale: Float, viewWidth: Float): Float =
        offsetX.coerceIn(viewWidth - viewWidth * scale, 0f)

    /** 依目前倍率決定頁面圖片的解析度倍率（0.5 一級，最高 [MAX_RENDER]） */
    fun renderScaleFor(scale: Float): Float = minOf(MAX_RENDER, ceil(scale * 2f) / 2f).coerceAtLeast(MIN_SCALE)
}
