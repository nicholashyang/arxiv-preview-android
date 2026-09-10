package com.example.arxivpreview.ui

import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.core.content.FileProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.arxivpreview.data.PreferencesRepository
import com.example.arxivpreview.data.ThemeMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class ReaderRegressionTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private fun pdfIntent(basic: Boolean): Intent {
        val file = File(context.cacheDir, "pdfs/reader-regression.pdf").apply { parentFile!!.mkdirs() }
        instrumentation.context.assets.open("reader-fixture.pdf").use { input -> file.outputStream().use { input.copyTo(it) } }
        return Intent(context, ReaderTestActivity::class.java).putExtra("basic", basic)
            .putExtra("uri", FileProvider.getUriForFile(context, "${context.packageName}.files", file).toString())
    }
    @Test fun basicReaderReadsNavigatesAndRestoresPage() {
        ActivityScenario.launch<ReaderTestActivity>(pdfIntent(true)).use { scenario ->
            compose.waitUntil(10000) { runCatching { compose.onNodeWithText("1 / 2").assertExists(); true }.getOrDefault(false) }
            compose.onNodeWithText("Next").performClick()
            compose.onNodeWithText("2 / 2").assertExists()
            scenario.recreate()
            compose.waitUntil(10000) { runCatching { compose.onNodeWithText("2 / 2").assertExists(); true }.getOrDefault(false) }
            compose.onNodeWithText("Previous").performClick()
            compose.onNodeWithText("1 / 2").assertExists()
        }
    }
    @Test fun jetpackReaderAttachesBeforeSettingDocumentAndSurvivesRecreation() {
        repeat(2) {
            ActivityScenario.launch<ReaderTestActivity>(pdfIntent(false)).use { scenario ->
                compose.waitUntil(15000) {
                    var loaded = false
                    scenario.onActivity { activity ->
                        loaded = activity.supportFragmentManager.fragments.filterIsInstance<ReaderPdfFragment>()
                            .any { it.isAdded && it.loadedPageCount == 2 }
                    }
                    loaded
                }
                scenario.recreate()
                compose.waitUntil(15000) {
                    var attached = false
                    scenario.onActivity { a -> attached = a.supportFragmentManager.fragments.filterIsInstance<ReaderPdfFragment>().any { it.isAdded && it.loadedPageCount == 2 } }
                    attached
                }
            }
        }
    }
    @Test fun bundledMathRendersOfflineAndNeverExecutesPaperMarkup() {
        val text = "Energy ${'$'}E=mc^2${'$'} and \\(\\frac{1}{2}\\) <img src=x onerror=\"window.injected=true\">"
        val intent = Intent(context, ReaderTestActivity::class.java).putExtra("screen", "math").putExtra("text", text)
        ActivityScenario.launch<ReaderTestActivity>(intent).use { scenario ->
            compose.waitUntil(15000) { evaluate(scenario, "document.querySelectorAll('.katex').length") == "2" }
            assertEquals("true", evaluate(scenario, "document.getElementById('text').textContent.includes('<img')"))
            assertEquals("false", evaluate(scenario, "Boolean(window.injected)"))
            compose.waitUntil(10000) { evaluate(scenario, "document.fonts.check('16px KaTeX_Main')") == "true" }
            assertEquals("true", evaluate(scenario, "document.getElementById('text').getBoundingClientRect().height > 20"))
        }
    }
    @Test fun invalidMathRetainsReadableText() {
        val intent = Intent(context, ReaderTestActivity::class.java).putExtra("screen", "math").putExtra("text", "Before ${'$'}\\unknowncommand{x}${'$'} after")
        ActivityScenario.launch<ReaderTestActivity>(intent).use { scenario ->
            compose.waitUntil(15000) { evaluate(scenario, "document.body.textContent.includes('unknowncommand')") == "true" }
            assertEquals("true", evaluate(scenario, "document.getElementById('text').textContent.includes('after')"))
        }
    }
    @Test fun themeSelectionPersistsAcrossRepositoryInstances() = runBlocking {
        val repo = PreferencesRepository(context)
        val original = repo.preferences.first().themeMode
        try {
            ThemeMode.entries.forEach { mode ->
                repo.setThemeMode(mode)
                assertEquals(mode, PreferencesRepository(context).preferences.first().themeMode)
            }
        } finally { repo.setThemeMode(original) }
    }
    private fun evaluate(scenario: ActivityScenario<ReaderTestActivity>, script: String): String? {
        val result = AtomicReference<String?>()
        val done = CountDownLatch(1)
        scenario.onActivity { activity ->
            val web = findWeb(activity.window.decorView)
            if (web == null) done.countDown() else web.evaluateJavascript(script) { result.set(it); done.countDown() }
        }
        done.await(3, TimeUnit.SECONDS)
        return result.get()
    }
    private fun findWeb(view: View): WebView? {
        if (view is WebView) return view
        if (view is ViewGroup) for (i in 0 until view.childCount) findWeb(view.getChildAt(i))?.let { return it }
        return null
    }
}
