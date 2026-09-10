package com.example.arxivpreview.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.*
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import com.example.arxivpreview.ArxivApplication
import com.example.arxivpreview.data.AppPreferences
import com.example.arxivpreview.data.local.HtmlProgressEntity
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.Saver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.arxivpreview.model.*
import com.example.arxivpreview.ui.theme.LocalDarkAppearance

internal fun openExternal(context: Context, url: String): Boolean {
    val uri = Uri.parse(url)
    if (uri.scheme !in setOf("https", "http")) return false
    return runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)); true }.getOrDefault(false)
}

internal fun isArxivHtml(uri: Uri): Boolean = uri.scheme == "https" && uri.host == "arxiv.org" &&
    uri.path.orEmpty().startsWith("/html/")

private class HtmlReadingState(var history: Bundle? = null, var scrollY: Int = 0) {
    var view: WebView? = null
}
private val HtmlReadingSaver = Saver<HtmlReadingState, Bundle>(
    save = { state -> Bundle().apply {
        putBundle("history", state.view?.let { Bundle().also(it::saveState) } ?: state.history)
        putInt("scrollY", state.view?.scrollY ?: state.scrollY)
    } },
    restore = { HtmlReadingState(it.getBundle("history"), it.getInt("scrollY")) },
)

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun HtmlScreen(
    paper: Paper?,
    onBack: () -> Unit,
    interceptRequest: ((WebResourceRequest) -> WebResourceResponse?)? = null,
    onReadPdf: () -> Unit,
) {
    if (paper == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    val context = LocalContext.current
    val container = (context.applicationContext as ArxivApplication).container
    val loadedPreferences by container.preferencesRepository.preferences.collectAsStateWithLifecycle<com.example.arxivpreview.data.AppPreferences?>(null)
    val preferences = loadedPreferences ?: AppPreferences()
    val scope = rememberCoroutineScope()
    val persistenceScope = container.persistenceScope
    var storedPosition by remember(paper.canonicalVersionedId) { mutableStateOf<HtmlProgressEntity?>(null) }
    var positionLoaded by remember(paper.canonicalVersionedId) { mutableStateOf(false) }
    var contents by remember { mutableStateOf<List<Pair<String, String>>?>(null) }
    var showSettings by remember { mutableStateOf(false) }
    var showFind by remember { mutableStateOf(false) }
    var findText by remember { mutableStateOf("") }
    var findCount by remember { mutableStateOf("") }
    var menu by remember { mutableStateOf(false) }
    LaunchedEffect(paper.canonicalVersionedId) {
        storedPosition = container.database.dao().progress(paper.canonicalVersionedId)
        positionLoaded = true
    }
    val currentPreferences by rememberUpdatedState(preferences)
    val dark = LocalDarkAppearance.current
    var webView by remember { mutableStateOf<WebView?>(null) }
    var progress by remember { mutableIntStateOf(0) }
    var readerReady by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val readingState = rememberSaveable(paper.id, saver = HtmlReadingSaver) { HtmlReadingState() }
    var retry by remember { mutableIntStateOf(0) }
    val currentDark by rememberUpdatedState(dark)
    val currentReadPdf by rememberUpdatedState(onReadPdf)
    fun capturePosition(view: WebView, save: Boolean = true, after: ((HtmlProgressEntity?) -> Unit)? = null) {
        if (!readerReady || !isArxivHtml(Uri.parse(view.url ?: ""))) return
        view.evaluateJavascript(readPositionScript) { raw ->
            val position = parseHtmlPosition(paper.canonicalVersionedId, raw)
            if (position != null && save) persistenceScope.launch { container.database.dao().saveProgress(position) }
            after?.invoke(position)
        }
    }
    fun back() { webView?.let { capturePosition(it) }; if (webView?.canGoBack() == true) webView?.goBack() else onBack() }
    BackHandler { back() }
    LaunchedEffect(webView, error) {
        while (error == null) { delay(1_000); webView?.let { if (progress == 100) capturePosition(it) } }
    }
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, webView) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) webView?.let { capturePosition(it) }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(preferences.readerMobile, preferences.readerFont, preferences.readerLine) {
        webView?.let { view -> capturePosition(view, false) { pos -> applyReaderLayout(view, preferences) { pos?.let { restoreHtmlPosition(view, it) } } } }
    }
    DisposableEffect(Unit) {
        onDispose { webView?.let { readingState.history = Bundle().also(it::saveState); readingState.scrollY = it.scrollY } }
    }
    LaunchedEffect(dark) { webView?.let { applyHtmlTheme(it, dark) } }
    Scaffold(topBar = {
        TopAppBar(title = { Text("HTML") }, navigationIcon = {
            IconButton(onClick = { back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
        }, actions = {
            IconButton(onClick = { sharePaper(context, paper) }) { Icon(Icons.Default.Share, "Share paper") }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Reader tools") }
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem(text = { Text("Contents") }, onClick = { menu = false; webView?.evaluateJavascript(readerContentsScript) { raw ->
                        contents = runCatching { val array = org.json.JSONArray(raw); (0 until array.length()).map { i -> array.getJSONObject(i).let { it.getString("id") to it.getString("title") } } }.getOrDefault(emptyList())
                    } })
                    DropdownMenuItem(text = { Text("Find in page") }, onClick = { menu = false; showFind = true })
                    DropdownMenuItem(text = { Text("Reading settings") }, onClick = { menu = false; showSettings = true })
                }
            }
            TextButton(onClick = onReadPdf) { Text("PDF") }
            IconButton(onClick = { openExternal(context, paper.htmlUrl) }) {
                Icon(Icons.Default.OpenInBrowser, "Open in browser")
            }
        })
    }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (error != null) {
                Column(Modifier.align(Alignment.Center).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Unable to open HTML", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(12.dp)); Text(error!!)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        TextButton(onClick = { error = null; readingState.history = null; retry++ }) { Text("Retry") }
                        TextButton(onClick = onReadPdf) { Text("Read PDF") }
                    }
                }
            } else if (positionLoaded && loadedPreferences != null) {
                key(paper.id, retry) {
                    AndroidView(factory = { ctx ->
                        var restoreY: Int? = readingState.scrollY
                        WebView(ctx).apply {
                            webView = this
                            readingState.view = this
                            setBackgroundColor(if (currentDark) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
                            settings.apply {
                                javaScriptEnabled = true
                                domStorageEnabled = true
                                allowFileAccess = false
                                allowContentAccess = false
                                mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                                builtInZoomControls = true
                                displayZoomControls = false
                            }
                            setOnScrollChangeListener { _, _, y, _, _ -> readingState.scrollY = y }
                            setFindListener { active, count, done -> if (done) findCount = if (count == 0) "No matches" else "${active + 1} / $count" }
                            webChromeClient = object : WebChromeClient() {
                                override fun onProgressChanged(view: WebView, newProgress: Int) { progress = newProgress }
                            }
                            webViewClient = object : WebViewClient() {
                                override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                                    interceptRequest?.invoke(request) ?: super.shouldInterceptRequest(view, request)
                                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                                    if (!request.isForMainFrame) return false
                                    val uri = request.url
                                    if (isArxivHtml(uri) && ArxivIds.versioned(uri.toString()) == paper.canonicalVersionedId) return false
                                    if (uri.host == "arxiv.org" && uri.path.orEmpty().startsWith("/pdf/") &&
                                        ArxivIds.normalize(uri.toString()) == ArxivIds.normalize(paper.canonicalVersionedId)) {
                                        currentReadPdf()
                                    } else { openExternal(ctx, uri.toString()) }
                                    return true
                                }
                                override fun onPageFinished(view: WebView, url: String) {
                                    if (!isArxivHtml(Uri.parse(url))) {
                                        error = "This paper does not have an HTML version. You can read the PDF instead."
                                        return
                                    }
                                    applyHtmlTheme(view, currentDark)
                                    val initialY = restoreY
                                    applyReaderLayout(view, currentPreferences) {
                                        if (initialY != null) view.postVisualStateCallback(1, object : WebView.VisualStateCallback() {
                                            override fun onComplete(requestId: Long) {
                                                if (initialY > 0) view.scrollTo(0, initialY)
                                                else storedPosition?.let { restoreHtmlPosition(view, it) }
                                                view.postDelayed({ readerReady = true; view.evaluateJavascript("document.documentElement.dataset.arxivReaderReady='true';", null) }, 150)
                                            }
                                        })
                                    }
                                    if (initialY == null) readerReady = true
                                    restoreY = null
                                    readingState.history = Bundle().also(view::saveState)
                                }
                                override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: WebResourceResponse) {
                                    if (request.isForMainFrame) error = if (response.statusCode == 404 || response.statusCode == 410)
                                        "This paper does not have an HTML version. You can read the PDF instead."
                                    else "The server returned HTTP ${response.statusCode}. Please try again."
                                }
                                override fun onReceivedError(view: WebView, request: WebResourceRequest, failure: WebResourceError) {
                                    if (request.isForMainFrame) error = "Could not load the article. Check your connection and retry."
                                }
                                override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                                    error = "The reader stopped. Tap Retry to reopen this article."
                                    return true
                                }
                            }
                            if (readingState.history == null || restoreState(readingState.history!!) == null) loadUrl(paper.htmlUrl)
                        }
                    }, modifier = Modifier.fillMaxSize(), onRelease = {
                        readingState.history = Bundle().also(it::saveState)
                        readingState.scrollY = it.scrollY
                        readingState.view = null
                        val released = it
                        readerReady = false
                        released.stopLoading()
                        released.postDelayed({ released.destroy() }, 200)
                        webView = null
                    })
                }
                if (progress < 100) LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth())
            }
        }
    }
    if (showSettings) AlertDialog(onDismissRequest = { showSettings = false }, title = { Text("Reading settings") }, text = {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Mobile layout", Modifier.weight(1f))
                Switch(preferences.readerMobile, { enabled -> scope.launch { container.preferencesRepository.setReader(enabled, preferences.readerFont, preferences.readerLine) } })
            }
            Text(if (preferences.readerMobile) "Mobile layout" else "Original layout")
            ChoiceMenu("Font size", listOf(16,18,20,22,24), preferences.readerFont, { it.toString() }, { font -> scope.launch { container.preferencesRepository.setReader(preferences.readerMobile, font, preferences.readerLine) } })
            ChoiceMenu("Line spacing", listOf(1.4f,1.6f,1.8f), preferences.readerLine, { it.toString() }, { line -> scope.launch { container.preferencesRepository.setReader(preferences.readerMobile, preferences.readerFont, line) } })
        }
    }, confirmButton = { TextButton(onClick = { showSettings = false }) { Text("Done") } })
    contents?.let { headings -> AlertDialog(onDismissRequest = { contents = null }, title = { Text("Contents") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            if (headings.isEmpty()) Text("No section headings available")
            headings.forEach { (id, title) -> TextButton(onClick = {
                webView?.evaluateJavascript("document.getElementById(" + org.json.JSONObject.quote(id) + ")?.scrollIntoView();", null); contents = null
            }) { Text(title) } }
        }
    }, confirmButton = { TextButton(onClick = { contents = null }) { Text("Close") } }) }
    if (showFind) AlertDialog(onDismissRequest = { showFind = false; webView?.clearMatches() }, title = { Text("Find in page") }, text = {
        Column {
            OutlinedTextField(findText, { findText = it; webView?.findAllAsync(it) }, singleLine = true, label = { Text("Find text") })
            Text(findCount)
            Row { TextButton(onClick = { webView?.findNext(false) }) { Text("Previous") }; TextButton(onClick = { webView?.findNext(true) }) { Text("Next") } }
        }
    }, confirmButton = { TextButton(onClick = { showFind = false; webView?.clearMatches() }) { Text("Done") } })

}

private fun applyHtmlTheme(view: WebView, dark: Boolean) {
    if (!isArxivHtml(Uri.parse(view.url ?: return))) return
    val background = if (dark) "#111113" else "#ffffff"
    val foreground = if (dark) "#ebebf0" else "#1c1c1e"
    val link = if (dark) "#ff918b" else "#b31b1b"
    val css = "html,body{background:$background!important;color:$foreground!important;color-scheme:${if (dark) "dark" else "light"}} " +
        ".ltx_page_main,.ltx_page_content,.ltx_document,.ltx_para,.ltx_title,.ltx_abstract,.ltx_p,.ltx_equation,.ltx_equationgroup{background:transparent!important;color:inherit!important} " +
        "a{color:$link!important} img,svg{filter:none!important} img{background:white}"
    view.evaluateJavascript("(function(){let s=document.getElementById('arxiv-app-theme');if(!s){s=document.createElement('style');s.id='arxiv-app-theme';document.head.appendChild(s);}s.textContent=${org.json.JSONObject.quote(css)};})();", null)
}
