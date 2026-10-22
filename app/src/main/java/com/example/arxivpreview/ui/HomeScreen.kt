package com.example.arxivpreview.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Badge
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.arxivpreview.model.ArxivCategory

@Composable
fun HomeScreen(
    viewModel: DiscoveryViewModel,
    contentPadding: PaddingValues,
    onManageCategories: () -> Unit,
    onCategoryClick: (ArxivCategory) -> Unit,
) {
    val categories by viewModel.homeCategories.collectAsStateWithLifecycle()
    val sync by viewModel.sync.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.refreshIfStale() }

    Column(Modifier.padding(contentPadding).fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Home", Modifier.weight(1f), style = MaterialTheme.typography.headlineMedium)
            TextButton(onClick = onManageCategories) { Text("Manage") }
            IconButton(onClick = viewModel::refresh, enabled = !sync.running) {
                Icon(Icons.Default.Refresh, "Refresh Home")
            }
        }
        if (sync.running) LinearProgressIndicator(Modifier.fillMaxWidth())
        Text(
            "Followed categories", Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            "Unread articles from your synced updates",
            Modifier.padding(start = 20.dp, end = 20.dp, bottom = 12.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val message = error ?: sync.message
        if (!message.isNullOrBlank()) Text(
            message, Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (categories.isEmpty()) {
            Text("Choose your followed categories in Setting → Content preferences.", Modifier.padding(20.dp))
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(160.dp * LocalDensity.current.fontScale.coerceAtLeast(1f)),
            modifier = Modifier.weight(1f).testTag("home-categories"),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(categories, key = { it.category.code }) { item ->
                HomeCategoryCard(item, onCategoryClick)
            }
        }
    }
}

@Composable
private fun HomeCategoryCard(item: HomeCategoryUiState, onCategoryClick: (ArxivCategory) -> Unit) {
    val category = item.category
    OutlinedCard(
        onClick = { onCategoryClick(category) },
        modifier = Modifier.fillMaxWidth().testTag("home-category-${category.code}"),
    ) {
        Column(
            Modifier.fillMaxWidth().heightIn(min = 164.dp).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Text(
                    category.code, Modifier.weight(1f).padding(end = 8.dp),
                    style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
                )
                Badge(
                    modifier = Modifier.testTag("home-category-unread-${category.code}")
                        .semantics { contentDescription = "${item.unreadCount} unread articles" },
                ) { Text(item.unreadCount.toString()) }
            }
            Text(category.name, style = MaterialTheme.typography.titleMedium)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    category.group, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
            }
        }
    }
}
