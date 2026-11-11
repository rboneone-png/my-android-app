package com.rboneone.stockrecord.ui.screens

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.rboneone.stockrecord.ui.ManualZoom
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/** 操作手冊 PDF 放在 assets/manual.pdf；用系統內建的 PdfRenderer 在 App 內直接顯示，不需要另外的 PDF 程式 */
private const val MANUAL_ASSET = "manual.pdf"

private class ManualDoc(val renderer: PdfRenderer, val fd: ParcelFileDescriptor) {
    val mutex = Mutex()
    fun close() {
        runCatching { renderer.close() }
        runCatching { fd.close() }
    }
}

@Composable
fun ManualScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var failed by remember { mutableStateOf(false) }
    var doc by remember { mutableStateOf<ManualDoc?>(null) }

    LaunchedEffect(Unit) {
        doc = withContext(Dispatchers.IO) {
            runCatching {
                // PdfRenderer 要讀檔案：先把 assets 裡的 PDF 複製到快取資料夾（每次覆蓋，確保是目前版本）
                val file = File(context.cacheDir, MANUAL_ASSET)
                context.assets.open(MANUAL_ASSET).use { input -> file.outputStream().use { input.copyTo(it) } }
                val fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                ManualDoc(PdfRenderer(fd), fd)
            }.getOrNull()
        }
        if (doc == null) failed = true
    }
    DisposableEffect(Unit) { onDispose { doc?.close() } }

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(
            title = "操作手冊",
            navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回") }
            },
        )
        val d = doc
        when {
            failed -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("無法開啟操作手冊", color = MaterialTheme.colorScheme.error)
            }
            d == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            else -> ZoomablePages(d)
        }
    }
}

/**
 * 兩指縮放（1～4 倍）、放大後單指左右拖曳、連點兩下在 1 倍與 2 倍之間切換；上下滑動照常捲動換頁。
 * 做法：頁面清單用「畫面寬高 ÷ 倍率」的大小排版，再整個放大 [scale] 倍，剛好填滿畫面，所以放大後仍可捲到每一頁的每個位置。
 * 放開手指後，頁面圖片會依新倍率（最高 2 倍解析度）重新繪製，文字才會清楚。
 */
@Composable
private fun ZoomablePages(doc: ManualDoc) {
    val density = LocalDensity.current
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var renderScale by remember { mutableFloatStateOf(1f) }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .clipToBounds()
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = {
                    scale = if (scale > 1.05f) 1f else 2f
                    offsetX = 0f
                    renderScale = ManualZoom.renderScaleFor(scale)
                })
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    var pinching = false
                    do {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val down = event.changes.count { it.pressed }
                        val width = size.width.toFloat()
                        if (down >= 2) {
                            pinching = true
                            val zoom = event.calculateZoom()
                            val centroid = event.calculateCentroid()
                            val pan = event.calculatePan()
                            val newScale = ManualZoom.clampScale(scale * zoom)
                            if (centroid.isSpecified) {
                                offsetX = centroid.x - (centroid.x - offsetX) * (newScale / scale) + pan.x
                            }
                            scale = newScale
                            offsetX = ManualZoom.clampOffset(offsetX, scale, width)
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        } else if (scale > 1f && !pinching) {
                            // 放大後單指左右拖曳；不 consume，上下捲動仍由清單處理
                            offsetX = ManualZoom.clampOffset(offsetX + event.calculatePan().x, scale, width)
                        }
                    } while (event.changes.any { it.pressed })
                    renderScale = ManualZoom.renderScaleFor(scale)
                }
            },
    ) {
        val viewW = constraints.maxWidth.toFloat()
        val viewH = constraints.maxHeight.toFloat()
        val layoutW = with(density) { (viewW / scale).toDp() }
        val layoutH = with(density) { (viewH / scale).toDp() }
        LazyColumn(
            Modifier
                .requiredSize(layoutW, layoutH)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    transformOrigin = TransformOrigin(0f, 0f)
                    translationX = offsetX
                },
        ) {
            items((0 until doc.renderer.pageCount).toList(), key = { it }) { index ->
                PdfPage(doc, index, (viewW * renderScale).toInt().coerceAtLeast(1), Modifier.fillMaxWidth().padding(bottom = 6.dp))
            }
        }
    }
}

@Composable
private fun PdfPage(doc: ManualDoc, index: Int, widthPx: Int, modifier: Modifier) {
    val bitmap by produceState<Bitmap?>(null, doc, index, widthPx) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                doc.mutex.withLock {
                    doc.renderer.openPage(index).use { page ->
                        val h = (widthPx.toFloat() * page.height / page.width).toInt()
                        Bitmap.createBitmap(widthPx, h, Bitmap.Config.ARGB_8888).also { bmp ->
                            bmp.eraseColor(AndroidColor.WHITE)
                            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        }
                    }
                }
            }.getOrNull()
        }
    }
    val bmp = bitmap
    if (bmp != null) {
        Image(bmp.asImageBitmap(), contentDescription = "第 ${index + 1} 頁", modifier = modifier, contentScale = ContentScale.FillWidth)
    } else {
        // 頁面還沒畫好：先留一塊 A4 比例的空白
        Box(modifier.padding(vertical = 120.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
    }
}
