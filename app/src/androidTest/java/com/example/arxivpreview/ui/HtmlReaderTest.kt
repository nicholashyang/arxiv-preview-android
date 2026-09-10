package com.example.arxivpreview.ui

import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.webkit.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class HtmlReaderTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val fixture = """<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1"></head>
        <body><h1>HTML article</h1><a href="#section" id="jump">Section</a>
        <a href="https://arxiv.org/pdf/2501.12948v1" id="pdf">PDF link</a>
        <math><msup><mi>x</mi><mn>2</mn></msup></math><img alt="figure" src="data:image/gif;base64,R0lGODlhAQABAIAAAAAAAP///ywAAAAAAQABAAACAUwAOw==">
        <div style="height:3000px">Article text</div><h2 id="section">Section two</h2><div style="height:1000px"></div></body></html>"""
    private fun launch(dark: Boolean = false) = ActivityScenario.launch<ReaderTestActivity>(
        Intent(context, ReaderTestActivity::class.java).putExtra("screen", "html").putExtra("dark", dark))
    @After fun clear() { ReaderTestActivity.htmlInterceptor = null; ReaderTestActivity.pdfRequests.set(0) }
    @Test fun articleRetainsMathImagesThemeAndScrollAfterRecreation() {
        ReaderTestActivity.htmlInterceptor = { WebResourceResponse("text/html", "UTF-8", fixture.byteInputStream()) }
        launch(dark = true).use { scenario ->
            compose.waitUntil(15000) { evaluate(scenario, "Boolean(document.getElementById('arxiv-app-theme'))") == "true" }
            assertEquals("1", evaluate(scenario, "document.querySelectorAll('math').length"))
            assertEquals("true", evaluate(scenario, "document.querySelector('img').complete"))
            assertEquals("\"rgb(17, 17, 19)\"", evaluate(scenario, "getComputedStyle(document.body).backgroundColor"))
            evaluate(scenario, "window.scrollTo(0, 700)")
            compose.waitUntil(5000) { evaluate(scenario, "window.scrollY > 600") == "true" }
            compose.waitUntil(5000) {
                var scrolled = false
                scenario.onActivity { scrolled = (findWeb(it.window.decorView)?.scrollY ?: 0) > 600 }
                scrolled
            }
            scenario.recreate()
            compose.waitUntil(15000) { evaluate(scenario, "window.scrollY > 600") == "true" }
            evaluate(scenario, "document.getElementById('jump').click()")
            compose.waitUntil(5000) { evaluate(scenario, "location.hash") == "\"#section\"" }
            evaluate(scenario, "document.getElementById('pdf').click()")
            compose.waitUntil(5000) { ReaderTestActivity.pdfRequests.get() == 1 }
        }
    }
    @Test fun missingHtmlShowsPdfChoiceAndRetryRecovers() {
        var missing = true
        ReaderTestActivity.htmlInterceptor = {
            if (missing) WebResourceResponse("text/html", "UTF-8", 404, "Not Found", emptyMap(), "Missing".byteInputStream())
            else WebResourceResponse("text/html", "UTF-8", fixture.byteInputStream())
        }
        launch().use { scenario ->
            compose.waitUntil(10000) { runCatching { compose.onNodeWithText("Unable to open HTML").assertExists(); true }.getOrDefault(false) }
            compose.onNodeWithText("Read PDF").performClick()
            assertEquals(1, ReaderTestActivity.pdfRequests.get())
            missing = false
            compose.onNodeWithText("Retry").performClick()
            compose.waitUntil(10000) { evaluate(scenario, "document.querySelector('h1').textContent") == "\"HTML article\"" }
        }
    }
    private fun evaluate(scenario: ActivityScenario<ReaderTestActivity>, script: String): String? {
        val result = AtomicReference<String?>(); val done = CountDownLatch(1)
        scenario.onActivity { activity ->
            val web = findWeb(activity.window.decorView)
            if (web == null) done.countDown() else web.evaluateJavascript(script) { result.set(it); done.countDown() }
        }
        done.await(2, TimeUnit.SECONDS); return result.get()
    }
    private fun findWeb(view: View): WebView? {
        if (view is WebView) return view
        if (view is ViewGroup) for (i in 0 until view.childCount) findWeb(view.getChildAt(i))?.let { return it }
        return null
    }
}
