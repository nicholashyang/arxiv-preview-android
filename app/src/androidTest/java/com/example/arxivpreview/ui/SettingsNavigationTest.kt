package com.example.arxivpreview.ui

import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import com.example.arxivpreview.MainActivity
import com.example.arxivpreview.data.PreferencesRepository
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test

class SettingsNavigationTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun updateIntentScrollsToAboutOnColdAndWarmLaunch() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        runBlocking { PreferencesRepository(context).finishOnboarding(setOf("cs.AI")) }
        fun intent() = Intent(context, MainActivity::class.java)
            .putExtra(MainActivity.OPEN_UPDATES, true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        ActivityScenario.launch<MainActivity>(intent()).use { scenario ->
            compose.waitUntil(10000) {
                runCatching { compose.onNodeWithText("Check for updates").assertIsDisplayed(); true }.getOrDefault(false)
            }
            compose.onNodeWithText("Automatic app updates").assertDoesNotExist()
            compose.onNodeWithTag("settings-list").performScrollToIndex(0)
            compose.onNodeWithText("APPEARANCE").assertIsDisplayed()
            compose.onNodeWithTag("settings-list").performScrollToNode(hasText("Install unknown apps"))
            compose.onNodeWithText("Install unknown apps").assertIsDisplayed()
            context.startActivity(intent())
            compose.waitUntil(10000) {
                runCatching { compose.onNodeWithText("Check for updates").assertIsDisplayed(); true }.getOrDefault(false)
            }
            scenario.recreate()
            compose.waitUntil(10000) {
                runCatching { compose.onNodeWithText("ABOUT ARXIV").assertIsDisplayed(); true }.getOrDefault(false)
            }
            compose.onNodeWithText("Check for updates").performScrollTo().assertIsDisplayed()
        }
    }

}
