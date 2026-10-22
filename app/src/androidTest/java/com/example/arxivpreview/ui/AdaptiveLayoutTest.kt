package com.example.arxivpreview.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.espresso.Espresso
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import kotlin.math.roundToInt

class AdaptiveLayoutTest {
    @get:Rule val compose = createComposeRule()

    @Test fun selectionAndListInputSurviveCompactExpandedTransitions() {
        val width = mutableStateOf(400)
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                MaterialTheme {
                    var selected by rememberSaveable { mutableStateOf<String?>(null) }
                    Box(Modifier.requiredSize(width.value.dp, 700.dp)) {
                        AdaptivePaperBrowser(selected, { selected = null }, list = {
                            var query by rememberSaveable { mutableStateOf("") }
                            Column {
                                OutlinedTextField(query, { query = it }, modifier = Modifier.testTag("query"))
                                Button(onClick = { selected = "fixture" }) { Text("Open fixture") }
                            }
                        }) { id ->
                            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                                Text("Details: $id")
                                Spacer(Modifier.height(1500.dp))
                                Text("End of detail")
                            }
                        }
                    }
                }
            }
        }
        compose.onNodeWithTag("query").performTextInput("saved filter")
        Espresso.closeSoftKeyboard()
        compose.onNodeWithText("Open fixture").performClick()
        compose.onNodeWithText("Details: fixture").assertIsDisplayed()
        compose.onNodeWithTag("query").assertDoesNotExist()
        compose.onNodeWithText("End of detail").performScrollTo()
        compose.runOnIdle { width.value = 1000 }
        compose.onNodeWithTag("query").assertTextContains("saved filter")
        compose.onNodeWithText("End of detail").assertIsDisplayed()
        compose.runOnIdle { width.value = 400 }
        compose.onNodeWithText("End of detail").assertIsDisplayed()
        Espresso.pressBack()
        compose.onNodeWithTag("query").assertTextContains("saved filter")
        compose.onNodeWithText("Details: fixture").assertDoesNotExist()
    }

    @Test fun separatingBookHingeStaysBetweenThePanes() {
        var origin by mutableStateOf(Offset.Zero)
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                val x = origin.x.roundToInt()
                val fold = PaneFold(PaneBounds(x + 480, 0, x + 520, 10000), vertical = true)
                FoldLayoutProvider(listOf(fold)) {
                    Box(Modifier.requiredSize(1000.dp, 700.dp).onGloballyPositioned { origin = it.positionInWindow() }) {
                        AdaptivePaperBrowser("fixture", {}, { Text("List") }) { Text("Detail") }
                    }
                }
            }
        }
        val list = compose.onNodeWithTag("paper-list-pane").fetchSemanticsNode().boundsInRoot
        val detail = compose.onNodeWithTag("paper-detail-pane").fetchSemanticsNode().boundsInRoot
        assertTrue(list.right <= detail.left - 64f)
        assertTrue(list.width >= 320f)
        assertTrue(detail.width >= 320f)
    }

    @Test fun tabletopHingeStaysBetweenTopAndBottomPanes() {
        var origin by mutableStateOf(Offset.Zero)
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                val y = origin.y.roundToInt()
                val fold = PaneFold(PaneBounds(0, y + 330, 10000, y + 350), vertical = false)
                FoldLayoutProvider(listOf(fold)) {
                    Box(Modifier.requiredSize(800.dp, 700.dp).onGloballyPositioned { origin = it.positionInWindow() }) {
                        AdaptivePaperBrowser("fixture", {}, { Text("List") }) { Text("Detail") }
                    }
                }
            }
        }
        val list = compose.onNodeWithTag("paper-list-pane").fetchSemanticsNode().boundsInRoot
        val detail = compose.onNodeWithTag("paper-detail-pane").fetchSemanticsNode().boundsInRoot
        assertTrue(list.bottom <= detail.top - 44f)
        assertTrue(list.height >= 240f)
        assertTrue(detail.height >= 240f)
    }

    @Test fun readerUsesOneSafeRegionBesideAnOccludingHinge() {
        var origin by mutableStateOf(Offset.Zero)
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                val x = origin.x.roundToInt()
                FoldLayoutProvider(listOf(PaneFold(PaneBounds(x + 400, 0, x + 430, 10000), true))) {
                    Box(Modifier.requiredSize(1000.dp, 700.dp).onGloballyPositioned { origin = it.positionInWindow() }) {
                        AdaptiveSinglePane(maxContentWidth = androidx.compose.ui.unit.Dp.Infinity) {
                            Box(Modifier.fillMaxSize().testTag("reader"))
                        }
                    }
                }
            }
        }
        val reader = compose.onNodeWithTag("reader").fetchSemanticsNode().boundsInRoot
        assertTrue(reader.width == 558f)
    }
}
