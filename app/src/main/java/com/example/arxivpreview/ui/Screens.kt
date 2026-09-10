package com.example.arxivpreview.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Folder
import androidx.compose.ui.platform.LocalContext
import com.example.arxivpreview.data.SwipeAction
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.arxivpreview.model.ArxivCategories
import com.example.arxivpreview.model.ArxivCategory
import com.example.arxivpreview.model.Paper
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun OnboardingScreen(viewModel: OnboardingViewModel) {
    val selected by viewModel.selected.collectAsStateWithLifecycle()
    var search by remember { mutableStateOf("") }
    Scaffold(
        topBar = {
            Column(
                Modifier
                    .statusBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
            ) {
                Text("Welcome to arXiV", style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Choose at least one category for your daily feed. You can change this later.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        bottomBar = {
            Button(
                onClick = viewModel::finish,
                enabled = selected.isNotEmpty(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .navigationBarsPadding()
                    .testTag("continue"),
            ) {
                Text("Continue with ${selected.size} selected")
            }
        },
    ) { padding ->
        CategoryPicker(
            selected = selected,
            search = search,
            onSearchChange = { search = it },
            onToggle = viewModel::toggle,
            modifier = Modifier.padding(padding),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LatestScreen(
    viewModel: LatestViewModel,
    contentPadding: PaddingValues,
    onPaperClick: (Paper) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Column(Modifier.padding(contentPadding)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Latest", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "New submissions in your categories",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = viewModel::refresh) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh")
            }
        }
        PullToRefreshBox(
            isRefreshing = state.refreshing,
            onRefresh = viewModel::refresh,
            modifier = Modifier.fillMaxSize(),
        ) {
            PaperList(
                papers = state.papers,
                loading = state.refreshing,
                loadingMore = state.loadingMore,
                hasMore = state.hasMore,
                error = state.error,
                onPaperClick = onPaperClick,
                onLoadMore = viewModel::loadMore,
            )
        }
    }
}

@Composable
fun FavoritesScreen(
    viewModel: FavoritesViewModel,
    contentPadding: PaddingValues,
    onPaperClick: (Paper) -> Unit,
) {
    val papers by viewModel.papers.collectAsStateWithLifecycle()
    val actions = LocalPaperActions.current
    var query by remember { mutableStateOf("") }
    var group by remember { mutableStateOf<Long?>(null) } // null: all, -1: ungrouped
    var tags by remember { mutableStateOf(setOf<Long>()) }
    LaunchedEffect(actions.groups, actions.tags) {
        if (group != null && group != -1L && actions.groups.none { it.id == group }) group = null
        tags = tags.intersect(actions.tags.map { it.id }.toSet())
    }
    val filtered = papers.filter { paper ->
        val record = actions.records.firstOrNull { it.paperId == paper.id }
        val paperTags = actions.relations.filter { it.paperId == paper.id }.map { it.tagId }.toSet()
        (group == null || (if (group == -1L) record?.groupId == null else record?.groupId == group)) &&
            paperTags.containsAll(tags) && (query.isBlank() || listOf(paper.title, paper.summary, paper.authors.joinToString(" "), paper.id).any { it.contains(query.trim(), true) })
    }
    var showTags by remember { mutableStateOf(false) }
    Column(Modifier.padding(contentPadding).fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Favorites", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f).padding(20.dp))
            TextButton(onClick = { actions.managing = true }) { Text("Manage") }
        }
        OutlinedTextField(query, { query = it }, label = { Text("Search favorites") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp))
        Column(Modifier.padding(horizontal = 16.dp)) {
            ChoiceMenu("Group", listOf<Long?>(null, -1L) + actions.groups.map { it.id }, group,
                { id -> when(id) { null -> "All favorites"; -1L -> "Ungrouped"; else -> actions.groups.firstOrNull { it.id == id }?.name ?: "All favorites" } }, { group = it })
            Row {
                TextButton(onClick = { showTags = true }) { Text("Tags (${tags.size})") }
                TextButton(onClick = { query = ""; group = null; tags = emptySet() }) { Text("Clear filters") }
            }
        }
        if (filtered.isEmpty()) EmptyMessage("No matching favorites", "Save a paper or change your filters.")
        else LazyColumn { items(filtered, key = Paper::id) { SwipePaperCard(it, onPaperClick) } }
    }
    if (showTags) AlertDialog(onDismissRequest = { showTags = false }, title = { Text("Match all selected tags") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState())) { actions.tags.forEach { tag -> Row(verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material3.Checkbox(tag.id in tags, { tags = if (it) tags + tag.id else tags - tag.id }); Text(tag.name)
        } } }
    }, confirmButton = { TextButton(onClick = { showTags = false }) { Text("Done") } })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    viewModel: DetailViewModel,
    onBack: () -> Unit,
    onReadPdf: () -> Unit,
    onReadHtml: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val actions = LocalPaperActions.current
    val context = LocalContext.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Paper details") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { state.paper?.let { sharePaper(context, it) } }, enabled = state.paper != null) { Icon(Icons.Default.Share, "Share paper") }
                    IconButton(onClick = { actions.organizing = state.paper }, enabled = state.paper != null) { Icon(Icons.Default.Folder, "Groups & tags") }
                    IconButton(onClick = { state.paper?.let { actions.act(it, SwipeAction.FAVORITE, context) } }, enabled = state.paper != null) {
                        Icon(
                            if (state.favorite) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                            if (state.favorite) "Remove favorite" else "Add favorite",
                        )
                    }
                },
            )
        },
    ) { padding ->
        val paper = state.paper
        if (paper == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
        ) {
            MathText(paper.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(10.dp))
            Text(
                paper.authors.joinToString(", "),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "${paper.versionedId}  •  ${formatDate(paper.publishedAt)}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                paper.categories.joinToString("  "),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.secondary,
            )
            HorizontalDivider(Modifier.padding(vertical = 18.dp))
            Text("Abstract", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            MathText(paper.summary, style = MaterialTheme.typography.bodyLarge)
            paper.journalReference?.let {
                Spacer(Modifier.height(16.dp))
                Text("Journal reference", fontWeight = FontWeight.SemiBold)
                Text(it)
            }
            paper.doi?.let {
                Spacer(Modifier.height(10.dp))
                Text("DOI", fontWeight = FontWeight.SemiBold)
                Text(it)
            }
            Spacer(Modifier.height(24.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onReadHtml, modifier = Modifier.weight(1f)) { Text("Read HTML") }
                OutlinedButton(onClick = onReadPdf, modifier = Modifier.weight(1f)) {
                    Text(if (state.download != null) "Offline PDF" else "Read PDF")
                }
            }
            Spacer(Modifier.height(10.dp))
            when {
                state.download != null -> OutlinedButton(
                    onClick = viewModel::deleteDownload,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.Delete, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Delete offline PDF")
                }
                state.downloadRunning -> {
                    LinearProgressIndicator(
                        progress = { state.downloadProgress / 100f },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        "Downloading ${state.downloadProgress}%",
                        modifier = Modifier.padding(top = 6.dp),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                else -> OutlinedButton(
                    onClick = viewModel::download,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.Download, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Download for offline reading")
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
internal fun PaperList(
    papers: List<Paper>,
    loading: Boolean,
    loadingMore: Boolean,
    hasMore: Boolean,
    error: String?,
    onPaperClick: (Paper) -> Unit,
    onLoadMore: () -> Unit,
) {
    when {
        loading && papers.isEmpty() -> LazyColumn(Modifier.fillMaxSize()) {
            items(6) { PaperSkeleton() }
        }
        papers.isEmpty() && error != null -> EmptyMessage("Could not load papers", error)
        papers.isEmpty() -> EmptyMessage("Nothing here yet", "Try refreshing or changing your search.")
        else -> LazyColumn(Modifier.fillMaxSize()) {
            error?.let {
                item {
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
            items(papers, key = Paper::id) { paper -> SwipePaperCard(paper, onPaperClick) }
            if (loadingMore) {
                item {
                    Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(28.dp))
                    }
                }
            } else if (hasMore) {
                item {
                    TextButton(
                        onClick = onLoadMore,
                        modifier = Modifier.fillMaxWidth().padding(8.dp),
                    ) { Text("Load more") }
                }
            }
        }
    }
}

@Composable
internal fun PaperCard(paper: Paper, onPaperClick: (Paper) -> Unit) {
    val actions = LocalPaperActions.current
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Box {
            Column(Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(paper.primaryCategory.uppercase(), style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.weight(1f))
                    Text(formatDate(paper.publishedAt), modifier = Modifier.padding(end = 48.dp), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(10.dp))
                MathText(paper.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(7.dp))
                Text(paper.authors.take(4).joinToString(", ") + if (paper.authors.size > 4) " et al." else "",
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(10.dp))
                MathText(paper.summary, maxLines = 3, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Box(Modifier.matchParentSize().semantics { contentDescription = "${paper.title}. ${paper.authors.joinToString(", ")}. ${paper.summary}" }.clickable { onPaperClick(paper) })
            Row(Modifier.align(Alignment.TopEnd).background(MaterialTheme.colorScheme.surface), verticalAlignment = Alignment.CenterVertically) {
                if (actions.records.any { it.paperId == paper.id }) Icon(Icons.Default.Bookmark, "Saved", Modifier.size(18.dp))
                IconButton(onClick = { actions.more = paper }) { Icon(Icons.Default.MoreVert, "More actions for ${paper.id}") }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, modifier = Modifier.padding(start = 32.dp, end = 20.dp, top = 24.dp, bottom = 8.dp),
        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun PaperSkeleton() {
    Column(Modifier.fillMaxWidth().padding(20.dp)) {
        Box(
            Modifier.fillMaxWidth(.9f).height(18.dp).clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )
        Spacer(Modifier.height(10.dp))
        Box(
            Modifier.fillMaxWidth(.55f).height(12.dp).clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )
        Spacer(Modifier.height(10.dp))
        Box(
            Modifier.fillMaxWidth().height(40.dp).clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )
    }
}

@Composable
private fun EmptyMessage(title: String, message: String) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            Text(
                message,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun CategoryPicker(
    selected: Set<String>,
    search: String,
    onSearchChange: (String) -> Unit,
    onToggle: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        OutlinedTextField(
            value = search,
            onValueChange = onSearchChange,
            label = { Text("Search categories") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        val filtered = remember(search) { filteredCategories(search) }
        LazyColumn {
            items(filtered, key = ArxivCategory::code) { category ->
                CategoryRow(
                    category,
                    selected = category.code in selected,
                    onToggle = { onToggle(category.code) },
                )
            }
        }
    }
}

@Composable
internal fun CategoryRow(
    category: ArxivCategory,
    selected: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(category.name, style = MaterialTheme.typography.bodyLarge)
            Text(
                "${category.code} • ${category.group}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        FilterChip(
            selected = selected,
            onClick = onToggle,
            label = { Text(if (selected) "Following" else "Follow") },
            leadingIcon = if (selected) {
                { Icon(Icons.Default.Check, null, Modifier.size(16.dp)) }
            } else null,
        )
    }
}

@Composable
private fun CategoryFilterDialog(
    selected: String?,
    onSelect: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var search by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Filter by category") },
        text = {
            Column(Modifier.height(480.dp)) {
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    label = { Text("Search") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                TextButton(onClick = { onSelect(null) }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (selected == null) "✓ All categories" else "All categories")
                }
                LazyColumn {
                    items(filteredCategories(search), key = ArxivCategory::code) { category ->
                        Text(
                            (if (category.code == selected) "✓ " else "") +
                                "${category.code} — ${category.name}",
                            modifier = Modifier.fillMaxWidth().clickable {
                                onSelect(category.code)
                            }.padding(vertical = 10.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun filteredCategories(search: String): List<ArxivCategory> {
    val query = search.trim()
    return if (query.isEmpty()) ArxivCategories.all else ArxivCategories.all.filter {
        it.code.contains(query, true) ||
            it.name.contains(query, true) ||
            it.group.contains(query, true)
    }
}

private val dateFormatter: DateTimeFormatter =
    DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)

private fun formatDate(epochMillis: Long): String =
    if (epochMillis <= 0) "Unknown date" else Instant.ofEpochMilli(epochMillis)
        .atZone(ZoneId.systemDefault())
        .toLocalDate()
        .format(dateFormatter)
