package com.example.arxivpreview.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.Closeable
import kotlin.math.roundToInt

/** Renderer/page access is serialized. Only the displayed page bitmap is retained by the UI. */
internal class PdfPageSource(context: Context, uri: Uri) : Closeable {
    private val descriptor = requireNotNull(context.contentResolver.openFileDescriptor(uri, "r"))
    private val renderer = try { PdfRenderer(descriptor) } catch (error: Exception) { descriptor.close(); throw error }
    val pageCount: Int get() = renderer.pageCount
    @Synchronized fun render(index: Int, width: Int): Bitmap = renderer.openPage(index).use { page ->
        val scale = minOf(width.coerceIn(320, 2200).toFloat() / page.width, 2600f / page.height)
        val bitmap = Bitmap.createBitmap((page.width * scale).roundToInt().coerceAtLeast(1),
            (page.height * scale).roundToInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.WHITE)
        try { page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY); bitmap }
        catch (error: Exception) { bitmap.recycle(); throw error }
    }
    @Synchronized override fun close() { renderer.close(); descriptor.close() }
}

@Composable
internal fun BasicPdfReader(uri: Uri) {
    val context = LocalContext.current
    var page by rememberSaveable(uri.toString()) { mutableIntStateOf(0) }
    var count by remember(uri) { mutableIntStateOf(0) }
    var source by remember(uri) { mutableStateOf<PdfPageSource?>(null) }
    var bitmap by remember(uri) { mutableStateOf<Bitmap?>(null) }
    var error by remember(uri) { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    var zoom by remember(page) { mutableFloatStateOf(1f) }
    var pan by remember(page) { mutableStateOf(Offset.Zero) }
    LaunchedEffect(uri, reload) {
        error = null
        try {
            // Opening and closing happen inside the IO block even if the composition is cancelled.
            withContext(Dispatchers.IO) {
                val opened = PdfPageSource(context, uri)
                try {
                    withContext(Dispatchers.Main) { source = opened; count = opened.pageCount; page = page.coerceIn(0, (count - 1).coerceAtLeast(0)) }
                    kotlinx.coroutines.awaitCancellation()
                } finally { opened.close() }
            }
        } catch (cancel: kotlinx.coroutines.CancellationException) { throw cancel }
        catch (failure: Exception) { error = "This PDF could not be read. Try downloading it again." }
    }
    Column(Modifier.fillMaxSize()) {
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().clipToBounds(), contentAlignment = Alignment.Center) {
            val width = constraints.maxWidth
            LaunchedEffect(source, page, width) {
                val opened = source ?: return@LaunchedEffect
                bitmap = null
                try { bitmap = withContext(Dispatchers.IO) { opened.render(page, width * 2) } }
                catch (cancel: kotlinx.coroutines.CancellationException) { throw cancel }
                catch (failure: Exception) { error = "Could not render this page. Try again." }
            }
            when {
                error != null -> Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(error!!)
                    TextButton(onClick = { source = null; bitmap = null; reload++ }) { Text("Retry") }
                }
                bitmap == null -> CircularProgressIndicator()
                else -> Image(bitmap!!.asImageBitmap(), "PDF page ${page + 1}",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().pointerInput(page) {
                        detectTransformGestures { _, delta, scale, _ ->
                            zoom = (zoom * scale).coerceIn(1f, 5f)
                            val limitX = size.width * (zoom - 1) / 2f
                            val limitY = size.height * (zoom - 1) / 2f
                            pan = Offset((pan.x + delta.x).coerceIn(-limitX, limitX), (pan.y + delta.y).coerceIn(-limitY, limitY))
                        }
                    }.graphicsLayer(scaleX = zoom, scaleY = zoom, translationX = pan.x, translationY = pan.y))
            }
        }
        Surface {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = { page-- }, enabled = page > 0) { Text("Previous") }
                Text(if (count > 0) "${page + 1} / $count" else "PDF", style = MaterialTheme.typography.labelLarge)
                TextButton(onClick = { zoom = 1f; pan = Offset.Zero }) { Text("Fit") }
                TextButton(onClick = { page++ }, enabled = page + 1 < count) { Text("Next") }
            }
        }
    }
}
