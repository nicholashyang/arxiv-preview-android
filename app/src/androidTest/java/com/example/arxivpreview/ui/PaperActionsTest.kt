package com.example.arxivpreview.ui

import android.content.Intent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.arxivpreview.ArxivApplication
import com.example.arxivpreview.data.SwipeAction
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PaperActionsTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val container get() = (context.applicationContext as ArxivApplication).container
    @Before fun setup() = runBlocking {
        container.preferencesRepository.setSwipe(true, SwipeAction.MORE)
        container.preferencesRepository.setSwipe(false, SwipeAction.FAVORITE)
        container.database.dao().removeFavorite("2501.12940")
    }
    @After fun cleanup() = runBlocking {
        container.database.dao().removeFavorite("2501.12940")
        container.preferencesRepository.setSwipe(true, SwipeAction.MORE)
        container.preferencesRepository.setSwipe(false, SwipeAction.FAVORITE)
    }
    private fun card() = compose.onNode(hasContentDescription("Action fixture 0. Author. Abstract"))
    @Test fun swipeThresholdFavoriteUndoAndMoreWork() {
        ActivityScenario.launch<ReaderTestActivity>(Intent(context, ReaderTestActivity::class.java).putExtra("screen", "actions")).use {
            card().performTouchInput { swipe(Offset(width*.3f, height*.5f), Offset(width*.4f, height*.5f), 300) }
            assertNull(runBlocking { container.database.dao().favorite("2501.12940") })
            card().performTouchInput { swipe(Offset(width*.15f, height*.5f), Offset(width*.85f, height*.5f), 400) }
            compose.waitUntil(5000) { runBlocking { container.database.dao().favorite("2501.12940") != null } }
            card().performTouchInput { swipe(Offset(width*.15f, height*.5f), Offset(width*.85f, height*.5f), 400) }
            compose.waitUntil(5000) { runCatching { compose.onNodeWithText("Undo").assertExists(); true }.getOrDefault(false) }
            compose.onNodeWithText("Undo").performClick()
            compose.waitUntil(5000) { runBlocking { container.database.dao().favorite("2501.12940") != null } }
            card().performTouchInput { swipe(Offset(width*.85f, height*.5f), Offset(width*.15f, height*.5f), 400) }
            compose.onNodeWithText("Paper actions").assertExists()
            compose.onNodeWithText("Groups & tags").performClick()
            compose.onNodeWithText("Save").performClick()
            compose.waitUntil(5000) { runBlocking { container.database.dao().favorite("2501.12940") != null } }
        }
    }
    @Test fun customizedDirectionPersistsAndVerticalScrollDoesNotSave() {
        runBlocking { container.preferencesRepository.setSwipe(true, SwipeAction.FAVORITE); container.preferencesRepository.setSwipe(false, SwipeAction.NONE) }
        ActivityScenario.launch<ReaderTestActivity>(Intent(context, ReaderTestActivity::class.java).putExtra("screen", "actions")).use { scenario ->
            scenario.recreate()
            card().performTouchInput { swipe(Offset(width*.2f, height*.5f), Offset(width*.8f, height*.5f), 400) }
            assertNull(runBlocking { container.database.dao().favorite("2501.12940") })
            card().performTouchInput { swipe(Offset(width*.8f, height*.5f), Offset(width*.2f, height*.5f), 400) }
            compose.waitUntil(5000) { runBlocking { container.database.dao().favorite("2501.12940") != null } }
            runBlocking { container.database.dao().removeFavorite("2501.12940") }
            card().performTouchInput { swipeUp() }
            assertNull(runBlocking { container.database.dao().favorite("2501.12940") })
        }
    }
}
