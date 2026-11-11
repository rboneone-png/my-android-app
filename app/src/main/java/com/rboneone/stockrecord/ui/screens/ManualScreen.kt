package com.rboneone.stockrecord.ui.screens

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/** 操作手冊 PDF 放在 assets/manual.pdf；用系統內建的 PdfRenderer 在 App 內直接顯示，不需要另外的 PDF 程式 */
private const val MANUAL_ASSET = "manual.pdf"
private val ZOOMS = listOf(1f, 1.5f, 2f)

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
    var zoomIndex by rememberSaveable { mutableIntStateOf(0) }
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
            actions = {
                TextButton(onClick = { if (zoomIndex > 0) zoomIndex-- }, enabled = zoomIndex > 0) { Text("縮小") }
                TextButton(onClick = { if (zoomIndex < ZOOMS.lastIndex) zoomIndex++ }, enabled = zoomIndex < ZOOMS.lastIndex) { Text("放大") }
            },
        )
        val d = doc
        when {
            failed -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("無法開啟操作手冊", color = MaterialTheme.colorScheme.error)
            }
            d == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            else -> {
                val zoom = ZOOMS[zoomIndex]
                val density = LocalDensity.current
                val screenDp = LocalConfiguration.current.screenWidthDp.dp
                val pageWidth = screenDp * zoom
                val widthPx = with(density) { pageWidth.roundToPx() }
                // 放大時頁面比螢幕寬，可以左右滑動；上下滑動換頁
                Box(Modifier.fillMaxSize().then(if (zoom > 1f) Modifier.horizontalScroll(rememberScrollState()) else Modifier)) {
                    LazyColumn(Modifier.width(pageWidth)) {
                        items((0 until d.renderer.pageCount).toList(), key = { it }) { index ->
                            PdfPage(d, index, widthPx, Modifier.fillMaxWidth().padding(bottom = 6.dp))
                        }
                    }
                }
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
