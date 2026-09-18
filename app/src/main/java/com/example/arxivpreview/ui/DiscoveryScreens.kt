@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.example.arxivpreview.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalUriHandler
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.arxivpreview.AppContainer
import com.example.arxivpreview.data.discovery.*
import com.example.arxivpreview.model.*
import kotlinx.coroutines.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

internal data class DiscoveryNavigation(val author: (Paper, String) -> Unit, val paper: (Paper) -> Unit)
internal val LocalDiscoveryNavigation = staticCompositionLocalOf<DiscoveryNavigation?> { null }
internal fun discoveryDate(value: Long): String = if (value == 0L) "Not synced yet" else DateTimeFormatter.ofPattern("MMM d, HH:mm").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(value))

@Composable internal fun PaperAuthors(paper: Paper, compact: Boolean = false) {
    val navigation = LocalDiscoveryNavigation.current
    var expanded by rememberSaveable(paper.id) { mutableStateOf(false) }
    val visible = if (expanded) paper.authors else paper.authors.take(if (compact) 2 else 6)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        visible.forEach { name ->
            if (navigation != null) TextButton(onClick = { navigation.author(paper, name) }, contentPadding = PaddingValues(horizontal = 4.dp)) {
                Text(name, style = if (compact) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium)
            } else Text(name, style = MaterialTheme.typography.bodySmall)
        }
        if (paper.authors.size > visible.size) TextButton(onClick = { expanded = true }) { Text("+${paper.authors.size - visible.size} authors") }
        else if (expanded) TextButton(onClick = { expanded = false }) { Text("Show fewer") }
    }
}

@Composable private fun DiscoveryHeader(title: String, refreshing: Boolean, refresh: () -> Unit, extra: @Composable RowScope.() -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
        extra()
        IconButton(onClick = refresh, enabled = !refreshing) { Icon(Icons.Default.Refresh, "Refresh $title") }
    }
    if (refreshing) LinearProgressIndicator(Modifier.fillMaxWidth())
}
@Composable private fun StatusText(message: String?) { if (!message.isNullOrBlank()) Text(message, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) }

@Composable fun HomeScreen(vm: DiscoveryViewModel, padding: PaddingValues, interests: () -> Unit, onPaper: (Paper) -> Unit) {
    val live by vm.recommendations.collectAsStateWithLifecycle()
    val sync by vm.sync.collectAsStateWithLifecycle()
    val sources by vm.sources.collectAsStateWithLifecycle()
    val warning by vm.onlineWarning.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val actions = LocalPaperActions.current
    val scope = rememberCoroutineScope()
    val scroll = rememberLazyListState()
    // Preserve relative order while browsing; a completed refresh or returning to the top adopts the new ranking.
    var orderedIds by remember { mutableStateOf<List<String>>(emptyList()) }
    val atTop by remember { derivedStateOf { scroll.firstVisibleItemIndex == 0 } }
    LaunchedEffect(live, sync.running, atTop) {
        if (orderedIds.isEmpty() || (!sync.running && atTop)) orderedIds = live.map { it.paper.id }
    }
    val items = live.sortedBy { orderedIds.indexOf(it.paper.id).takeIf { n -> n >= 0 } ?: Int.MAX_VALUE }
    LaunchedEffect(Unit) { vm.refreshIfStale() }
    Column(Modifier.padding(padding).fillMaxSize()) {
        DiscoveryHeader("Home", sync.running, vm::refresh) { TextButton(onClick = interests) { Text("Interests") } }
        LazyColumn(state = scroll, modifier = Modifier.weight(1f)) {
            item {
                Text("For You", Modifier.padding(horizontal = 20.dp, vertical = 8.dp), style = MaterialTheme.typography.titleLarge)
                StatusText("New research from the last 7 days · ${discoveryDate(sources.maxOfOrNull { it.completedAt } ?: 0)}")
                StatusText(error ?: sync.message ?: warning)
                if (items.isEmpty()) {
                    Text("Your research starts here", Modifier.padding(20.dp), style = MaterialTheme.typography.titleMedium)
                    Text("Follow categories, add a topic or save papers to shape your recommendations.", Modifier.padding(horizontal = 20.dp))
                    TextButton(onClick = interests, modifier = Modifier.padding(horizontal = 12.dp)) { Text("Manage interests") }
                }
            }
            for (earlier in listOf(false, true)) {
                val section = items.filter { it.earlier == earlier }.let { if (earlier) it.take((20 - items.count { p -> !p.earlier }).coerceAtLeast(0)) else it }
                if (earlier && section.isNotEmpty()) item { Text("Earlier papers", Modifier.padding(20.dp), style = MaterialTheme.typography.titleLarge) }
                items(section, key = { it.paper.id }) { item ->
                    Column {
                        Text(item.reasons.joinToString(" · "), Modifier.padding(horizontal = 24.dp, vertical = 4.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        SwipePaperCard(item.paper, onPaper)
                        TextButton(onClick = {
                            scope.launch {
                                vm.container.recommendationRepository.dismiss(item.paper.id)
                                actions.snackbar.currentSnackbarData?.dismiss()
                                if (actions.snackbar.showSnackbar("Removed from recommendations", "Undo", duration = SnackbarDuration.Short) == SnackbarResult.ActionPerformed)
                                    vm.container.recommendationRepository.restore(item.paper.id)
                            }
                        }, modifier = Modifier.padding(horizontal = 16.dp)) { Text("Not interested") }
                    }
                }
            }
            item { Spacer(Modifier.height(20.dp)) }
        }
    }
}

@Composable fun InboxScreen(vm: DiscoveryViewModel, padding: PaddingValues, onPaper: (Paper) -> Unit) {
    val all by vm.inbox.collectAsStateWithLifecycle()
    val sync by vm.sync.collectAsStateWithLifecycle()
    val sources by vm.sources.collectAsStateWithLifecycle()
    var filter by rememberSaveable { mutableStateOf("Unread") }
    val filtered = all.filter { when (filter) { "Archived" -> it.archived; "Unread" -> !it.archived && !it.isRead; else -> !it.archived } }
    Column(Modifier.padding(padding).fillMaxSize()) {
        DiscoveryHeader("Inbox", sync.running, vm::refresh)
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Unread", "All", "Archived").forEach { label -> FilterChip(filter == label, { filter = label }, label = { Text(label) }) }
            TextButton(onClick = { vm.run { vm.container.inboxRepository.readAll() } }) { Text("Read all") }
        }
        LazyColumn {
            item { StatusText(sync.message ?: "${discoveryDate(sources.maxOfOrNull { it.completedAt } ?: 0)} · Author updates may arrive after indexing.") }
            if (filtered.isEmpty()) item { Text("No ${filter.lowercase()} updates. Follow authors and topics in Profile, then refresh.", Modifier.padding(20.dp)) }
            items(filtered, key = { it.paper.id }) { item ->
                Text((if (!item.isRead) "● " else "") + item.reasons.joinToString(" · "), Modifier.padding(horizontal = 24.dp), style = MaterialTheme.typography.labelMedium)
                SwipePaperCard(item.paper) { paper -> vm.run { vm.container.inboxRepository.read(paper.id); onPaper(paper) } }
                FlowRow(Modifier.padding(horizontal = 16.dp)) {
                    TextButton(onClick = { vm.run { vm.container.inboxRepository.read(item.paper.id, !item.isRead) } }) { Text(if (item.isRead) "Mark unread" else "Mark read") }
                    TextButton(onClick = { vm.run { vm.container.inboxRepository.archive(item.paper.id, !item.archived) } }) { Text(if (item.archived) "Restore" else "Archive") }
                }
            }
        }
    }
}

@Composable fun ExploreScreen(container: AppContainer, padding: PaddingValues, onPaper: (Paper) -> Unit, onAuthor: (String) -> Unit, onLatestCategory: (ArxivCategory) -> Unit) {
    var tab by rememberSaveable { mutableStateOf("Latest") }
    val search: SearchViewModel = viewModel(factory = ViewModelFactories.search(container))
    val authorSearch: AuthorSearchViewModel = viewModel(factory = discoveryFactory { AuthorSearchViewModel(container.authorRepository) })
    val tabState = rememberSaveableStateHolder()
    var categoryQuery by rememberSaveable { mutableStateOf("") }
    Column(Modifier.padding(padding).fillMaxSize()) {
        Text("Explore", Modifier.padding(horizontal = 20.dp, vertical = 12.dp), style = MaterialTheme.typography.headlineMedium)
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Latest", "Papers", "Authors", "Categories").forEach { label -> FilterChip(tab == label, { tab = label }, label = { Text(label) }) }
        }
        Box(Modifier.weight(1f)) {
            tabState.SaveableStateProvider(tab) {
            when (tab) {
                "Latest" -> LatestScreen(PaddingValues(), onLatestCategory)
                "Papers" -> SearchScreen(search, PaddingValues(), onPaper)
                "Authors" -> AuthorSearchScreen(authorSearch, onAuthor)
                else -> Column {
                    OutlinedTextField(categoryQuery, { categoryQuery = it }, label = { Text("Find a category") }, modifier = Modifier.fillMaxWidth().padding(16.dp), singleLine = true)
                    LazyColumn { items(ArxivCategories.all.filter { it.code.contains(categoryQuery, true) || it.name.contains(categoryQuery, true) }, key = { it.code }) { category ->
                        ListItem(headlineContent = { Text(category.name) }, supportingContent = { Text("${category.code} · ${category.group}") }, modifier = Modifier.clickable {
                            search.edit(SearchCriteria(categories = setOf(category.code), sort = SearchSort.SUBMITTED)); search.search(); tab = "Papers"
                        })
                    } }
                }
            }
        }
    }
    }
}

@Composable private fun AuthorSearchScreen(vm: AuthorSearchViewModel, onAuthor: (String) -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    Column {
        OutlinedTextField(query, { query = it }, label = { Text("Author name") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(16.dp))
        Button(onClick = { vm.search(query) }, modifier = Modifier.padding(horizontal = 16.dp)) { Text("Search authors") }
        if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        LazyColumn {
            item { StatusText(state.error); if (state.submitted && !state.loading && state.results.isEmpty()) StatusText("No matching authors. Try another spelling.") }
            items(state.results, key = { it.id }) { author -> ListItem(headlineContent = { Text(author.name) }, supportingContent = { Text("Semantic Scholar · ${author.id}") }, modifier = Modifier.clickable { onAuthor(author.id) }) }
            if (state.next != null) item { TextButton(onClick = vm::more, enabled = !state.loading) { Text("Load more authors") } }
        }
    }
}

@Composable fun AuthorPickerScreen(container: AppContainer, paperId: String, name: String, onBack: () -> Unit, onAuthor: (String) -> Unit, onPaper: (Paper) -> Unit) {
    var candidates by remember { mutableStateOf<List<AuthorSummary>>(emptyList()) }
    var fallback by remember { mutableStateOf<List<Paper>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var message by remember { mutableStateOf<String?>(null) }
    var retry by remember { mutableIntStateOf(0) }
    var searchingNames by remember { mutableStateOf(false) }
    LaunchedEffect(paperId, name, retry, searchingNames) {
        loading = true
        try {
            candidates = if (searchingNames) container.authorRepository.search(name).first else container.authorRepository.resolve(paperId, name)
            if (candidates.isEmpty()) {
                message = "Identity not confirmed. These arXiv results match a name, not a verified author."
                fallback = container.paperRepository.search(SearchCriteria(author = name, match = MatchMode.PHRASE, sort = SearchSort.SUBMITTED), 0).papers
            } else {
                fallback = emptyList()
                message = if (searchingNames) "Select the correct profile. Name search can include different people." else "Select an author from this paper to view their profile."
            }
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { message = e.message }
        finally { loading = false }
    }
    Scaffold(topBar = { TopAppBar(title = { Text(name) }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }) }) { padding ->
        LazyColumn(Modifier.padding(padding)) {
            item { StatusText(message); if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                FlowRow { TextButton(onClick = { retry++ }, enabled = !loading) { Text("Retry") }; TextButton(onClick = { searchingNames = true; retry++ }, enabled = !loading) { Text("Find author profiles") } }
            }
            items(candidates, key = { it.id }) { a -> ListItem(headlineContent = { Text(a.name) }, supportingContent = { Text("View profile · ${a.id}") }, modifier = Modifier.clickable { onAuthor(a.id) }) }
            items(fallback, key = { it.id }) { SwipePaperCard(it, onPaper) }
        }
    }
}

@Composable fun AuthorScreen(vm: AuthorViewModel, onBack: () -> Unit, onPaper: (Paper) -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val following by vm.following.collectAsStateWithLifecycle()
    val uri = LocalUriHandler.current
    val actions = LocalPaperActions.current
    val warning by vm.container.semanticScholar.warning.collectAsStateWithLifecycle()
    Scaffold(topBar = { TopAppBar(title = { Text("Author") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }, actions = { IconButton(onClick = vm::refresh, enabled = !state.loading) { Icon(Icons.Default.Refresh, "Refresh author") } }) }) { padding ->
        LazyColumn(Modifier.padding(padding)) {
            item {
                Column(Modifier.padding(20.dp)) {
                    state.profile?.let { p ->
                        Text(p.name, style = MaterialTheme.typography.headlineMedium)
                        Text(p.affiliations.joinToString(" · ").ifBlank { "Affiliation not provided" }, Modifier.padding(vertical = 8.dp))
                        Text("Papers: ${p.paperCount ?: "Not provided"} · Citations: ${p.citationCount ?: "Not provided"} · h-index: ${p.hIndex ?: "Not provided"}")
                        Button(onClick = vm::follow) { Text(if (following) "Following · Unfollow" else "Follow author") }
                        StatusText("Semantic Scholar · ${discoveryDate(p.updatedAt)}${if (state.stale) " · Cached" else ""}")
                    }
                    if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                    StatusText(state.error ?: warning)
                    Text("Papers", style = MaterialTheme.typography.titleLarge)
                    if (!state.loading && state.works.isEmpty()) Text("No papers available.")
                }
            }
            items(state.works, key = { it.id }) { work ->
                ListItem(headlineContent = { Text(work.title) }, supportingContent = { Text("${work.year ?: "Date not provided"} · ${work.arxivId?.let { "arXiv:$it" } ?: "Open in Semantic Scholar"}") }, modifier = Modifier.clickable {
                    if (work.arxivId != null) vm.open(work, onPaper) else actions.run { uri.openUri(work.url) }
                })
            }
            if (state.next != null) item { TextButton(onClick = vm::more, enabled = !state.loading) { Text("Load more papers") } }
        }
    }
}

@Composable fun ResearchPreferencesScreen(vm: DiscoveryViewModel, onBack: () -> Unit, onAuthor: (String) -> Unit) {
    val authors by vm.authors.collectAsStateWithLifecycle()
    val keywords by vm.keywords.collectAsStateWithLifecycle()
    val feedback by vm.feedback.collectAsStateWithLifecycle()
    val configured by vm.keyConfigured.collectAsStateWithLifecycle()
    val status by vm.keyStatus.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val prefs by vm.container.preferencesRepository.preferences.collectAsStateWithLifecycle(com.example.arxivpreview.data.AppPreferences())
    var topic by rememberSaveable { mutableStateOf("") }
    // Never put API keys in saved instance state.
    var key by remember { mutableStateOf("") }
    Scaffold(topBar = { TopAppBar(title = { Text("Research preferences") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }) }) { padding ->
        LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { StatusText(error); Text("Following authors", style = MaterialTheme.typography.titleLarge); if (authors.isEmpty()) Text("Find an author in Explore or tap an author on a paper.") }
            items(authors, key = { "author:${it.id}" }) { a ->
                Row(verticalAlignment = Alignment.CenterVertically) { TextButton(onClick = { onAuthor(a.id) }, modifier = Modifier.weight(1f)) { Text(a.name) }; TextButton(onClick = { vm.run { vm.container.authorRepository.follow(a.id, false) } }) { Text("Unfollow") } }
            }
            item { HorizontalDivider(); Text("Topics & keywords", style = MaterialTheme.typography.titleLarge)
                Text("Match a phrase in paper titles or abstracts. Topics can bring in papers beyond your categories.")
                OutlinedTextField(topic, { topic = it }, label = { Text("Add a topic") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                TextButton(onClick = { vm.addKeyword(topic); topic = "" }) { Text("Add topic") }
            }
            items(keywords, key = { "keyword:${it.text}" }) { k -> Row(verticalAlignment = Alignment.CenterVertically) { Text(k.text, Modifier.weight(1f)); TextButton(onClick = { vm.removeKeyword(k.text) }) { Text("Remove") } } }
            item {
                Text("Followed categories: ${prefs.categories.sorted().joinToString(", ")}")
                Text("Manage categories under Profile → Content preferences.", style = MaterialTheme.typography.bodySmall)
                HorizontalDivider(Modifier.padding(vertical = 12.dp))
                Text("Online recommendations", style = MaterialTheme.typography.titleLarge)
                Row(verticalAlignment = Alignment.CenterVertically) { Text("Enhance recommendations", Modifier.weight(1f)); Switch(prefs.onlineRecommendations, { value -> vm.run { vm.container.preferencesRepository.setOnlineRecommendations(value) } }) }
                Text("Uses saved and excluded paper IDs with Semantic Scholar. Your reading time is not recorded. Local ranking works without this option.")
                Text("Semantic Scholar access", Modifier.padding(top = 16.dp), style = MaterialTheme.typography.titleLarge)
                Text(if (configured) "Personal key stored securely" else "Public access · A personal key is optional")
                OutlinedTextField(key, { key = it }, label = { Text("Personal API key") }, visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth())
                FlowRow { TextButton(onClick = { vm.saveKey(key); key = "" }, enabled = key.isNotBlank()) { Text("Save key") }; TextButton(onClick = vm::validateKey) { Text("Validate access") }; if (configured) TextButton(onClick = vm::deleteKey) { Text("Delete key") } }
                StatusText(status)
                HorizontalDivider(); Text("Not interested", Modifier.padding(top = 12.dp), style = MaterialTheme.typography.titleLarge)
                if (feedback.isEmpty()) Text("No excluded papers.")
            }
            items(feedback, key = { "feedback:${it.paperId}" }) { f -> Row(verticalAlignment = Alignment.CenterVertically) { Text("arXiv:${f.paperId}", Modifier.weight(1f)); TextButton(onClick = { vm.run { vm.container.recommendationRepository.restore(f.paperId) } }) { Text("Restore") } } }
        }
    }
}

@Composable internal fun SimilarPapers(paper: Paper) {
    val actions = LocalPaperActions.current
    val nav = LocalDiscoveryNavigation.current ?: return
    var results by remember(paper.id) { mutableStateOf<List<Paper>>(emptyList()) }
    var loading by remember(paper.id) { mutableStateOf(false) }
    var requested by rememberSaveable(paper.id) { mutableStateOf(false) }
    var retry by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(paper.id, requested, retry) {
        if (requested) {
            loading = true
            try { results = actions.container.recommendationRepository.similar(paper); error = null }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { error = e.message }
            finally { loading = false }
        }
    }
    Text("Similar papers", style = MaterialTheme.typography.titleLarge)
    TextButton(onClick = { requested = true; retry++ }, enabled = !loading) { Text(if (loading) "Loading…" else if (requested) "Refresh similar papers" else "Find similar papers") }
    if (requested && !loading && results.isEmpty()) StatusText(error ?: "No similar papers available yet.")
    results.forEach { related -> TextButton(onClick = { nav.paper(related) }) { Text(related.title) } }
}
