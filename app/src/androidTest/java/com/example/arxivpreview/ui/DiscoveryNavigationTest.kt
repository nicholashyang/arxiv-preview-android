package com.example.arxivpreview.ui

import android.content.Intent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.example.arxivpreview.ArxivApplication
import com.example.arxivpreview.MainActivity
import com.example.arxivpreview.data.local.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import java.security.MessageDigest

class DiscoveryNavigationTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val container get() = (context.applicationContext as ArxivApplication).container
    private val id = "2609.09990"
    private fun cache(path: String, params: Map<String,String>, payload: String) = runBlocking {
        val raw = path + params.toSortedMap().toString()
        val key = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray()).joinToString("") { "%02x".format(it) }
        container.database.discoveryDao().cache(SemanticCacheEntity(key, payload, System.currentTimeMillis()))
    }
    @Before fun setup() = runBlocking {
        container.preferencesRepository.finishOnboarding(setOf("hep-ph"))
        container.preferencesRepository.setOnlineRecommendations(false)
        val now = System.currentTimeMillis()
        val paper = PaperEntity(id, "${id}v1", "Correlations in proton structure", "We study the evolution of double parton distributions and transverse correlations in proton structure.", listOf("Alex Doe"), now, now, "hep-ph", listOf("hep-ph"), "https://arxiv.org/abs/$id", "https://arxiv.org/pdf/$id", null, null)
        container.database.dao().upsertPaper(paper)
        container.database.dao().removeFavorite(id)
        val dao = container.database.discoveryDao()
        dao.restore(id)
        dao.candidates(listOf(CandidateEntity(id, "category:hep-ph", now)))
        dao.saveSync(SyncSourceEntity("category:hep-ph", now, 0, initialized = true, completedAt = now))
        dao.insertInbox(InboxEntity(id, listOf("Category: hep-ph"), now))
        dao.archive(id, false); dao.read(id, false)
        cache("graph/v1/paper/ARXIV:$id", mapOf("fields" to "authors"), """{"authors":[{"authorId":"99123","name":"Alex Doe"},{"authorId":"99456","name":"Alex Doe"}]}""")
        cache("graph/v1/author/99123", mapOf("fields" to "name,affiliations,paperCount,citationCount,hIndex"), """{"authorId":"99123","name":"Alex Doe","affiliations":["Research Institute"],"paperCount":12,"citationCount":45,"hIndex":3}""")
        cache("graph/v1/author/99123/papers", mapOf("offset" to "0", "limit" to "50", "fields" to "title,externalIds,year,publicationDate,url"), """{"data":[{"paperId":"fixture","title":"Correlations in proton structure","externalIds":{"ArXiv":"$id"},"year":2026}]}""")
    }
    @After fun cleanup() = runBlocking {
        container.database.discoveryDao().unfollow("99123")
        container.database.discoveryDao().restore(id)
        container.database.discoveryDao().archive(id, false)
        container.database.discoveryDao().read(id, false)
        container.preferencesRepository.setOnlineRecommendations(true)
    }
    @Test fun fiveTabsAuthorIdentitySelectionAndFollowSurviveRecreation() {
        ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)).use { scenario ->
            compose.waitUntil(10000) { compose.onAllNodesWithTag("home-category-hep-ph").fetchSemanticsNodes().isNotEmpty() }
            for (route in listOf("home", "explore", "favorites", "inbox", "settings")) compose.onNodeWithTag("nav-$route").assertExists()
            compose.onNodeWithTag("nav-inbox").performClick()
            compose.onNodeWithText("All", substring = false).performClick()
            compose.waitUntil(10000) { compose.onAllNodesWithText("Alex Doe").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Alex Doe").performClick()
            compose.waitUntil(10000) { compose.onAllNodesWithText("View profile · 99123").fetchSemanticsNodes().isNotEmpty() }
            // Duplicate names require an explicit profile choice.
            compose.onNodeWithText("View profile · 99123").performClick()
            compose.waitUntil(10000) { runCatching { compose.onNodeWithText("Follow author").assertIsDisplayed(); true }.getOrDefault(false) }
            compose.onNodeWithText("Research Institute").assertIsDisplayed()
            compose.onNodeWithText("Follow author").performClick()
            compose.waitUntil(5000) { runBlocking { container.database.discoveryDao().follows().any { it.authorId == "99123" } } }
            scenario.recreate()
            compose.waitUntil(10000) { runCatching { compose.onNodeWithText("Following · Unfollow").assertIsDisplayed(); true }.getOrDefault(false) }
        }
    }
    private fun capture(name: String) {
        compose.waitForIdle()
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        val directory = java.io.File(context.filesDir, "discovery-qa").apply { mkdirs() }
        java.io.File(directory, "$name.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("screencap -p /sdcard/Download/arxiv-discovery-$name.png").use { descriptor ->
            android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes() }
        }
    }
    @Test fun captureLightDarkAndLargeTypeScreens() {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        fun font(scale: String) { automation.executeShellCommand("settings put system font_scale $scale").close() }
        try {
            for ((name, theme, scale) in listOf(Triple("light", com.example.arxivpreview.data.ThemeMode.LIGHT, "1.0"), Triple("dark", com.example.arxivpreview.data.ThemeMode.DARK, "1.0"), Triple("large", com.example.arxivpreview.data.ThemeMode.LIGHT, "1.3"))) {
                font(scale)
                runBlocking { container.preferencesRepository.setThemeMode(theme) }
                ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)).use {
                    compose.waitUntil(10000) { compose.onAllNodesWithTag("home-category-hep-ph").fetchSemanticsNodes().isNotEmpty() }
                    capture("home-$name")
                    compose.onNodeWithTag("nav-inbox").performClick()
                    compose.waitUntil(5000) { runCatching { compose.onNodeWithText("Read all").assertIsDisplayed(); true }.getOrDefault(false) }
                    capture("inbox-$name")
                    compose.onNodeWithTag("nav-settings").performClick()
                    compose.onNodeWithText("Research", substring = false).performClick()
                    compose.waitUntil(5000) { runCatching { compose.onNodeWithText("Topics & keywords").assertIsDisplayed(); true }.getOrDefault(false) }
                    capture("research-$name")
                }
            }
        } finally { font("1.0"); runBlocking { container.preferencesRepository.setThemeMode(com.example.arxivpreview.data.ThemeMode.SYSTEM) } }
    }
    @Test fun inboxReadArchiveRestoreAndNotificationNavigation() {
        ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java).putExtra(MainActivity.OPEN_INBOX, true)).use {
            compose.waitUntil(10000) { runCatching { compose.onNodeWithText("Mark read").assertIsDisplayed(); true }.getOrDefault(false) }
            compose.onNodeWithText("Mark read").performClick()
            compose.onNodeWithText("All", substring = false).performClick()
            compose.waitUntil(5000) { runCatching { compose.onNodeWithText("Mark unread").assertIsDisplayed(); true }.getOrDefault(false) }
            compose.onNodeWithText("Archive", substring = false).performClick()
            compose.onNodeWithText("Archived", substring = false).performClick()
            compose.waitUntil(5000) { runCatching { compose.onNodeWithText("Restore").assertIsDisplayed(); true }.getOrDefault(false) }
            compose.onNodeWithText("Restore").performClick()
            compose.waitUntil(5000) { !runBlocking { container.database.discoveryDao().inbox(id)!!.archived } }
            assertFalse(runBlocking { container.database.discoveryDao().inbox(id)!!.archived })
            assertTrue(runBlocking { container.database.discoveryDao().inbox(id)!!.isRead })
            compose.onNodeWithText("All", substring = false).performClick()
            compose.onNodeWithText("Correlations in proton structure").performClick()
            compose.waitUntil(5000) { compose.onAllNodesWithText("Paper details").fetchSemanticsNodes().isNotEmpty() }
            context.startActivity(Intent(context, MainActivity::class.java)
                .putExtra(MainActivity.OPEN_INBOX, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP))
            compose.waitUntil(5000) { compose.onAllNodesWithText("Paper details").fetchSemanticsNodes().isEmpty() }
            compose.onNodeWithText("Read all").assertIsDisplayed()
        }
    }
}
