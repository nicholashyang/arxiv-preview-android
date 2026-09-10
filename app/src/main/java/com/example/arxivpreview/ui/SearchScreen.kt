package com.example.arxivpreview.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.arxivpreview.model.*

@Composable
fun SearchScreen(viewModel: SearchViewModel, contentPadding: PaddingValues, onPaperClick: (Paper) -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var advanced by remember { mutableStateOf(false) }
    Column(Modifier.padding(contentPadding).fillMaxSize().padding(horizontal = 16.dp)) {
        Text("Search", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 12.dp))
        OutlinedTextField(state.draft.keywords, { viewModel.edit(state.draft.copy(keywords = it)) },
            label = { Text("Keywords, arXiv ID, or link") }, modifier = Modifier.fillMaxWidth().testTag("search_input"), singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { viewModel.search() }))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { advanced = true }) { Text("Advanced") }
            Button(onClick = viewModel::search) { Text("Search") }
            TextButton(onClick = viewModel::clear) { Text("Clear") }
        }
        state.applied?.let { Text(it.description(), style = MaterialTheme.typography.bodySmall, maxLines = 4); Text("${state.total} results", style = MaterialTheme.typography.labelMedium) }
        state.error?.let { error -> Row {
            Text(error, Modifier.weight(1f), color = MaterialTheme.colorScheme.error)
            TextButton(onClick = viewModel::retry) { Text("Retry") }
        } }
        Box(Modifier.weight(1f)) {
            PaperList(state.papers, state.searching, state.loadingMore, state.hasMore, null, onPaperClick, viewModel::loadMore)
        }
    }
    if (advanced) AdvancedSearchDialog(state.draft, onDismiss = { advanced = false }, onApply = {
        viewModel.edit(it); viewModel.search(); advanced = false
    })
}

@Composable
private fun AdvancedSearchDialog(initial: SearchCriteria, onDismiss: () -> Unit, onApply: (SearchCriteria) -> Unit) {
    var draft by remember { mutableStateOf(initial) }
    var categorySearch by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Advanced search") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(draft.keywords, { draft = draft.copy(keywords = it) }, label = { Text("Keywords / ID / link") })
            if (draft.exactId.isNotEmpty()) Text("Exact ID lookup: other filters will not apply.")
            OutlinedTextField(draft.title, { draft = draft.copy(title = it) }, label = { Text("Title") })
            OutlinedTextField(draft.author, { draft = draft.copy(author = it) }, label = { Text("Author") })
            OutlinedTextField(draft.abstractText, { draft = draft.copy(abstractText = it) }, label = { Text("Abstract") })
            ChoiceMenu("Match", MatchMode.entries, draft.match, { it.label }, { draft = draft.copy(match = it) })
            OutlinedTextField(draft.from, { draft = draft.copy(from = it) }, label = { Text("From (YYYY-MM-DD, UTC)") }, singleLine = true)
            OutlinedTextField(draft.until, { draft = draft.copy(until = it) }, label = { Text("Through (YYYY-MM-DD, UTC)") }, singleLine = true)
            ChoiceMenu("Sort", SearchSort.entries, draft.sort, { it.label }, { draft = draft.copy(sort = it) })
            if (draft.sort != SearchSort.RELEVANCE) ChoiceMenu("Order", listOf(false, true), draft.ascending, { if (it) "Oldest first" else "Newest first" }, { draft = draft.copy(ascending = it) })
            Text("Categories (${draft.categories.size})")
            if (draft.categories.isNotEmpty()) Text(draft.categories.sorted().joinToString(", "))
            OutlinedTextField(categorySearch, { categorySearch = it }, label = { Text("Find categories") }, singleLine = true)
            Column(Modifier.height(180.dp).verticalScroll(rememberScrollState())) {
                ArxivCategories.all.filter { categorySearch.isBlank() || "${it.code} ${it.name}".contains(categorySearch, true) }.forEach { category ->
                    Row { Checkbox(category.code in draft.categories, { selected -> draft = draft.copy(categories = if (selected) draft.categories + category.code else draft.categories - category.code) }); Text("${category.code} — ${category.name}") }
                }
            }
            TextButton(onClick = { draft = SearchCriteria() }) { Text("Reset all") }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }, confirmButton = { TextButton(onClick = { error = draft.validate(); if (error == null) onApply(draft) }) { Text("Apply & search") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}
