package com.example.arxivpreview.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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
import androidx.compose.material3.Switch
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
                Text("Welcome to arXiv Preview", style = MaterialTheme.typography.headlineMedium)
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
fun SearchScreen(
    viewModel: SearchViewModel,
    contentPadding: PaddingValues,
    onPaperClick: (Paper) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showCategories by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .padding(contentPadding)
            .fillMaxSize()
            .padding(horizontal = 16.dp),
    ) {
        Text("Search", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 12.dp))
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = state.term,
            onValueChange = viewModel::setTerm,
            modifier = Modifier.fillMaxWidth().testTag("search_input"),
            label = { Text("Title, abstract, author, or arXiv ID") },
            singleLine = true,
            leadingIcon = { Icon(Icons.Default.Search, null) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { viewModel.search() }),
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(onClick = { showCategories = true }, modifier = Modifier.weight(1f)) {
                Text(state.category ?: "All categories", maxLines = 1)
            }
            Button(onClick = viewModel::search, enabled = state.term.isNotBlank()) {
                Text("Search")
            }
        }
        Box(Modifier.weight(1f)) {
            PaperList(
                papers = state.papers,
                loading = state.searching,
                loadingMore = state.loadingMore,
                hasMore = state.hasMore,
                error = state.error,
                onPaperClick = onPaperClick,
                onLoadMore = viewModel::loadMore,
            )
        }
    }
    if (showCategories) {
        CategoryFilterDialog(
            selected = state.category,
            onSelect = {
                viewModel.setCategory(it)
                showCategories = false
            },
            onDismiss = { showCategories = false },
        )
    }
}

@Composable
fun FavoritesScreen(
    viewModel: FavoritesViewModel,
    contentPadding: PaddingValues,
    onPaperClick: (Paper) -> Unit,
) {
    val papers by viewModel.papers.collectAsStateWithLifecycle()
    Column(Modifier.padding(contentPadding).fillMaxSize()) {
        Text(
            "Favorites",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
        )
        if (papers.isEmpty()) {
            EmptyMessage(
                title = "No favorites yet",
                message = "Open a paper and tap the bookmark to keep it here.",
            )
        } else {
            LazyColumn {
                items(papers, key = Paper::id) { paper ->
                    PaperCard(paper, onPaperClick)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    viewModel: DetailViewModel,
    onBack: () -> Unit,
    onReadPdf: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
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
                    IconButton(onClick = viewModel::toggleFavorite, enabled = state.paper != null) {
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
            Text(paper.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
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
            Text(paper.summary, style = MaterialTheme.typography.bodyLarge)
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
            Button(onClick = onReadPdf, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.PictureAsPdf, null)
                Spacer(Modifier.width(8.dp))
                Text(if (state.download != null) "Read offline PDF" else "Read PDF")
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
fun SettingsScreen(viewModel: SettingsViewModel, contentPadding: PaddingValues) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var selected by remember { mutableStateOf(state.preferences.categories) }
    var categorySearch by remember { mutableStateOf("") }
    LaunchedEffect(state.preferences.categories) {
        selected = state.preferences.categories
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> viewModel.setNotifications(granted) }

    Column(Modifier.padding(contentPadding).fillMaxSize()) {
        Text(
            "Settings",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
        )
        ListItem(
            headlineContent = { Text("Daily update notifications") },
            supportingContent = { Text("A summary when new submissions are available") },
            trailingContent = {
                Switch(
                    checked = state.preferences.notificationsEnabled,
                    onCheckedChange = { enabled ->
                        if (enabled && Build.VERSION.SDK_INT >= 33) {
                            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            viewModel.setNotifications(enabled)
                        }
                    },
                )
            },
        )
        HorizontalDivider()
        Text(
            "Followed categories",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
        )
        OutlinedTextField(
            value = categorySearch,
            onValueChange = { categorySearch = it },
            label = { Text("Filter categories") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        )
        val filtered = remember(categorySearch) { filteredCategories(categorySearch) }
        LazyColumn(Modifier.weight(1f)) {
            items(filtered, key = ArxivCategory::code) { category ->
                CategoryRow(
                    category = category,
                    selected = category.code in selected,
                    onToggle = {
                        selected = if (category.code in selected) {
                            selected - category.code
                        } else {
                            selected + category.code
                        }
                    },
                )
            }
        }
        Button(
            onClick = { viewModel.saveCategories(selected) },
            enabled = selected.isNotEmpty() && selected != state.preferences.categories,
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        ) {
            Text("Save ${selected.size} categories")
        }
    }
}

@Composable
private fun PaperList(
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
            items(papers, key = Paper::id) { paper -> PaperCard(paper, onPaperClick) }
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
private fun PaperCard(paper: Paper, onPaperClick: (Paper) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)
            .clickable { onPaperClick(paper) },
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                paper.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                paper.authors.take(4).joinToString(", ") +
                    if (paper.authors.size > 4) " et al." else "",
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                paper.summary,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(10.dp))
            Row {
                Text(
                    paper.primaryCategory,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    formatDate(paper.publishedAt),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
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
private fun CategoryRow(
    category: ArxivCategory,
    selected: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(vertical = 10.dp),
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
