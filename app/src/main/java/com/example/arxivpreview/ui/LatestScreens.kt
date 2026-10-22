package com.example.arxivpreview.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Surface
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.arxivpreview.model.ArxivCategories
import com.example.arxivpreview.model.ArxivCategory
import com.example.arxivpreview.model.Paper

@Composable
fun LatestScreen(contentPadding: PaddingValues, onCategoryClick: (ArxivCategory) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val groups = remember(query) {
        val term = query.trim()
        ArxivCategories.all.filter {
            term.isEmpty() || it.code.contains(term, true) || it.name.contains(term, true) || it.group.contains(term, true)
        }.groupBy(ArxivCategory::group)
    }
    Column(Modifier.padding(contentPadding).fillMaxSize()) {
        Text("Browse the latest preprints by category", Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 12.dp),
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        AppSearchField(query, { query = it }, "Find a category",
            Modifier.padding(horizontal = 20.dp).testTag("latest-category-search"))
        LazyColumn(Modifier.weight(1f).testTag("latest-categories"), contentPadding = PaddingValues(bottom = 16.dp)) {
            if (groups.isEmpty()) item { Text("No matching categories", Modifier.padding(20.dp)) }
            groups.forEach { (group, categories) ->
                item(key = "group-$group", contentType = "heading") {
                    Text(
                        group.uppercase(), Modifier.padding(start = 36.dp, end = 20.dp, top = 24.dp, bottom = 8.dp).semantics { heading() },
                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                itemsIndexed(categories, key = { _, category -> category.code }, contentType = { _, _ -> "category" }) { index, category ->
                    Surface(Modifier.padding(horizontal = 20.dp).clip(RoundedCornerShape(
                        topStart = if (index == 0) 14.dp else 0.dp, topEnd = if (index == 0) 14.dp else 0.dp,
                        bottomStart = if (index == categories.lastIndex) 14.dp else 0.dp, bottomEnd = if (index == categories.lastIndex) 14.dp else 0.dp))) {
                        Column {
                            ListItem(
                                headlineContent = { Text(category.name) }, supportingContent = { Text(category.code) },
                                trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.outline) },
                                modifier = Modifier.testTag("latest-category-${category.code}").clickable { onCategoryClick(category) },
                            )
                            if (index != categories.lastIndex) HorizontalDivider(Modifier.padding(start = 16.dp), thickness = .5.dp)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryLatestScreen(
    viewModel: LatestViewModel,
    category: ArxivCategory,
    onBack: () -> Unit,
    onPaperClick: (Paper) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            AppNavigationBar(
                title = "Latest · ${category.code}", onBack = onBack, backDescription = "Back to categories",
                actions = {
                    IconButton(onClick = viewModel::refresh, enabled = !state.refreshing) {
                        Icon(Icons.Default.Refresh, "Refresh category")
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Text(category.name, Modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 8.dp), style = MaterialTheme.typography.headlineSmall)
            Text(
                "Latest preprints · Newest submissions first",
                Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (state.error != null) {
                TextButton(onClick = viewModel::retry, modifier = Modifier.padding(horizontal = 12.dp)) { Text("Retry") }
            }
            PullToRefreshBox(
                isRefreshing = state.refreshing, onRefresh = viewModel::refresh,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) {
                PaperList(
                    papers = state.papers, loading = state.refreshing, loadingMore = state.loadingMore,
                    hasMore = state.hasMore, error = state.error,
                    onPaperClick = onPaperClick, onLoadMore = viewModel::loadMore,
                )
            }
        }
    }
}
