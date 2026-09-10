package com.example.arxivpreview.ui

import android.os.Bundle
import android.net.Uri
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import com.example.arxivpreview.ui.theme.ArxivPreviewTheme

/** Debug-only host; deliberately absent from the published release APK. */
class ReaderTestActivity : FragmentActivity() {
    companion object {
        var htmlInterceptor: ((android.webkit.WebResourceRequest) -> android.webkit.WebResourceResponse?)? = null
        val pdfRequests = java.util.concurrent.atomic.AtomicInteger()
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ArxivPreviewTheme(if (intent.getBooleanExtra("dark", false)) com.example.arxivpreview.data.ThemeMode.DARK else com.example.arxivpreview.data.ThemeMode.LIGHT) {
                var fallback by remember { mutableStateOf(intent.getBooleanExtra("basic", false)) }
                when (intent.getStringExtra("screen")) {
                    "html" -> HtmlScreen(
                        paper = com.example.arxivpreview.model.Paper("2501.12948", "2501.12948v1", "HTML fixture", "Abstract", listOf("Author"),
                            1, 1, "cs.CL", listOf("cs.CL"), "https://arxiv.org/abs/2501.12948v1", "https://arxiv.org/pdf/2501.12948v1", null, null),
                        onBack = { finish() }, interceptRequest = htmlInterceptor, onReadPdf = { pdfRequests.incrementAndGet() },
                    )
                    "math" -> Column(Modifier.fillMaxSize()) {
                        MathText(intent.getStringExtra("text").orEmpty())
                    }
                    else -> PdfContent(Uri.parse(intent.getStringExtra("uri")), fallback, { fallback = true })
                }
            }
        }
    }
}
