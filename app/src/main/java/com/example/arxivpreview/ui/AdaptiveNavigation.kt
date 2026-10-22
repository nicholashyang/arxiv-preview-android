package com.example.arxivpreview.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

internal data class Destination(val route: String, val label: String, val icon: ImageVector)

@Composable
internal fun AdaptiveNavigationScaffold(
    destinations: List<Destination>,
    currentRoute: String?,
    activeDestination: String,
    showBottomBar: Boolean,
    unread: Int,
    onNavigate: (Destination) -> Unit,
    content: @Composable (PaddingValues) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val reading = currentRoute?.startsWith("html/") == true || currentRoute?.startsWith("pdf/") == true
        val useRail = maxWidth >= 600.dp && maxHeight >= 480.dp && !reading
        val sidebar = maxWidth >= 1100.dp
        Scaffold(
            bottomBar = {
                if (showBottomBar && !useRail) {
                    Surface(color = MaterialTheme.colorScheme.surface.copy(alpha = .97f), modifier = Modifier.testTag("bottom-navigation")) {
                        Column {
                            HorizontalDivider(thickness = .5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                            Row(Modifier.fillMaxWidth().navigationBarsPadding().selectableGroup()) {
                                destinations.forEach { destination ->
                                    val selected = currentRoute == destination.route
                                    val tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    Column(Modifier.weight(1f).selectable(selected, role = Role.Tab, onClick = { onNavigate(destination) })
                                        .testTag("nav-${destination.route}").heightIn(min = 62.dp).padding(vertical = 8.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically)) {
                                        DestinationIcon(destination, unread, selected, tint)
                                        Text(destination.label, style = MaterialTheme.typography.labelSmall, color = tint)
                                    }
                                }
                            }
                        }
                    }
                }
            },
        ) { padding ->
            Row(Modifier.fillMaxSize()) {
                if (useRail) AdaptiveSinglePane(Modifier.width(if (sidebar) 228.dp else 80.dp).fillMaxHeight()) {
                    Surface(color = MaterialTheme.colorScheme.surface.copy(alpha = .7f)) {
                        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState())
                            .testTag("navigation-rail").padding(horizontal = 8.dp, vertical = 20.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            if (sidebar) Text("arXiV", Modifier.fillMaxWidth().padding(start = 12.dp, bottom = 24.dp), style = MaterialTheme.typography.headlineSmall)
                            destinations.forEach { destination ->
                                val selected = activeDestination == destination.route
                                val tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                Surface(color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = .1f) else Color.Transparent,
                                    shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().testTag("nav-${destination.route}")
                                        .selectable(selected, role = Role.Tab, onClick = { onNavigate(destination) })) {
                                    if (sidebar) Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        DestinationIcon(destination, unread, selected, tint)
                                        Text(destination.label, style = MaterialTheme.typography.bodyLarge, color = tint, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
                                    } else Column(Modifier.padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        DestinationIcon(destination, unread, selected, tint)
                                        Text(destination.label, style = MaterialTheme.typography.labelSmall, color = tint)
                                    }
                                }
                            }
                        }
                    }
                }
                if (useRail) Box(Modifier.width(.5.dp).fillMaxHeight().background(MaterialTheme.colorScheme.outlineVariant))
                Box(Modifier.weight(1f).fillMaxHeight()) { content(padding) }
            }
        }
    }
}

@Composable
private fun DestinationIcon(destination: Destination, unread: Int, selected: Boolean, tint: Color) {
    val outline = when (destination.route) {
        "home" -> Icons.Outlined.Home
        "explore" -> Icons.Outlined.Search
        "favorites" -> Icons.Outlined.Bookmarks
        "inbox" -> Icons.Outlined.Inbox
        else -> Icons.Outlined.Settings
    }
    BadgedBox(badge = {
        if (destination.route == "inbox" && unread > 0) Badge(containerColor = Color(0xFFFF3B30), contentColor = Color.White) {
            Text(if (unread > 99) "99+" else "$unread")
        }
    }) { Icon(if (selected) destination.icon else outline, contentDescription = destination.label, tint = tint, modifier = Modifier.size(25.dp)) }
}
