package com.example.arxivpreview.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.arxivpreview.model.ArxivCategory
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun HomeScreen(
    viewModel: DiscoveryViewModel,
    contentPadding: PaddingValues,
    onManageCategories: () -> Unit,
    onCategoryClick: (ArxivCategory) -> Unit,
) {
    val categories by viewModel.homeCategories.collectAsStateWithLifecycle()
    val inbox by viewModel.inbox.collectAsStateWithLifecycle()
    val unread = inbox.count { !it.isRead && !it.archived }
    val sync by viewModel.sync.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.refreshIfStale() }
    Column(Modifier.padding(contentPadding).fillMaxSize()) {
        AppLargeTitle("Home", actions = {
            TextButton(onClick = onManageCategories) { Text("Manage") }
            IconButton(onClick = viewModel::refresh, enabled = !sync.running) { Icon(Icons.Default.Refresh, "Refresh Home") }
        })
        if (sync.running) LinearProgressIndicator(Modifier.fillMaxWidth())
        LazyVerticalGrid(
            columns = GridCells.Adaptive(160.dp * LocalDensity.current.fontScale.coerceAtLeast(1f)),
            modifier = Modifier.weight(1f).testTag("home-categories"),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(22.dp)) {
                    Column(Modifier.padding(22.dp)) {
                        Text(LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.ENGLISH)).uppercase(),
                            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Your research.\nAll in one place.", Modifier.padding(top = 10.dp, bottom = 16.dp), style = MaterialTheme.typography.headlineSmall)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(8.dp)) {
                                Text("$unread unread ${if (unread == 1) "paper" else "papers"}", Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                            }
                        }
                        Text("${categories.size} followed ${if (categories.size == 1) "category" else "categories"}", Modifier.padding(top = 10.dp),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(Modifier.padding(top = 12.dp, bottom = 2.dp)) {
                    Text("Followed categories", style = MaterialTheme.typography.titleLarge)
                    Text("The latest in your fields.", Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            val message = error ?: sync.message
            if (!message.isNullOrBlank()) item(span = { GridItemSpan(maxLineSpan) }) {
                Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (categories.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
                Column {
                    AppEmptyState("Make it yours", "Follow categories to see the latest research here.", modifier = Modifier.heightIn(min = 200.dp))
                    TextButton(onClick = onManageCategories, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Choose categories") }
                }
            }
            items(categories, key = { it.category.code }) { HomeCategoryCard(it, onCategoryClick) }
        }
    }
}

@Composable
private fun HomeCategoryCard(item: HomeCategoryUiState, onCategoryClick: (ArxivCategory) -> Unit) {
    val category = item.category
    Surface(onClick = { onCategoryClick(category) }, shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth().testTag("home-category-${category.code}")) {
        Column(Modifier.fillMaxWidth().heightIn(min = 180.dp).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                SubjectIcon(category.group)
                Spacer(Modifier.weight(1f))
                Surface(color = MaterialTheme.colorScheme.background, shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.testTag("home-category-unread-${category.code}").semantics { contentDescription = "${item.unreadCount} unread articles" }) {
                    Text(item.unreadCount.toString(), Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text(category.name, style = MaterialTheme.typography.titleMedium)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(category.code, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.outline)
            }
        }
    }
}
