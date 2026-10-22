package com.example.arxivpreview.ui

import android.content.Intent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso
import androidx.test.platform.app.InstrumentationRegistry
import com.example.arxivpreview.ArxivApplication
import com.example.arxivpreview.MainActivity
import com.example.arxivpreview.model.Paper
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test

class FoldableNavigationTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun selectedPaperAndLibraryFilterSurviveRecreationAndBack() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val container = (context.applicationContext as ArxivApplication).container
        val paper = Paper("2610.99881", "2610.99881v1", "Foldable navigation fixture", "A locally cached abstract for foldable testing.",
            listOf("Fixture Author"), 1, 1, "cs.AI", listOf("cs.AI"), "https://arxiv.org/abs/2610.99881v1", "https://arxiv.org/pdf/2610.99881v1", null, null)
        val original = runBlocking { container.preferencesRepository.preferences.first() }
        runBlocking {
            container.preferencesRepository.finishOnboarding(setOf("cs.AI"))
            container.favoriteRepository.setFavorite(paper, true)
        }
        try {
            ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)).use { scenario ->
                compose.waitUntil(10000) { compose.onAllNodesWithTag("nav-favorites").fetchSemanticsNodes().isNotEmpty() }
                compose.onNodeWithTag("nav-favorites").performClick()
                compose.onNodeWithText("Search favorites").performTextInput("Foldable navigation")
                Espresso.closeSoftKeyboard()
                compose.onNodeWithText(paper.title).performClick()
                waitForDetails()
                scenario.recreate()
                waitForDetails()
                compose.onNodeWithTag("paper-detail-pane").assertIsDisplayed()
                val wide = compose.onAllNodesWithTag("navigation-rail").fetchSemanticsNodes().isNotEmpty()
                if (wide) {
                    compose.onNodeWithTag("paper-list-pane").assertIsDisplayed()
                    compose.onNodeWithText("Search favorites").assertTextContains("Foldable navigation")
                } else {
                    compose.onNodeWithTag("paper-list-pane").assertDoesNotExist()
                    compose.onNodeWithTag("bottom-navigation").assertDoesNotExist()
                }
                val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
                java.io.File(context.filesDir, "foldable-library-${if (wide) "expanded" else "compact"}.png").outputStream().use {
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                }
                bitmap.recycle()
                Espresso.pressBack()
                compose.onNodeWithText("Search favorites").assertTextContains("Foldable navigation")
                compose.onNodeWithText("Paper details").assertDoesNotExist()
            }
        } finally {
            runBlocking {
                container.favoriteRepository.setFavorite(paper, false)
                container.database.openHelper.writableDatabase.execSQL("DELETE FROM papers WHERE id = ?", arrayOf(paper.id))
                if (original.categories.isNotEmpty()) container.preferencesRepository.setCategories(original.categories)
            }
        }
    }

    private fun waitForDetails() {
        compose.waitUntil(10000) { compose.onAllNodesWithText("Paper details").fetchSemanticsNodes().isNotEmpty() }
    }
}
