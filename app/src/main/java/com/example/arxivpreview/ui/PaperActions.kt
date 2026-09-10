package com.example.arxivpreview.ui

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.arxivpreview.AppContainer
import com.example.arxivpreview.data.*
import com.example.arxivpreview.data.local.*
import com.example.arxivpreview.model.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs
import kotlin.math.roundToInt

internal fun sharePaper(context: Context, paper: Paper) {
    try { context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"; putExtra(Intent.EXTRA_TEXT, paper.shareText()); putExtra(Intent.EXTRA_SUBJECT, paper.title)
    }, "Share paper")) } catch (_: android.content.ActivityNotFoundException) {
        android.widget.Toast.makeText(context, "No sharing app available", android.widget.Toast.LENGTH_SHORT).show()
    }
}
internal class PaperActions(val container: AppContainer, val scope: CoroutineScope, val snackbar: SnackbarHostState) {
    var preferences by mutableStateOf(AppPreferences())
    var records by mutableStateOf<List<FavoriteEntity>>(emptyList())
    var groups by mutableStateOf<List<FavoriteGroupEntity>>(emptyList())
    var tags by mutableStateOf<List<TagEntity>>(emptyList())
    var relations by mutableStateOf<List<FavoriteTagEntity>>(emptyList())
    var organizing by mutableStateOf<Paper?>(null)
    var more by mutableStateOf<Paper?>(null)
    var managing by mutableStateOf(false)
    private val gate = Mutex()
    fun run(block: suspend () -> Unit) { scope.launch {
        try { gate.withLock { block() } }
        catch (cancel: kotlinx.coroutines.CancellationException) { throw cancel }
        catch (error: Exception) { snackbar.showSnackbar(error.message ?: "Could not save changes") }
    } }
    fun act(paper: Paper, action: SwipeAction, context: Context) {
        when (action) {
            SwipeAction.FAVORITE -> run {
                val snapshot = container.favoriteRepository.toggle(paper)
                if (snapshot != null) scope.launch {
                    snackbar.currentSnackbarData?.dismiss()
                    val result = withTimeoutOrNull(5_000) { snackbar.showSnackbar("Removed from favorites", "Undo", duration = SnackbarDuration.Indefinite) }
                    if (result == SnackbarResult.ActionPerformed) run { container.favoriteRepository.restore(snapshot) }
                }
            }
            SwipeAction.ORGANIZE -> { organizing = paper }
            SwipeAction.SHARE -> sharePaper(context, paper)
            SwipeAction.DOWNLOAD -> run { container.pdfRepository.enqueueOfflineDownload(paper.id); scope.launch { snackbar.showSnackbar("PDF download queued") } }
            SwipeAction.MORE -> { more = paper }
            SwipeAction.NONE -> Unit
        }
    }
}
internal val LocalPaperActions = staticCompositionLocalOf<PaperActions> { error("Paper actions host required") }

@Composable
internal fun PaperActionsHost(container: AppContainer, content: @Composable () -> Unit) {
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val actions = remember(container) { PaperActions(container, scope, snackbar) }
    val preferences by container.preferencesRepository.preferences.collectAsStateWithLifecycle(AppPreferences())
    val records by container.favoriteRepository.records.collectAsStateWithLifecycle(emptyList())
    val groups by container.favoriteRepository.groups.collectAsStateWithLifecycle(emptyList())
    val tags by container.favoriteRepository.tags.collectAsStateWithLifecycle(emptyList())
    val relations by container.favoriteRepository.relations.collectAsStateWithLifecycle(emptyList())
    SideEffect { actions.preferences = preferences; actions.records = records; actions.groups = groups; actions.tags = tags; actions.relations = relations }
    CompositionLocalProvider(LocalPaperActions provides actions) {
        Box(Modifier.fillMaxSize()) {
            content()
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 80.dp))
        }
        val context = LocalContext.current
        actions.more?.let { paper ->
            AlertDialog(onDismissRequest = { actions.more = null }, title = { Text("Paper actions") }, text = {
                Column { SwipeAction.entries.filter { it != SwipeAction.MORE && it != SwipeAction.NONE }.forEach { action ->
                    TextButton(onClick = { actions.more = null; actions.act(paper, action, context) }) {
                        Text(if (action == SwipeAction.FAVORITE) if (records.any { it.paperId == paper.id }) "Remove favorite" else "Add favorite" else action.label)
                    }
                } }
            }, confirmButton = { TextButton(onClick = { actions.more = null }) { Text("Close") } })
        }
        actions.organizing?.let { paper -> key(paper.id) { OrganizationDialog(actions, paper) } }
        if (actions.managing) OrganizationManager(actions)
    }
}

@Composable
internal fun SwipePaperCard(paper: Paper, onPaperClick: (Paper) -> Unit) {
    val actions = LocalPaperActions.current
    val context = LocalContext.current
    var displacement by remember(paper.id) { mutableFloatStateOf(0f) }
    var width by remember { mutableIntStateOf(1) }
    val left = actions.preferences.swipeLeft
    val right = actions.preferences.swipeRight
    val action = if (displacement > 0) right else left
    Box(Modifier.fillMaxWidth().onSizeChanged { width = it.width }.semantics {
        customActions = SwipeAction.entries.filter { it != SwipeAction.NONE }.map { value ->
            CustomAccessibilityAction(value.label) { actions.act(paper, value, context); true }
        }
    }.pointerInput(paper.id, left, right) {
        var startedAtEdge = false
        detectHorizontalDragGestures(onDragStart = { position -> startedAtEdge = position.x < 24.dp.toPx() || position.x > size.width - 24.dp.toPx() },
            onDragCancel = { displacement = 0f }, onDragEnd = {
                val chosen = if (displacement > 0) right else left
                if (abs(displacement) >= width * .35f) actions.act(paper, chosen, context)
                displacement = 0f
            }) { change, amount ->
                val proposed = displacement + amount
                val enabled = (if (proposed > 0) right else left) != SwipeAction.NONE
                if (!startedAtEdge && enabled) { change.consume(); displacement = proposed.coerceIn(-width.toFloat(), width.toFloat()) }
            }
    }) {
        if (displacement != 0f) Row(Modifier.matchParentSize().padding(20.dp).background(MaterialTheme.colorScheme.secondaryContainer),
            horizontalArrangement = if (displacement > 0) Arrangement.Start else Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            Icon(when(action) { SwipeAction.FAVORITE -> Icons.Default.Bookmark; SwipeAction.SHARE -> Icons.Default.Share; SwipeAction.DOWNLOAD -> Icons.Default.Download; SwipeAction.ORGANIZE -> Icons.Default.Folder; else -> Icons.Default.MoreHoriz }, null)
            Text(action.label, Modifier.padding(8.dp))
        }
        Box(Modifier.offset { IntOffset(displacement.roundToInt(), 0) }) { PaperCard(paper, onPaperClick) }
    }
}

@Composable
private fun OrganizationDialog(actions: PaperActions, paper: Paper) {
    var groupId by remember { mutableStateOf(actions.records.firstOrNull { it.paperId == paper.id }?.groupId) }
    var selected by remember { mutableStateOf(actions.relations.filter { it.paperId == paper.id }.map { it.tagId }.toSet()) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = { if (!saving) actions.organizing = null }, title = { Text("Groups & tags") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Text("Group")
            ChoiceMenu("Group", listOf<Long?>(null) + actions.groups.map { it.id }, groupId,
                { id -> actions.groups.firstOrNull { it.id == id }?.name ?: "Ungrouped" }, { groupId = it })
            Text("Tags")
            actions.tags.forEach { tag -> Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(tag.id in selected, { selected = if (it) selected + tag.id else selected - tag.id }); Text(tag.name)
            } }
            TextButton(onClick = { actions.managing = true }) { Text("Manage groups & tags") }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }, confirmButton = { TextButton(enabled = !saving, onClick = {
        saving = true
        actions.scope.launch {
            try { actions.container.favoriteRepository.organize(paper, groupId, selected); actions.organizing = null }
            catch (e: Exception) { error = e.message ?: "Could not save"; saving = false }
        }
    }) { Text("Save") } }, dismissButton = { TextButton(onClick = { actions.organizing = null }, enabled = !saving) { Text("Cancel") } })
}

@Composable
internal fun <T> ChoiceMenu(label: String, values: List<T>, selected: T, text: (T) -> String, onSelect: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }) { Text("$label: ${text(selected)}") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            values.forEach { value -> DropdownMenuItem(text = { Text(text(value)) }, onClick = { onSelect(value); open = false }) }
        }
    }
}

@Composable
private fun OrganizationManager(actions: PaperActions) {
    var groupMode by remember { mutableStateOf(true) }
    var editingId by remember { mutableLongStateOf(0) }
    var name by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var pendingDelete by remember { mutableStateOf<Pair<Long, String>?>(null) }
    var busy by remember { mutableStateOf(false) }
    fun save() { busy = true; actions.scope.launch {
        try {
            if (groupMode) actions.container.favoriteRepository.saveGroup(editingId, name) else actions.container.favoriteRepository.saveTag(editingId, name)
            name = ""; editingId = 0; error = null
        } catch (_: android.database.sqlite.SQLiteConstraintException) { error = "This name already exists" }
        catch (e: Exception) { error = e.message ?: "Could not save" }
        finally { busy = false }
    } }
    AlertDialog(onDismissRequest = { actions.managing = false }, title = { Text("Manage groups & tags") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Row { TextButton(onClick = { groupMode = true; editingId = 0; name = "" }) { Text("Groups") }
                TextButton(onClick = { groupMode = false; editingId = 0; name = "" }) { Text("Tags") } }
            Text(if (groupMode) "Groups" else "Tags", style = MaterialTheme.typography.titleMedium)
            val entries = if (groupMode) actions.groups.map { it.id to it.name } else actions.tags.map { it.id to it.name }
            entries.forEach { (id, label) -> Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, Modifier.weight(1f))
                IconButton(onClick = { editingId = id; name = label }) { Icon(Icons.Default.Edit, "Rename $label") }
                IconButton(onClick = { pendingDelete = id to label }) { Icon(Icons.Default.Delete, "Delete $label") }
            } }
            OutlinedTextField(name, { name = it }, label = { Text(if (editingId == 0L) "New name" else "Rename") }, singleLine = true)
            Row { TextButton(onClick = { save() }, enabled = !busy) { Text("Save") }
                if (editingId != 0L) TextButton(onClick = { editingId = 0; name = "" }) { Text("Cancel rename") } }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }, confirmButton = { TextButton(onClick = { actions.managing = false }) { Text("Done") } })
    pendingDelete?.let { (id, label) -> AlertDialog(onDismissRequest = { pendingDelete = null }, title = { Text("Delete $label?") },
        text = { Text(if (groupMode) "Papers will become ungrouped." else "This tag will be removed from favorites.") },
        confirmButton = { TextButton(onClick = { val isGroup = groupMode; pendingDelete = null; actions.run {
            if (isGroup) actions.container.favoriteRepository.deleteGroup(id) else actions.container.favoriteRepository.deleteTag(id)
        } }) { Text("Delete") } }, dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancel") } }) }
}
