package com.example.arxivpreview.ui

import android.content.Intent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.example.arxivpreview.ArxivApplication
import com.example.arxivpreview.MainActivity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class LatestNavigationTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun categoryRouteRestoresAndBackReturnsToFilteredDirectory() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val container = (context.applicationContext as ArxivApplication).container
        runBlocking { container.preferencesRepository.finishOnboarding(setOf("cs.AI")) }
        ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)).use { scenario ->
            compose.waitUntil(10000) { compose.onAllNodesWithTag("nav-explore").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("nav-explore").performClick()
            compose.onNodeWithTag("latest-category-search").performTextInput("hep-ph")
            compose.onNodeWithTag("latest-category-hep-ph").performClick()
            compose.onNodeWithText("Latest · hep-ph").assertIsDisplayed()
            scenario.recreate()
            compose.waitUntil(10000) { compose.onAllNodesWithText("Latest · hep-ph").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithContentDescription("Back to categories").performClick()
            compose.onNodeWithTag("latest-category-search").assertTextContains("hep-ph")
            compose.onNodeWithTag("latest-category-search").performTextReplacement("stat.ML")
            compose.onNodeWithTag("latest-category-stat.ML").performClick()
            compose.onNodeWithText("Latest · stat.ML").assertIsDisplayed()
            androidx.test.espresso.Espresso.pressBack()
            compose.onNodeWithTag("latest-category-search").assertTextContains("stat.ML")
            // Browsing all categories does not subscribe the user to them.
            assertEquals(setOf("cs.AI"), runBlocking {
                container.preferencesRepository.preferences.first().categories
            })
        }
    }
}
