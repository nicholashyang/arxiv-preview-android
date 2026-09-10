package com.example.arxivpreview.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color as AndroidColor
import android.webkit.*
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.util.concurrent.atomic.AtomicInteger

internal fun containsMath(text: String): Boolean =
    text.contains('$') || text.contains("\\(") || text.contains("\\[")

/** The original paper text is never interpreted as HTML or executable JavaScript. */
@Composable
fun MathText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyLarge,
    fontWeight: FontWeight? = null,
    color: Color = MaterialTheme.colorScheme.onSurface,
    maxLines: Int = Int.MAX_VALUE,
) {
    if (!containsMath(text)) {
        Text(text, modifier, color = color, style = style, fontWeight = fontWeight,
            maxLines = maxLines, overflow = TextOverflow.Ellipsis)
        return
    }
    val density = LocalDensity.current
    val fontSize = style.fontSize.value * density.fontScale
    val lineHeight = style.lineHeight.value.takeIf { it.isFinite() }?.times(density.fontScale) ?: fontSize * 1.5f
    var height by remember(text, fontSize, maxLines) { mutableIntStateOf(lineHeight.toInt().coerceAtLeast(1)) }
    val data = remember(text, color, fontSize, lineHeight, maxLines, fontWeight) {
        JSONObject().put("text", text).put("fontSize", fontSize).put("lineHeight", lineHeight)
            .put("weight", (fontWeight ?: style.fontWeight ?: FontWeight.Normal).weight)
            .put("color", String.format("#%06X", color.toArgb() and 0xFFFFFF))
            .put("maxLines", if (maxLines == Int.MAX_VALUE) 0 else maxLines).toString()
    }
    AndroidView(
        factory = { MathWebView(it) },
        modifier = modifier.fillMaxWidth().height(height.dp),
        onReset = { it.reset() },
        onRelease = { it.release() },
        update = { view -> view.show(data) { height = it.coerceIn(1, 100000) } },
    )
}

@SuppressLint("SetJavaScriptEnabled", "ViewConstructor")
internal class MathWebView(context: Context) : WebView(context) {
    private var ready = false
    private var payload: String? = null
    private var heightChanged: ((Int) -> Unit)? = null
    private val generation = AtomicInteger()
    init {
        setBackgroundColor(AndroidColor.TRANSPARENT)
        isVerticalScrollBarEnabled = false
        isHorizontalScrollBarEnabled = false
        settings.apply {
            javaScriptEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            blockNetworkLoads = true
            textZoom = 100
            cacheMode = WebSettings.LOAD_NO_CACHE
        }
        addJavascriptInterface(object {
            @JavascriptInterface fun update(token: Int, height: Int) {
                post { if (generation.get() == token) heightChanged?.invoke(height) }
            }
        }, "MathHeight")
        webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) = true
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse {
                val path = request.url.path.orEmpty().removePrefix("/math/")
                if (request.url.scheme == "https" && request.url.host == "appassets.androidplatform.net" &&
                    request.url.path.orEmpty().startsWith("/math/") && !path.contains("..")) {
                    val mime = when (path.substringAfterLast('.')) {
                        "js" -> "application/javascript"
                        "css" -> "text/css"
                        "woff2" -> "font/woff2"
                        else -> "text/html"
                    }
                    runCatching { context.assets.open("math/$path") }.getOrNull()?.let {
                        return WebResourceResponse(mime, "UTF-8", it)
                    }
                }
                return WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream(ByteArray(0)))
            }
            override fun onPageFinished(view: WebView, url: String) { ready = true; render() }
        }
        loadUrl("https://appassets.androidplatform.net/math/index.html")
    }
    fun show(data: String, callback: (Int) -> Unit) {
        heightChanged = callback
        if (payload == data) return
        payload = data
        generation.incrementAndGet()
        render()
    }
    private fun render() {
        val data = payload ?: return
        if (ready) evaluateJavascript("showPaper(${JSONObject(data).put("generation", generation.get())});", null)
    }
    fun reset() { generation.incrementAndGet(); payload = null; heightChanged = null }
    fun release() { reset(); removeJavascriptInterface("MathHeight"); stopLoading(); destroy() }
}
