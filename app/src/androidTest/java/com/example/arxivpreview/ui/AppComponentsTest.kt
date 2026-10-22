package com.example.arxivpreview.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.arxivpreview.ui.theme.ArxivPreviewTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class AppComponentsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun segmentedControlChangesTheDisplayedFilterAndSelection() {
        compose.setContent {
            ArxivPreviewTheme {
                var filter by remember { mutableStateOf("Unread") }
                Column {
                    AppSegmentedControl(listOf("Unread", "All", "Archived"), filter, { filter = it })
                    androidx.compose.material3.Text("Showing $filter")
                }
            }
        }
        compose.onNodeWithText("Unread", substring = false).assertIsSelected()
        compose.onNodeWithText("All", substring = false).performClick().assertIsSelected()
        compose.onNodeWithText("Showing All").assertIsDisplayed()
        compose.onNodeWithText("Archived", substring = false).performClick().assertIsSelected()
        compose.onNodeWithText("Showing Archived").assertIsDisplayed()
        compose.onNodeWithText("All", substring = false).assertIsNotSelected()
    }

    @Test fun searchCanSubmitClearAndRetainItsAccessibleLabel() {
        var submitted = ""
        compose.setContent {
            ArxivPreviewTheme {
                var query by remember { mutableStateOf("") }
                AppSearchField(query, { query = it }, "Find papers", onSearch = { submitted = query })
            }
        }
        compose.onNodeWithContentDescription("Find papers").performTextInput("quantum")
        compose.onNodeWithContentDescription("Find papers").performImeAction()
        compose.runOnIdle { assertEquals("quantum", submitted) }
        compose.onNodeWithContentDescription("Clear Find papers").performClick()
        compose.onNodeWithContentDescription("Find papers").assertTextContains("")
        compose.onNodeWithContentDescription("Clear Find papers").assertDoesNotExist()
    }
}
