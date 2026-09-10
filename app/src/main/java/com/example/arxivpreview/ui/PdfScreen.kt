package com.example.arxivpreview.ui

import android.annotation.SuppressLint
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.content.res.Configuration
import androidx.appcompat.view.ContextThemeWrapper
import androidx.core.os.bundleOf
import com.example.arxivpreview.R
import com.example.arxivpreview.ui.theme.LocalDarkAppearance
import android.os.ext.SdkExtensions
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.fragment.compose.AndroidFragment
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.pdf.viewer.fragment.PdfViewerFragment

internal fun supportsPdfViewer(sdk: Int, extension: Int): Boolean = sdk >= 31 && extension >= 13

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfScreen(viewModel: PdfViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var basic by rememberSaveable { mutableStateOf(false) }
    Scaffold(topBar = {
        TopAppBar(title = { Text("PDF") }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
        }, actions = {
            if (!basic && state.fileUri != null) TextButton(onClick = { basic = true }) { Text("Basic reader") }
            IconButton(onClick = { viewModel.retry(forceDownload = true) }, enabled = !state.loading) {
                Icon(Icons.Default.Refresh, "Download PDF again")
            }
            state.pdfUrl?.let { url -> IconButton(onClick = { openExternal(context, url) }) {
                Icon(Icons.Default.OpenInBrowser, "Open in browser")
            } }
        })
    }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                state.fileUri != null -> PdfContent(state.fileUri!!, basic, { basic = true })
                else -> Column(Modifier.align(Alignment.Center).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Unable to open PDF", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(12.dp)); Text(state.error ?: "Please try again.")
                    Button(onClick = { viewModel.retry() }, modifier = Modifier.padding(top = 16.dp)) { Text("Retry") }
                }
            }
        }
    }
}

@SuppressLint("NewApi")
@Composable
internal fun PdfContent(uri: Uri, basic: Boolean, onFallback: () -> Unit) {
    if (basic || !supportsPdfViewer(Build.VERSION.SDK_INT, SdkExtensions.getExtensionVersion(Build.VERSION_CODES.S))) {
        BasicPdfReader(uri)
    } else {
        AndroidFragment<ReaderPdfFragment>(modifier = Modifier.fillMaxSize(), arguments = bundleOf("dark" to LocalDarkAppearance.current)) { fragment ->
            fragment.onFailure = onFallback
            if (fragment.documentUri != uri) fragment.documentUri = uri
        }
    }
}

/** A public no-arg fragment is required for framework state restoration. */
@SuppressLint("NewApi")
class ReaderPdfFragment : PdfViewerFragment() {
    var onFailure: (() -> Unit)? = null
    var loadedPageCount: Int = 0
        private set
    override fun onLoadDocumentSuccess(document: androidx.pdf.PdfDocument) {
        loadedPageCount = document.pageCount
    }
    override fun onGetLayoutInflater(savedInstanceState: Bundle?): LayoutInflater {
        val inflater = super.onGetLayoutInflater(savedInstanceState)
        val config = Configuration(requireContext().resources.configuration)
        config.uiMode = (config.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
            if (arguments?.getBoolean("dark") == true) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
        val themed = ContextThemeWrapper(requireContext().createConfigurationContext(config), R.style.Theme_ArxivPreview)
        return inflater.cloneInContext(themed)
    }
    override fun onLoadDocumentError(error: Throwable) {
        view?.post { onFailure?.invoke() }
    }
    override fun onDestroyView() {
        onFailure = null
        super.onDestroyView()
    }
}
