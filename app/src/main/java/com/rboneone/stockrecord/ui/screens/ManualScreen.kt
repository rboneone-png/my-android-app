package com.rboneone.stockrecord.ui.screens

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.wrapContentHeight
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.rboneone.stockrecord.ui.ManualZoom
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs

/** 操作手冊 PDF 放在 assets/manual.pdf；用系統內建的 PdfRenderer 在 App 內直接顯示，不需要另外的 PDF 程式 */
private const val MANUAL_ASSET = "manual.pdf"

/** 一份可以逐頁繪製的文件（正式版是 PDF，測試時可以換成假的） */
internal interface PageSource {
    val count: Int
    /** 高 ÷ 寬 */
    fun aspect(index: Int): Float
    suspend fun render(index: Int, widthPx: Int): Bitmap?
}

private class PdfPageSource(private val renderer: PdfRenderer, private val fd: ParcelFileDescriptor) : PageSource {
    private val mutex = Mutex()
    private val aspects: List<Float> = (0 until renderer.pageCount).map { i ->
        renderer.openPage(i).use { it.height.toFloat() / it.width }
    }
    override val count get() = aspects.size
    override fun aspect(index: Int) = aspects[index]

    override suspend fun render(index: Int, widthPx: Int): Bitmap? = withContext(Dispatchers.IO) {
        runCatching {
            // PdfRenderer 一次只能開一頁
            mutex.withLock {
                renderer.openPage(index).use { page ->
                    val h = (widthPx.toFloat() * page.height / page.width).toInt().coerceAtLeast(1)
                    Bitmap.createBitmap(widthPx, h, Bitmap.Config.ARGB_8888).also { bmp ->
                        bmp.eraseColor(AndroidColor.WHITE)
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    }
                }
            }
        }.getOrNull()
    }

    fun close() {
        runCatching { renderer.close() }
        runCatching { fd.close() }
    }
}

@Composable
fun ManualScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var failed by remember { mutableStateOf(false) }
    var source by remember { mutableStateOf<PdfPageSource?>(null) }

    LaunchedEffect(Unit) {
        source = withContext(Dispatchers.IO) {
            runCatching {
                // PdfRenderer 要讀檔案：先把 assets 裡的 PDF 複製到快取資料夾（每次覆蓋，確保是目前版本）
                val file = File(context.cacheDir, MANUAL_ASSET)
                context.assets.open(MANUAL_ASSET).use { input -> file.outputStream().use { input.copyTo(it) } }
                val fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                PdfPageSource(PdfRenderer(fd), fd)
            }.getOrNull()
        }
        if (source == null) failed = true
    }
    DisposableEffect(Unit) { onDispose { source?.close() } }

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(
            title = "操作手冊",
            navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回") }
            },
        )
        val s = source
        when {
            failed -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("無法開啟操作手冊", color = MaterialTheme.colorScheme.error)
            }
            s == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            else -> ZoomablePages(s)
        }
    }
}

/**
 * 所有頁面直排成一條長長的內容，整條內容用同一組「倍率＋位移」顯示：
 * - 單指拖曳：上下、左右移動（放開後會滑行一段）
 * - 兩指張開／捏合：以兩指中心縮放（1～4 倍）
 * - 連點兩下：在原大小與 2 倍之間切換
 * 縮放只動顯示用的倍率，不重新排版，所以不會跳動；放開手指後才依新倍率（最高 2 倍解析度）重畫看得到的頁面。
 */
@Composable
internal fun ZoomablePages(source: PageSource, onViewChange: (ManualZoom.View) -> Unit = {}) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    var view by remember { mutableStateOf(ManualZoom.View()) }
    var renderScale by remember { mutableFloatStateOf(1f) }
    var fling by remember { mutableStateOf<Job?>(null) }

    BoxWithConstraints(Modifier.fillMaxSize().clipToBounds()) {
        val viewW = constraints.maxWidth.toFloat()
        val viewH = constraints.maxHeight.toFloat()
        val gap = with(density) { 6.dp.toPx() }
        val heights = remember(source, viewW) { List(source.count) { viewW * source.aspect(it) } }
        val tops = remember(heights, gap) {
            var y = 0f
            heights.map { h -> y.also { y += h + gap } }
        }
        val contentH = (tops.lastOrNull() ?: 0f) + (heights.lastOrNull() ?: 0f)
        fun update(v: ManualZoom.View) { view = v; onViewChange(v) }

        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(viewW, viewH, contentH) {
                    detectTapGestures(onDoubleTap = { p ->
                        fling?.cancel()
                        update(ManualZoom.toggle(view, p.x, p.y, viewW, viewH, contentH))
                        renderScale = ManualZoom.renderScaleFor(view.scale)
                    })
                }
                .pointerInput(viewW, viewH, contentH) {
                    awaitEachGesture {
                        fling?.cancel()
                        awaitFirstDown(requireUnconsumed = false)
                        val tracker = VelocityTracker()
                        var multiTouch = false
                        do {
                            val event = awaitPointerEvent()
                            if (event.changes.count { it.pressed } >= 2) multiTouch = true
                            val centroid = event.calculateCentroid()
                            if (centroid.isSpecified && event.changes.any { it.positionChanged() }) {
                                val pan = event.calculatePan()
                                update(
                                    ManualZoom.transform(
                                        view, event.calculateZoom(), centroid.x, centroid.y, pan.x, pan.y,
                                        viewW, viewH, contentH,
                                    )
                                )
                                event.changes.forEach { if (it.positionChanged()) it.consume() }
                            }
                            event.changes.firstOrNull()?.let {
                                tracker.addPosition(it.uptimeMillis, it.position)
                            }
                        } while (event.changes.any { it.pressed })
                        renderScale = ManualZoom.renderScaleFor(view.scale)
                        if (!multiTouch) {
                            val v = tracker.calculateVelocity()
                            if (abs(v.x) + abs(v.y) > 300f) {
                                fling = scope.launch {
                                    launch {
                                        Animatable(view.x).animateDecay(v.x, exponentialDecay(frictionMultiplier = 1.6f)) {
                                            update(view.copy(x = ManualZoom.clampOffsetX(value, view.scale, viewW)))
                                        }
                                    }
                                    launch {
                                        Animatable(view.y).animateDecay(v.y, exponentialDecay(frictionMultiplier = 1.6f)) {
                                            update(view.copy(y = ManualZoom.clampOffsetY(value, view.scale, viewH, contentH)))
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
        ) {
            Column(
                Modifier
                    .graphicsLayer {
                        scaleX = view.scale
                        scaleY = view.scale
                        transformOrigin = TransformOrigin(0f, 0f)
                        translationX = view.x
                        translationY = view.y
                    }
                    .requiredWidth(with(density) { viewW.toDp() })
                    .wrapContentHeight(Alignment.Top, unbounded = true),
            ) {
                val margin = viewH * 0.5f
                for (i in 0 until source.count) {
                    val visible = ManualZoom.isVisible(tops[i], tops[i] + heights[i], view, viewH, margin)
                    PdfPage(
                        source, i, (viewW * renderScale).toInt().coerceAtLeast(1), visible,
                        Modifier.height(with(density) { (heights[i] + if (i < source.count - 1) gap else 0f).toDp() }),
                    )
                }
            }
        }
    }
}

/** 只在接近畫面時才繪製頁面；離開後釋放圖片，長文件也不會佔太多記憶體。換倍率重畫時先保留舊圖，不會閃白 */
@Composable
private fun PdfPage(source: PageSource, index: Int, widthPx: Int, visible: Boolean, modifier: Modifier) {
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(visible, widthPx) {
        bitmap = if (visible) source.render(index, widthPx) ?: bitmap else null
    }
    Box(modifier, contentAlignment = Alignment.TopCenter) {
        val bmp = bitmap
        if (bmp != null) {
            Image(
                bmp.asImageBitmap(), contentDescription = "第 ${index + 1} 頁",
                modifier = Modifier.fillMaxSize(), contentScale = ContentScale.FillWidth, alignment = Alignment.TopCenter,
            )
        }
    }
}
