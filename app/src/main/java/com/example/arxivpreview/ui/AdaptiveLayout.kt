package com.example.arxivpreview.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlin.math.roundToInt

private val LocalFolds = staticCompositionLocalOf<List<PaneFold>> { emptyList() }
internal val LocalSelectedPaperId = staticCompositionLocalOf<String?> { null }

@Composable
internal fun FoldAwareWindow(content: @Composable () -> Unit) {
    val activity = LocalActivity.current
    val info = remember(activity) {
        activity?.let { WindowInfoTracker.getOrCreate(it).windowLayoutInfo(it).map { info -> info.displayFeatures } }
            ?: flowOf(emptyList())
    }
    val features by info.collectAsStateWithLifecycle(emptyList())
    val folds = features.filterIsInstance<FoldingFeature>()
        .filter { it.isSeparating || it.occlusionType == FoldingFeature.OcclusionType.FULL }
        .map { feature ->
            val bounds = feature.bounds
            PaneFold(PaneBounds(bounds.left, bounds.top, bounds.right, bounds.bottom), feature.orientation == FoldingFeature.Orientation.VERTICAL)
        }
    CompositionLocalProvider(LocalFolds provides folds, content = content)
}

/** Also used by previews/tests to supply a posture without an OEM extension. */
@Composable
internal fun FoldLayoutProvider(folds: List<PaneFold>, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalFolds provides folds, content = content)
}

@Composable
internal fun AdaptiveSinglePane(
    modifier: Modifier = Modifier,
    maxContentWidth: Dp = 840.dp,
    content: @Composable () -> Unit,
) {
    AdaptivePaneLayout(modifier, maxContentWidth = maxContentWidth, primary = content)
}

@Composable
internal fun AdaptivePaperBrowser(
    selectedPaperId: String?,
    onClearSelection: () -> Unit,
    list: @Composable () -> Unit,
    detail: @Composable (String) -> Unit,
) {
    val savedList = rememberSaveableStateHolder()
    BackHandler(enabled = selectedPaperId != null, onBack = onClearSelection)
    AdaptivePaneLayout(
        allowTwoPanes = true,
        showSecondary = selectedPaperId != null,
        primary = { savedList.SaveableStateProvider("paper-list", list) },
        secondary = {
            if (selectedPaperId != null) detail(selectedPaperId)
            else Box(Modifier.fillMaxSize().testTag("paper-detail-placeholder"), contentAlignment = Alignment.Center) {
                Column(Modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Description, null, tint = MaterialTheme.colorScheme.primary)
                    Text("Select a paper", style = MaterialTheme.typography.titleLarge)
                    Text("Its details will appear here.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
    )
}

@Composable
private fun AdaptivePaneLayout(
    modifier: Modifier = Modifier,
    maxContentWidth: Dp = Dp.Infinity,
    allowTwoPanes: Boolean = false,
    showSecondary: Boolean = false,
    primary: @Composable () -> Unit,
    secondary: @Composable () -> Unit = {},
) {
    val folds = LocalFolds.current
    var origin by remember { mutableStateOf(Offset.Zero) }
    val density = LocalDensity.current
    BoxWithConstraints(modifier.fillMaxSize().onGloballyPositioned { origin = it.positionInWindow() }) {
        val localFolds = folds.map { fold ->
            val b = fold.bounds
            val x = origin.x.roundToInt()
            val y = origin.y.roundToInt()
            fold.copy(bounds = PaneBounds(b.left - x, b.top - y, b.right - x, b.bottom - y))
        }
        val layout = calculatePaneLayout(
            constraints.maxWidth, constraints.maxHeight, density.density,
            localFolds.firstOrNull { fold ->
                val b = fold.bounds
                if (fold.vertical) b.left < constraints.maxWidth && b.right > 0
                else b.top < constraints.maxHeight && b.bottom > 0
            },
            allowTwoPanes,
        )
        val dual = layout.secondary != null
        val displayPrimary = !showSecondary || dual
        val displaySecondary = showSecondary || dual
        Layout(
            content = {
                if (displayPrimary) Box(Modifier.fillMaxSize().then(if (allowTwoPanes) Modifier.testTag("paper-list-pane") else Modifier)) { primary() }
                if (displaySecondary) Box(Modifier.fillMaxSize().testTag("paper-detail-pane")) { secondary() }
            },
            modifier = Modifier.fillMaxSize(),
        ) { measurables, constraints ->
            val bounds = buildList {
                if (displayPrimary) add(layout.primary)
                if (displaySecondary) add(layout.secondary ?: layout.primary)
            }
            val maxWidthPx = if (maxContentWidth == Dp.Infinity) Int.MAX_VALUE else maxContentWidth.roundToPx()
            val placeables = measurables.mapIndexed { index, measurable ->
                val b = bounds[index]
                measurable.measure(Constraints.fixed(b.width.coerceAtMost(maxWidthPx), b.height))
            }
            layout(constraints.maxWidth, constraints.maxHeight) {
                placeables.forEachIndexed { index, placeable ->
                    val b = bounds[index]
                    placeable.place(b.left + (b.width - placeable.width) / 2, b.top)
                }
            }
        }
    }
}
