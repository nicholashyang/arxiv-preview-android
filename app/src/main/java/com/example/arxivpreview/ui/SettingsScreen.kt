package com.example.arxivpreview.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.arxivpreview.data.ThemeMode
import com.example.arxivpreview.model.ArxivCategories

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    contentPadding: PaddingValues,
    openAbout: Boolean = false,
    onAboutOpened: () -> Unit = {},
    onAboutVisible: (Boolean) -> Unit = {},
    onResearchPreferences: (() -> Unit)? = null,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Do not measure or restore a shorter placeholder list before preferences arrive.
    if (!state.preferences.onboardingComplete) {
        Box(Modifier.padding(contentPadding).fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }
    val context = LocalContext.current
    val listState = rememberLazyListState()
    var notificationsAllowed by remember { mutableStateOf(false) }
    var installsAllowed by remember { mutableStateOf(false) }
    var permissionError by remember { mutableStateOf<String?>(null) }
    fun refreshPermissions() {
        notificationsAllowed = NotificationManagerCompat.from(context).areNotificationsEnabled()
        installsAllowed = context.packageManager.canRequestPackageInstalls()
    }
    LifecycleResumeEffect(Unit) {
        refreshPermissions()
        onPauseOrDispose { }
    }
    val systemSettings = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        refreshPermissions()
    }
    fun openSettings(action: String) {
        val intent = if (action == Settings.ACTION_APP_NOTIFICATION_SETTINGS) {
            Intent(action).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        } else Intent(action, "package:${context.packageName}".toUri())
        try {
            systemSettings.launch(intent)
            permissionError = null
        } catch (_: Exception) {
            try {
                systemSettings.launch(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri()))
            } catch (_: Exception) {
                permissionError = "Could not open Android Settings. Open the app’s settings from your device settings."
            }
        }
    }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        viewModel.setNotifications(granted)
        refreshPermissions()
    }
    LaunchedEffect(openAbout) {
        if (openAbout) {
            // The category group changes height when DataStore loads on a cold launch.
            withFrameNanos { }
            listState.scrollToItem(5)
            onAboutOpened()
        }
    }
    val aboutVisible by remember {
        derivedStateOf { listState.layoutInfo.visibleItemsInfo.any { it.key == "about" } }
    }
    LaunchedEffect(aboutVisible) { onAboutVisible(aboutVisible) }
    DisposableEffect(Unit) { onDispose { onAboutVisible(false) } }

    Column(Modifier.padding(contentPadding).fillMaxSize()) {
        AppLargeTitle("Settings", actions = {
            onResearchPreferences?.let { TextButton(onClick = it) { Text("Research") } }
        })
        LazyColumn(state = listState, modifier = Modifier.weight(1f).testTag("settings-list"),
            contentPadding = PaddingValues(bottom = 24.dp)) {
            item(key = "appearance") {
                SettingsGroup("Appearance") {
                    ThemeMode.entries.forEachIndexed { index, mode ->
                        Row(Modifier.fillMaxWidth().clickable { viewModel.setThemeMode(mode) }
                            .heightIn(min = 52.dp).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(if (mode == ThemeMode.SYSTEM) "Follow system" else mode.label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                            if (state.preferences.themeMode == mode) Icon(Icons.Default.Check, "Selected", Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
                        }
                        if (index < ThemeMode.entries.lastIndex) HorizontalDivider(Modifier.padding(start = 16.dp), thickness = .5.dp)
                    }
                }
            }
            item(key = "swipes") {
                SettingsGroup("Swipe actions") {
                    Column(Modifier.padding(horizontal = 16.dp)) {
                        ChoiceMenu("Swipe left", com.example.arxivpreview.data.SwipeAction.entries, state.preferences.swipeLeft, { it.label }, { viewModel.setSwipe(true, it) })
                        ChoiceMenu("Swipe right", com.example.arxivpreview.data.SwipeAction.entries, state.preferences.swipeRight, { it.label }, { viewModel.setSwipe(false, it) })
                    }
                }
            }
            item(key = "notifications") {
                SettingsGroup("Notifications") {
                    ListItem(
                        leadingContent = { SettingsIcon(Icons.Default.Notifications, Color(0xFFFF3B30)) },
                        headlineContent = { Text("Daily update notifications") },
                        supportingContent = { Text("A summary when new submissions are available") },
                        trailingContent = {
                            AppSwitch(checked = state.preferences.notificationsEnabled, onCheckedChange = { enabled ->
                                when {
                                    !enabled -> viewModel.setNotifications(false)
                                    notificationsAllowed -> viewModel.setNotifications(true)
                                    Build.VERSION.SDK_INT >= 33 -> notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    else -> {
                                        viewModel.setNotifications(true)
                                        openSettings(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                    }
                                }
                            })
                        },
                    )
                    if (!notificationsAllowed) {
                        Text("System notifications are off. App update reminders are still shown in the app.",
                            Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = { openSettings(Settings.ACTION_APP_NOTIFICATION_SETTINGS) }) {
                            Text("Manage notifications")
                        }
                    }
                }
            }
            item(key = "permissions") {
                SettingsGroup("App permissions") {
                    PermissionRow("Notifications", notificationsAllowed) { openSettings(Settings.ACTION_APP_NOTIFICATION_SETTINGS) }
                    HorizontalDivider(Modifier.padding(start = 16.dp))
                    PermissionRow("Install unknown apps", installsAllowed) { openSettings(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES) }
                    HorizontalDivider(Modifier.padding(start = 16.dp))
                    ListItem(headlineContent = { Text("App info") }, leadingContent = { SettingsIcon(Icons.Default.Info, Color(0xFF8E8E93)) },
                        supportingContent = { Text("Manage this app in Android Settings") },
                        trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) },
                        modifier = Modifier.clickable { openSettings(Settings.ACTION_APPLICATION_DETAILS_SETTINGS) })
                    permissionError?.let { Text(it, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error) }
                }
            }
            item(key = "content") {
                SettingsGroup("Content preferences") {
                    CategoryPreferences(state.preferences.categories, viewModel::saveCategories)
                }
            }
            item(key = "about") {
                SettingsGroup("About arXiv") { AppUpdateSettings(state, viewModel) }
            }
        }
    }
}

@Composable
private fun SettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Text(title.uppercase(), Modifier.padding(start = 36.dp, top = 20.dp, end = 24.dp, bottom = 8.dp),
            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Surface(Modifier.padding(horizontal = 20.dp).fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
            Column(content = content)
        }
    }
}

@Composable
private fun PermissionRow(title: String, allowed: Boolean, onClick: () -> Unit) {
    ListItem(headlineContent = { Text(title) },
        leadingContent = { SettingsIcon(if (title == "Notifications") Icons.Default.Notifications else Icons.Default.Download, if (title == "Notifications") Color(0xFFFF3B30) else Color(0xFF34C759)) },
        supportingContent = { Text(if (allowed) "Allowed" else "Not allowed") },
        trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) },
        modifier = Modifier.clickable(onClick = onClick))
}

@Composable
internal fun CategoryPreferences(categories: Set<String>, onSave: (Set<String>) -> Unit) {
    var selected by rememberSaveable { mutableStateOf<List<String>>(categories.toList()) }
    var baseline by rememberSaveable { mutableStateOf<List<String>>(categories.toList()) }
    var search by rememberSaveable { mutableStateOf("") }
    var expanded by rememberSaveable { mutableStateOf<List<String>>(emptyList()) }
    LaunchedEffect(categories) {
        // A persisted save updates the baseline without overwriting newer draft edits.
        if (selected.toSet() == baseline.toSet()) selected = ArrayList(categories)
        baseline = ArrayList(categories)
    }
    val groups = remember { ArxivCategories.all.groupBy { it.group } }
    val query = search.trim()
    Column {
        Text("Followed categories", Modifier.padding(16.dp), style = MaterialTheme.typography.titleMedium)
        AppSearchField(search, { search = it }, "Filter categories", Modifier.padding(horizontal = 16.dp).testTag("category-search"))
        val matches = groups.mapValues { (_, children) -> children.filter {
            query.isEmpty() || it.code.contains(query, true) || it.name.contains(query, true) || it.group.contains(query, true)
        } }.filterValues { it.isNotEmpty() }
        if (matches.isEmpty()) Text("No matching categories", Modifier.padding(16.dp))
        matches.forEach { (group, children) ->
            val isExpanded = query.isNotEmpty() || group in expanded
            ListItem(
                headlineContent = { Text(group) }, leadingContent = { SubjectIcon(group) },
                supportingContent = { Text("${groups.getValue(group).count { it.code in selected }} selected") },
                trailingContent = { Icon(if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null) },
                modifier = Modifier.testTag("category-group-$group")
                    .semantics { stateDescription = if (isExpanded) "Expanded" else "Collapsed" }
                    .clickable(enabled = query.isEmpty()) {
                        expanded = ArrayList(if (group in expanded) expanded - group else expanded + group)
                    },
            )
            if (isExpanded) children.forEach { category ->
                CategoryRow(category, category.code in selected) {
                    selected = ArrayList(if (category.code in selected) selected - category.code else selected + category.code)
                }
            }
            HorizontalDivider(Modifier.padding(horizontal = 16.dp))
        }
        if (selected.isEmpty()) Text("Choose at least one category.", Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall)
        Button(onClick = { onSave(selected.toSet()) },
            enabled = selected.isNotEmpty() && selected.toSet() != categories,
            modifier = Modifier.fillMaxWidth().padding(16.dp).testTag("save-categories")) {
            Text("Save ${selected.size} categories")
        }
    }
}

@Composable
private fun SettingsIcon(icon: ImageVector, color: Color) {
    Surface(color = color, shape = RoundedCornerShape(7.dp)) {
        Icon(icon, null, Modifier.padding(5.dp).size(20.dp), tint = Color.White)
    }
}
