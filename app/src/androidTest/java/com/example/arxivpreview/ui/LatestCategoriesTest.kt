package com.example.arxivpreview.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.arxivpreview.model.ArxivCategories
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class LatestCategoriesTest {
    @get:Rule val compose = createComposeRule()

    @Test fun allSubjectsAreBrowsableAndCategorySearchRestores() {
        var selected = ""
        val restoration = StateRestorationTester(compose)
        restoration.setContent { MaterialTheme { LatestScreen(PaddingValues()) { selected = it.code } } }
        // Reach every subject, including categories that are not in the user's interests.
        ArxivCategories.all.groupBy { it.group }.values.forEach { categories ->
            compose.onNodeWithTag("latest-categories")
                .performScrollToNode(hasTestTag("latest-category-${categories.last().code}"))
            compose.onNodeWithTag("latest-category-${categories.last().code}").assertIsDisplayed()
        }
        compose.onNodeWithTag("latest-category-search").performTextInput(" HEp-pH ")
        compose.onNodeWithTag("latest-category-hep-ph").performClick()
        compose.runOnIdle { assertEquals("hep-ph", selected) }
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("latest-category-search").assertTextContains(" HEp-pH ")
        compose.onNodeWithTag("latest-category-hep-ph").assertIsDisplayed()
        compose.onNodeWithTag("latest-category-cs.AI").assertDoesNotExist()
    }

    @Test fun searchMatchesNamesAndSubjectsAndShowsEmptyResults() {
        compose.setContent { MaterialTheme { LatestScreen(PaddingValues()) {} } }
        compose.onNodeWithTag("latest-category-search").performTextInput("Artificial Intelligence")
        compose.onNodeWithTag("latest-category-cs.AI").assertIsDisplayed()
        compose.onNodeWithTag("latest-category-search").performTextReplacement("Economics")
        compose.onNodeWithTag("latest-category-econ.EM").assertIsDisplayed()
        compose.onNodeWithTag("latest-category-search").performTextReplacement("no-such-category")
        compose.onNodeWithText("No matching categories").assertIsDisplayed()
    }
}
