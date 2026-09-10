package com.example.arxivpreview.ui

import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.webkit.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import kotlinx.coroutines.runBlocking
import com.example.arxivpreview.ArxivApplication
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
    @Before fun resetReader() = runBlocking {
        val container = (context.applicationContext as ArxivApplication).container
        container.preferencesRepository.setReader(true, 18, 1.6f)
        container.database.dao().saveProgress(com.example.arxivpreview.data.local.HtmlProgressEntity("2501.12948v1", "", 0.0, 0.0))
    }
    @Test fun mobileLayoutHandlesWideContentToolsAndColdReopen() {
        val rich = """<html><head><meta name="viewport" content="width=device-width,initial-scale=1"></head><body>
          <article class="ltx_document"><h1>Long article</h1><p>Intro text</p>
          <table style="width:1800px"><tr><td>Wide table</td></tr></table>
          <div class="ltx_equation"><math display="block"><mrow>""" + "<mi>x</mi>".repeat(100) + """</mrow></math></div>
          <img width="1600" height="200" src="data:image/gif;base64,R0lGODlhAQABAIAAAAAAAP///ywAAAAAAQABAAACAUwAOw==">
          """ + (1..35).joinToString("") { "<p id='p$it'>Paragraph $it with enough content to scroll through and restore reading position.</p>" } + """
          <h2 id="conclusion">Conclusion</h2><p>Final paragraph</p><div style="height:1000px"></div></article></body></html>"""
        ReaderTestActivity.htmlInterceptor = { WebResourceResponse("text/html", "UTF-8", rich.byteInputStream()) }
        launch().use { scenario ->
            compose.waitUntil(10000) { evaluate(scenario, "document.querySelectorAll('.arxiv-scroll').length===2 && document.documentElement.dataset.arxivReaderReady==='true'") == "true" }
            assertEquals("true", evaluate(scenario, "document.documentElement.scrollWidth<=innerWidth+1"))
            assertEquals("true", evaluate(scenario, "document.querySelector('.arxiv-scroll').scrollWidth>document.querySelector('.arxiv-scroll').clientWidth"))
            compose.onNodeWithContentDescription("Reader tools").performClick()
            compose.onNodeWithText("Reading settings").performClick()
            compose.onNodeWithText("Font size: 18").performClick(); compose.onNodeWithText("24").performClick()
            compose.onNodeWithText("Done").performClick()
            compose.waitUntil(5000) { evaluate(scenario, "getComputedStyle(document.body).fontSize") == "\"24px\"" }
            assertEquals("2", evaluate(scenario, "document.querySelectorAll('.arxiv-scroll').length"))
            compose.onNodeWithContentDescription("Reader tools").performClick(); compose.onNodeWithText("Find in page").performClick()
            compose.onNodeWithText("Find text").performTextInput("Paragraph")
            compose.onNodeWithText("Done").performClick()
            compose.onNodeWithContentDescription("Reader tools").performClick(); compose.onNodeWithText("Contents").performClick()
            compose.onNodeWithText("Conclusion").performClick()
            try { compose.waitUntil(5000) { evaluate(scenario, "Math.abs(document.getElementById('conclusion').getBoundingClientRect().top)<5") == "true" } }
            catch (e: Exception) { throw AssertionError("Jump state: " + evaluate(scenario, "JSON.stringify({y:scrollY,top:document.getElementById('conclusion').getBoundingClientRect().top,height:innerHeight,body:document.documentElement.scrollHeight,ready:document.documentElement.dataset.arxivReaderReady})"), e) }
            compose.waitUntil(5000) { runBlocking { (context.applicationContext as ArxivApplication).container.database.dao().progress("2501.12948v1")?.anchor == "conclusion" } }
        }
        launch().use { scenario ->
            try { compose.waitUntil(10000) { evaluate(scenario, "Math.abs(document.getElementById('conclusion').getBoundingClientRect().top)<20") == "true" } }
            catch (e: Exception) { throw AssertionError("Restore state: " + evaluate(scenario, "JSON.stringify({y:scrollY,top:document.getElementById('conclusion').getBoundingClientRect().top,height:innerHeight,body:document.documentElement.scrollHeight})") + runBlocking { (context.applicationContext as ArxivApplication).container.database.dao().progress("2501.12948v1") }, e) }
            compose.onNodeWithContentDescription("Reader tools").performClick(); compose.onNodeWithText("Reading settings").performClick()

            compose.onNodeWithText("Done").performClick()
        }
    }
    @After fun clear() { ReaderTestActivity.htmlInterceptor = null; ReaderTestActivity.pdfRequests.set(0) }
    @Test fun articleRetainsMathImagesThemeAndScrollAfterRecreation() {
        ReaderTestActivity.htmlInterceptor = { WebResourceResponse("text/html", "UTF-8", fixture.byteInputStream()) }
        launch(dark = true).use { scenario ->
            compose.waitUntil(15000) { evaluate(scenario, "Boolean(document.getElementById('arxiv-app-theme')) && document.documentElement.dataset.arxivReaderReady==='true'") == "true" }
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
