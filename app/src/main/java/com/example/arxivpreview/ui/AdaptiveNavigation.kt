package com.example.arxivpreview.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
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
        Scaffold(
            bottomBar = {
                if (showBottomBar && !useRail) {
                    NavigationBar(
                        modifier = Modifier.testTag("bottom-navigation"),
                        containerColor = MaterialTheme.colorScheme.surface,
                        tonalElevation = 0.dp,
                    ) {
                        destinations.forEach { destination ->
                            NavigationBarItem(
                                modifier = Modifier.testTag("nav-${destination.route}"),
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.primary,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    indicatorColor = Color.Transparent,
                                ),
                                selected = currentRoute == destination.route,
                                onClick = { onNavigate(destination) },
                                icon = { DestinationIcon(destination, unread) },
                                label = { Text(destination.label) },
                            )
                        }
                    }
                }
            },
        ) { padding ->
            Row(Modifier.fillMaxSize()) {
                if (useRail) AdaptiveSinglePane(Modifier.width(80.dp).fillMaxHeight()) {
                    NavigationRail(
                        modifier = Modifier.fillMaxHeight().verticalScroll(rememberScrollState()).testTag("navigation-rail"),
                    ) {
                        destinations.forEach { destination ->
                            NavigationRailItem(
                                modifier = Modifier.testTag("nav-${destination.route}"),
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.primary,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = .1f),
                                ),
                                selected = activeDestination == destination.route,
                                onClick = { onNavigate(destination) },
                                icon = { DestinationIcon(destination, unread) },
                                label = { Text(destination.label) },
                            )
                        }
                    }
                }
                Box(Modifier.weight(1f).fillMaxHeight()) { content(padding) }
            }
        }
    }
}

@Composable
private fun DestinationIcon(destination: Destination, unread: Int) {
    BadgedBox(badge = {
        if (destination.route == "inbox" && unread > 0) Badge { Text(if (unread > 99) "99+" else "$unread") }
    }) { Icon(destination.icon, contentDescription = destination.label) }
}
