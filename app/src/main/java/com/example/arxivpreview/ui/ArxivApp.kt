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
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Person
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
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
import androidx.compose.ui.platform.testTag
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
    Destination("home", "Home", Icons.Default.Home),
    Destination("explore", "Explore", Icons.Default.Search),
    Destination("favorites", "Library", Icons.Default.Bookmark),
    Destination("inbox", "Inbox", Icons.Default.Inbox),
    Destination("settings", "Profile", Icons.Default.Person),
)

@Composable
fun ArxivApp(
    application: Application,
    container: AppContainer,
    openInbox: Boolean = false,
    onInboxOpened: () -> Unit = {},
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
        else -> PaperActionsHost(container) { MainNavigation(application, container, openUpdates, onUpdatesOpened, openInbox, onInboxOpened) }
    }
}

@Composable
private fun MainNavigation(
    application: Application,
    container: AppContainer,
    openUpdates: Boolean,
    onUpdatesOpened: () -> Unit,
    openInbox: Boolean,
    onInboxOpened: () -> Unit,
) {
    val navController = rememberNavController()
    val discovery: DiscoveryViewModel = viewModel(factory = discoveryFactory { DiscoveryViewModel(container) })
    val unread by container.inboxRepository.unread.collectAsStateWithLifecycle(0)
    val navigatePaper: (com.example.arxivpreview.model.Paper) -> Unit = { navController.navigate("detail/${Uri.encode(it.id)}") }
    val navigateAuthor: (String) -> Unit = { navController.navigate("author/${Uri.encode(it)}") }
    LaunchedEffect(openInbox) { if (openInbox) { navController.navigate("inbox") { launchSingleTop = true }; onInboxOpened() } }
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

    CompositionLocalProvider(LocalDiscoveryNavigation provides DiscoveryNavigation(
        author = { paper, name -> navController.navigate("author-picker/${Uri.encode(paper.id)}/${Uri.encode(name)}") },
        paper = navigatePaper,
    )) {
    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = androidx.compose.ui.unit.Dp(0f)) {
                    destinations.forEach { destination ->
                        NavigationBarItem(
                            modifier = Modifier.testTag("nav-${destination.route}"),
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
                            icon = { BadgedBox(badge = { if (destination.route == "inbox" && unread > 0) Badge { Text(if (unread > 99) "99+" else "$unread") } }) { Icon(destination.icon, contentDescription = destination.label) } },
                            label = { Text(destination.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = "home",
            modifier = Modifier.fillMaxSize(),
        ) {
            composable("home") { HomeScreen(discovery, padding, { navController.navigate("research-preferences") }, navigatePaper) }
            composable("explore") {
                ExploreScreen(container, padding, navigatePaper, navigateAuthor) { category ->
                    navController.navigate("latest/${Uri.encode(category.code)}")
                }
            }
            composable("latest/{category}", arguments = listOf(navArgument("category") { type = NavType.StringType })) { entry ->
                val code = entry.arguments?.getString("category").orEmpty()
                val category = com.example.arxivpreview.model.ArxivCategories.all.first { it.code == code }
                val vm: LatestViewModel = viewModel(key = "latest-$code", factory = ViewModelFactories.latest(code, container))
                CategoryLatestScreen(vm, category, { navController.navigateUp() }, navigatePaper)
            }
            composable("inbox") { InboxScreen(discovery, padding, navigatePaper) }
            composable("research-preferences") { ResearchPreferencesScreen(discovery, { navController.navigateUp() }, navigateAuthor) }
            composable("author/{authorId}", arguments = listOf(navArgument("authorId") { type = NavType.StringType })) { entry ->
                val id = entry.arguments?.getString("authorId").orEmpty()
                val vm: AuthorViewModel = viewModel(key = "author-$id", factory = discoveryFactory { AuthorViewModel(id, container) })
                AuthorScreen(vm, { navController.navigateUp() }, navigatePaper)
            }
            composable("author-picker/{paperId}/{name}", arguments = listOf(navArgument("paperId") { type = NavType.StringType }, navArgument("name") { type = NavType.StringType })) { entry ->
                AuthorPickerScreen(container, entry.arguments?.getString("paperId").orEmpty(), entry.arguments?.getString("name").orEmpty(), { navController.navigateUp() }, navigateAuthor, navigatePaper)
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
                SettingsScreen(vm, padding, onResearchPreferences = { navController.navigate("research-preferences") }, openAbout = openAbout,
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
}
