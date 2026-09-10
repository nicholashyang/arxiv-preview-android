package com.example.arxivpreview.ui

import android.app.Application
import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.arxivpreview.AppContainer

private data class Destination(
    val route: String,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
)

private val destinations = listOf(
    Destination("latest", "Latest", Icons.Default.Home),
    Destination("search", "Search", Icons.Default.Search),
    Destination("favorites", "Favorites", Icons.Default.Bookmark),
    Destination("settings", "Settings", Icons.Default.Settings),
)

@Composable
fun ArxivApp(
    application: Application,
    container: AppContainer,
    openUpdates: Boolean = false,
    onUpdatesOpened: () -> Unit = {},
) {
    val mainViewModel: MainViewModel = viewModel(factory = ViewModelFactories.main(container))
    val preferences by mainViewModel.preferences.collectAsStateWithLifecycle()
    when {
        preferences == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        preferences?.onboardingComplete != true -> {
            val onboarding: OnboardingViewModel = viewModel(
                factory = ViewModelFactories.onboarding(container, application),
            )
            OnboardingScreen(onboarding)
        }
        else -> PaperActionsHost(container) { MainNavigation(application, container, openUpdates, onUpdatesOpened) }
    }
}

@Composable
private fun MainNavigation(
    application: Application,
    container: AppContainer,
    openUpdates: Boolean,
    onUpdatesOpened: () -> Unit,
) {
    val navController = rememberNavController()
    val mainViewModel: MainViewModel = viewModel(factory = ViewModelFactories.main(container))
    val update by mainViewModel.appUpdate.collectAsStateWithLifecycle()
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    var openAbout by rememberSaveable { mutableStateOf(false) }
    var aboutVisible by remember { mutableStateOf(false) }
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val showBottomBar = destinations.any { it.route == currentRoute }
    LaunchedEffect(openUpdates) {
        if (openUpdates) {
            openAbout = true
            navController.navigate("settings") { launchSingleTop = true }
            onUpdatesOpened()
        }
    }

    val release = update.release
    LaunchedEffect(aboutVisible, release, update.promptDismissed) {
        if (aboutVisible && release != null && !update.promptDismissed) mainViewModel.dismissUpdatePrompt(release)
    }
    if (release != null && !update.promptDismissed && !openUpdates && !openAbout &&
        currentRoute != "settings" && currentRoute != null && lifecycleState.isAtLeast(Lifecycle.State.RESUMED)
    ) {
        AlertDialog(
            onDismissRequest = { mainViewModel.dismissUpdatePrompt(release) },
            title = { Text("arXiV ${release.version} is available") },
            text = { Text(if (update.readyToInstall) "An update is ready to install." else "A new version is available. Review it and download when you’re ready.") },
            confirmButton = {
                TextButton(onClick = {
                    mainViewModel.dismissUpdatePrompt(release)
                    openAbout = true
                    navController.navigate("settings") { launchSingleTop = true }
                }) { Text("View update") }
            },
            dismissButton = { TextButton(onClick = { mainViewModel.dismissUpdatePrompt(release) }) { Text("Later") } },
        )
    }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = androidx.compose.ui.unit.Dp(0f)) {
                    destinations.forEach { destination ->
                        NavigationBarItem(
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = Color.Transparent,
                            ),
                            selected = currentRoute == destination.route,
                            onClick = {
                                navController.navigate(destination.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(destination.icon, contentDescription = destination.label) },
                            label = { Text(destination.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = "latest",
            modifier = Modifier.fillMaxSize(),
        ) {
            composable("latest") {
                val vm: LatestViewModel = viewModel(
                    factory = ViewModelFactories.latest(container),
                )
                LatestScreen(
                    viewModel = vm,
                    contentPadding = padding,
                    onPaperClick = {
                        navController.navigate("detail/${Uri.encode(it.id)}")
                    },
                )
            }
            composable("search") {
                val vm: SearchViewModel = viewModel(
                    factory = ViewModelFactories.search(container),
                )
                SearchScreen(
                    viewModel = vm,
                    contentPadding = padding,
                    onPaperClick = {
                        navController.navigate("detail/${Uri.encode(it.id)}")
                    },
                )
            }
            composable("favorites") {
                val vm: FavoritesViewModel = viewModel(
                    factory = ViewModelFactories.favorites(container),
                )
                FavoritesScreen(
                    viewModel = vm,
                    contentPadding = padding,
                    onPaperClick = {
                        navController.navigate("detail/${Uri.encode(it.id)}")
                    },
                )
            }
            composable("settings") {
                val vm: SettingsViewModel = viewModel(
                    factory = ViewModelFactories.settings(container, application),
                )
                SettingsScreen(vm, padding, openAbout = openAbout,
                    onAboutOpened = { openAbout = false },
                    onAboutVisible = { aboutVisible = it })
            }
            composable(
                route = "detail/{paperId}",
                arguments = listOf(navArgument("paperId") { type = NavType.StringType }),
            ) { entry ->
                val paperId = entry.arguments?.getString("paperId").orEmpty()
                val vm: DetailViewModel = viewModel(
                    key = "detail-$paperId",
                    factory = ViewModelFactories.detail(paperId, container),
                )
                DetailScreen(
                    viewModel = vm,
                    onBack = navController::navigateUp,
                    onReadHtml = { navController.navigate("html/${Uri.encode(paperId)}") },
                    onReadPdf = {
                        navController.navigate("pdf/${Uri.encode(paperId)}")
                    },
                )
            }
            composable(
                route = "html/{paperId}",
                arguments = listOf(navArgument("paperId") { type = NavType.StringType }),
            ) { entry ->
                val paperId = entry.arguments?.getString("paperId").orEmpty()
                val vm: DetailViewModel = viewModel(key = "html-$paperId", factory = ViewModelFactories.detail(paperId, container))
                val state by vm.state.collectAsStateWithLifecycle()
                HtmlScreen(state.paper, navController::navigateUp) {
                    navController.navigate("pdf/${Uri.encode(paperId)}")
                }
            }
            composable(
                route = "pdf/{paperId}",
                arguments = listOf(navArgument("paperId") { type = NavType.StringType }),
            ) { entry ->
                val paperId = entry.arguments?.getString("paperId").orEmpty()
                val vm: PdfViewModel = viewModel(
                    key = "pdf-$paperId",
                    factory = ViewModelFactories.pdf(paperId, container),
                )
                PdfScreen(vm, navController::navigateUp)
            }
        }
    }
}
