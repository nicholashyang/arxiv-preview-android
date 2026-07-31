package com.example.arxivpreview.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.ext.SdkExtensions
import android.view.View
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.core.net.toUri
import androidx.fragment.app.FragmentContainerView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.pdf.viewer.fragment.PdfViewerFragment

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfScreen(viewModel: PdfViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("PDF") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    state.pdfUrl?.let { url ->
                        IconButton(onClick = { openBrowser(context, url) }) {
                            Icon(Icons.Default.OpenInBrowser, "Open in browser")
                        }
                    }
                },
            )
        },
    ) { padding ->
        when {
            state.loading -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }
            state.fileUri != null && supportsPdfViewer() -> Box(
                Modifier.fillMaxSize().padding(padding),
            ) {
                EmbeddedPdfViewer(state.fileUri!!)
            }
            else -> Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    state.error
                        ?: "The built-in PDF viewer is unavailable on this system extension.",
                )
                state.pdfUrl?.let { url ->
                    Button(onClick = { openBrowser(context, url) }, modifier = Modifier.padding(top = 16.dp)) {
                        Icon(Icons.Default.OpenInBrowser, null)
                        Text(" Open in browser")
                    }
                }
            }
        }
    }
}

@Composable
private fun EmbeddedPdfViewer(uri: Uri) {
    val activity = LocalActivity.current as FragmentActivity
    val containerId = androidx.compose.runtime.remember { View.generateViewId() }
    androidx.compose.ui.viewinterop.AndroidView(
        factory = { context ->
            FragmentContainerView(context).apply { id = containerId }
        },
        modifier = Modifier.fillMaxSize(),
    )
    DisposableEffect(uri, containerId) {
        val tag = "pdf-viewer-$containerId"
        if (activity.supportFragmentManager.findFragmentByTag(tag) == null) {
            val fragment = PdfViewerFragment().apply { documentUri = uri }
            activity.supportFragmentManager.beginTransaction()
                .replace(containerId, fragment, tag)
                .commitNowAllowingStateLoss()
        }
        onDispose {
            activity.supportFragmentManager.findFragmentByTag(tag)?.let { fragment ->
                activity.supportFragmentManager.beginTransaction()
                    .remove(fragment)
                    .commitAllowingStateLoss()
            }
        }
    }
}

private fun supportsPdfViewer(): Boolean =
    Build.VERSION.SDK_INT > Build.VERSION_CODES.S ||
        SdkExtensions.getExtensionVersion(Build.VERSION_CODES.S) >= 13

private fun openBrowser(context: android.content.Context, url: String) {
    context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
}
