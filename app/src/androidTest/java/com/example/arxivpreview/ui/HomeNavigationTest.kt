package com.example.arxivpreview.ui

import android.content.Intent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.room.withTransaction
import com.example.arxivpreview.ArxivApplication
import com.example.arxivpreview.MainActivity
import com.example.arxivpreview.data.local.InboxEntity
import com.example.arxivpreview.data.local.PaperEntity
import com.example.arxivpreview.data.local.SyncSourceEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertTrue

class HomeNavigationTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val container get() = (context.applicationContext as ArxivApplication).container
    private val paperId = "2610.99990"
    private var previousCategories = emptySet<String>()
    private var onlineRecommendations = true
    private var unreadCount = 0

    @Before fun setup() = runBlocking {
        val preferences = container.preferencesRepository.preferences.first()
        previousCategories = preferences.categories
        onlineRecommendations = preferences.onlineRecommendations
        container.preferencesRepository.finishOnboarding(setOf("math.AP", "math.AT"))
        container.preferencesRepository.setOnlineRecommendations(false)
        val now = System.currentTimeMillis()
        container.database.dao().upsertPaper(PaperEntity(paperId, "${paperId}v1",
            "Home unread fixture", "Abstract", listOf("Fixture Author"), now, now,
            "math.AP", listOf("math.AP", "math.AT"), "https://arxiv.org/abs/$paperId",
            "https://arxiv.org/pdf/$paperId", null, null))
        val dao = container.database.discoveryDao()
        dao.insertInbox(InboxEntity(paperId, listOf("Category: math.AP"), now))
        dao.read(paperId, false)
        dao.archive(paperId, false)
        for (code in listOf("math.AP", "math.AT", "math.AC")) {
            dao.saveSync(SyncSourceEntity("category:$code", now, 0, initialized = true, completedAt = now))
        }
        unreadCount = container.inboxRepository.unreadByCategory.first().getValue("math.AP")
    }

    @After fun cleanup() = runBlocking {
        container.database.withTransaction {
            val database = container.database.openHelper.writableDatabase
            database.execSQL("DELETE FROM inbox WHERE paperId = ?", arrayOf(paperId))
            database.execSQL("DELETE FROM papers WHERE id = ?", arrayOf(paperId))
        }
        if (previousCategories.isNotEmpty()) container.preferencesRepository.setCategories(previousCategories)
        container.preferencesRepository.setOnlineRecommendations(onlineRecommendations)
    }

    @Test fun followedCardsRestoreAndOpeningPaperUpdatesUnreadCount() {
        ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)).use { scenario ->
            waitForCard("math.AP")
            compose.onNodeWithTag("home-category-math.AT").assertIsDisplayed()
            compose.onNodeWithTag("home-category-cs.AI").assertDoesNotExist()
            assertUnread("math.AP", unreadCount)
            compose.onNodeWithTag("home-category-math.AP").performClick()
            compose.onNodeWithText("Latest · math.AP").assertIsDisplayed()
            compose.waitUntil(10000) { compose.onAllNodesWithText("Home unread fixture").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Home unread fixture").performClick()
            compose.waitUntil(5000) { runBlocking { container.database.discoveryDao().inbox(paperId)!!.isRead } }
            assertTrue(runBlocking { container.database.discoveryDao().inbox(paperId)!!.isRead })
            androidx.test.espresso.Espresso.pressBack()
            compose.onNodeWithContentDescription("Back to categories").performClick()
            waitForCard("math.AP")
            assertUnread("math.AP", unreadCount - 1)
            scenario.recreate()
            waitForCard("math.AP")
            assertUnread("math.AP", unreadCount - 1)
        }
    }

    @Test fun settingCategorySelectionUpdatesHomeCards() {
        ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)).use {
            waitForCard("math.AP")
            compose.onNodeWithTag("nav-settings").assertTextContains("Setting").performClick()
            compose.onAllNodesWithText("Profile", substring = false).assertCountEquals(0)
            compose.onNodeWithTag("settings-list").performScrollToNode(hasTestTag("category-search"))
            compose.onNodeWithTag("category-search").performTextInput("math.AC")
            compose.onNodeWithText("Commutative Algebra", substring = false).performScrollTo().performClick()
            compose.onNodeWithTag("category-search").performTextReplacement("math.AP")
            compose.onNodeWithText("Analysis of PDEs", substring = false).performScrollTo().performClick()
            compose.onNodeWithTag("save-categories").performScrollTo().performClick()
            compose.waitUntil(5000) { runBlocking { container.preferencesRepository.preferences.first().categories == setOf("math.AC", "math.AT") } }
            compose.onNodeWithTag("nav-home").performClick()
            waitForCard("math.AC")
            compose.onNodeWithTag("home-category-math.AP").assertDoesNotExist()
            compose.onNodeWithTag("home-category-math.AT").assertIsDisplayed()
            val count = runBlocking { container.inboxRepository.unreadByCategory.first()["math.AC"] ?: 0 }
            assertUnread("math.AC", count)
            compose.waitForIdle()
            val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
            java.io.File(context.filesDir, "home-category-cards.png").outputStream().use {
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
            bitmap.recycle()
        }
    }

    private fun waitForCard(code: String) {
        compose.waitUntil(10000) { compose.onAllNodesWithTag("home-category-$code").fetchSemanticsNodes().isNotEmpty() }
    }

    private fun assertUnread(code: String, count: Int) {
        compose.waitUntil(5000) {
            runCatching {
                compose.onNodeWithTag("home-category-unread-$code", useUnmergedTree = true)
                    .assertContentDescriptionEquals("$count unread articles")
                true
            }.getOrDefault(false)
        }
    }
}
