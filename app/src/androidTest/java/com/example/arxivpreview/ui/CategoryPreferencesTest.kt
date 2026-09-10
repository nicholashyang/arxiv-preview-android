package com.example.arxivpreview.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class CategoryPreferencesTest {
    @get:Rule val compose = createComposeRule()

    @Test fun searchAndRestorationPreserveDraftAndExpansion() {
        var saved = emptySet<String>()
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            MaterialTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    CategoryPreferences(setOf("cs.AI")) { saved = it }
                }
            }
        }
        compose.onNodeWithText("Artificial Intelligence", substring = false).assertDoesNotExist()
        compose.onNodeWithTag("category-group-Computer Science").performScrollTo().performClick()
        compose.onNodeWithText("Artificial Intelligence", substring = false).assertExists()
        compose.onNodeWithTag("category-search").performScrollTo().performTextInput("stat.ML")
        compose.onNodeWithText("Machine Learning", substring = false).performScrollTo().performClick()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("category-search").assertTextContains("stat.ML")
        compose.onNodeWithTag("save-categories").performScrollTo().assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(setOf("cs.AI", "stat.ML"), saved) }
        compose.onNodeWithTag("category-search").performScrollTo().performTextClearance()
        compose.onNodeWithText("Artificial Intelligence", substring = false).assertExists()
        compose.onNodeWithTag("category-group-Computer Science").performScrollTo().performClick()
        compose.onNodeWithText("Artificial Intelligence", substring = false).assertDoesNotExist()
    }

    @Test fun cannotSaveEmptySelectionAndSearchHasEmptyState() {
        compose.setContent {
            MaterialTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    CategoryPreferences(setOf("cs.AI")) { }
                }
            }
        }
        compose.onNodeWithTag("save-categories").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("category-search").performScrollTo().performTextInput("cs.AI")
        compose.onNodeWithText("Artificial Intelligence", substring = false).performScrollTo().performClick()
        compose.onNodeWithTag("save-categories").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("category-search").performScrollTo().performTextReplacement("no-such-category")
        compose.onNodeWithText("No matching categories").assertExists()
    }
}
