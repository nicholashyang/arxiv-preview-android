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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.NavBackStackEntry
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.arxivpreview.AppContainer
import com.example.arxivpreview.model.Paper

private val destinations = listOf(
    Destination("home", "Home", Icons.Default.Home),
    Destination("explore", "Explore", Icons.Default.Search),
    Destination("favorites", "Library", Icons.Default.Bookmark),
    Destination("inbox", "Inbox", Icons.Default.Inbox),
    Destination("settings", "Setting", Icons.Default.Settings),
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
    FoldAwareWindow {
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
                AdaptiveSinglePane { OnboardingScreen(onboarding) }
            }
            else -> PaperActionsHost(container) { MainNavigation(application, container, openUpdates, onUpdatesOpened, openInbox, onInboxOpened) }
        }
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
    val mainViewModel: MainViewModel = viewModel(factory = ViewModelFactories.main(container))
    val update by mainViewModel.appUpdate.collectAsStateWithLifecycle()
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    var openAbout by rememberSaveable { mutableStateOf(false) }
    var aboutVisible by remember { mutableStateOf(false) }
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    var activeDestination by rememberSaveable { mutableStateOf("home") }
    LaunchedEffect(currentRoute) {
        if (destinations.any { it.route == currentRoute }) activeDestination = currentRoute!!
    }
    var selections by rememberSaveable(stateSaver = listSaver<Map<String, String>, String>(
        save = { it.flatMap { (key, value) -> listOf(key, value) } },
        restore = { it.chunked(2).associate { pair -> pair[0] to pair[1] } },
    )) { mutableStateOf(emptyMap()) }
    LaunchedEffect(openInbox) {
        if (openInbox) {
            navController.navigate("inbox") { launchSingleTop = true }
            navController.currentBackStackEntry?.id?.let { selections = selections - it }
            onInboxOpened()
        }
    }
    val browserNavigation = PaperBrowserNavigation(
        selections = selections,
        select = { entryId, paperId ->
            selections = if (paperId == null) selections - entryId else selections + (entryId to paperId)
        },
        readHtml = { navController.navigate("html/${Uri.encode(it)}") },
        readPdf = { navController.navigate("pdf/${Uri.encode(it)}") },
    )
    val showBottomBar = destinations.any { it.route == currentRoute } && selections[backStack?.id] == null
    val navigateDestination: (Destination) -> Unit = { destination ->
        navController.navigate(destination.route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }
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
            title = { Text("An arXiV update is available") },
            text = { Text(if (update.readyToInstall) "An update is ready to install." else "Review the update and download when you’re ready.") },
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

    CompositionLocalProvider(LocalPaperBrowserNavigation provides browserNavigation, LocalDiscoveryNavigation provides DiscoveryNavigation(
        author = { paper, name -> navController.navigate("author-picker/${Uri.encode(paper.id)}/${Uri.encode(name)}") },
        paper = navigatePaper,
    )) {
        AdaptiveNavigationScaffold(
            destinations, currentRoute, activeDestination, showBottomBar, unread, navigateDestination,
        ) { padding ->
            NavHost(
                navController = navController,
                startDestination = "home",
                modifier = Modifier.fillMaxSize(),
            ) {
                composable("home") {
                    AdaptiveSinglePane {
                        HomeScreen(discovery, padding, { navController.navigate("settings") }) { category ->
                            navController.navigate("latest/${Uri.encode(category.code)}")
                        }
                    }
                }
                composable("explore") { entry ->
                    PaperBrowserDestination(entry, container) { onPaper ->
                        ExploreScreen(container, padding, onPaper, navigateAuthor) { category ->
                            navController.navigate("latest/${Uri.encode(category.code)}")
                        }
                    }
                }
                composable("latest/{category}", arguments = listOf(navArgument("category") { type = NavType.StringType })) { entry ->
                    val code = entry.arguments?.getString("category").orEmpty()
                    val category = com.example.arxivpreview.model.ArxivCategories.all.first { it.code == code }
                    val vm: LatestViewModel = viewModel(key = "latest-$code", factory = ViewModelFactories.latest(code, container))
                    PaperBrowserDestination(entry, container) { onPaper ->
                        CategoryLatestScreen(vm, category, { navController.navigateUp() }, onPaper)
                    }
                }
                composable("inbox") { entry ->
                    PaperBrowserDestination(entry, container) { onPaper -> InboxScreen(discovery, padding, onPaper) }
                }
                composable("research-preferences") { AdaptiveSinglePane { ResearchPreferencesScreen(discovery, { navController.navigateUp() }, navigateAuthor) } }
                composable("author/{authorId}", arguments = listOf(navArgument("authorId") { type = NavType.StringType })) { entry ->
                    val id = entry.arguments?.getString("authorId").orEmpty()
                    val vm: AuthorViewModel = viewModel(key = "author-$id", factory = discoveryFactory { AuthorViewModel(id, container) })
                    PaperBrowserDestination(entry, container) { onPaper -> AuthorScreen(vm, { navController.navigateUp() }, onPaper) }
                }
                composable("author-picker/{paperId}/{name}", arguments = listOf(navArgument("paperId") { type = NavType.StringType }, navArgument("name") { type = NavType.StringType })) { entry ->
                    PaperBrowserDestination(entry, container) { onPaper ->
                        AuthorPickerScreen(container, entry.arguments?.getString("paperId").orEmpty(), entry.arguments?.getString("name").orEmpty(), { navController.navigateUp() }, navigateAuthor, onPaper)
                    }
                }
                composable("favorites") { entry ->
                    val vm: FavoritesViewModel = viewModel(
                        factory = ViewModelFactories.favorites(container),
                    )
                    PaperBrowserDestination(entry, container) { onPaper ->
                        FavoritesScreen(vm, padding, onPaper)
                    }
                }
                composable("settings") {
                    val vm: SettingsViewModel = viewModel(
                        factory = ViewModelFactories.settings(container, application),
                    )
                    AdaptiveSinglePane { SettingsScreen(vm, padding, onResearchPreferences = { navController.navigate("research-preferences") }, openAbout = openAbout,
                        onAboutOpened = { openAbout = false },
                        onAboutVisible = { aboutVisible = it }) }
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
                    AdaptiveSinglePane { DetailScreen(
                        viewModel = vm,
                        onBack = navController::navigateUp,
                        onReadHtml = { navController.navigate("html/${Uri.encode(paperId)}") },
                        onReadPdf = {
                            navController.navigate("pdf/${Uri.encode(paperId)}")
                        },
                    ) }
                }
                composable(
                    route = "html/{paperId}",
                    arguments = listOf(navArgument("paperId") { type = NavType.StringType }),
                ) { entry ->
                    val paperId = entry.arguments?.getString("paperId").orEmpty()
                    val vm: DetailViewModel = viewModel(key = "html-$paperId", factory = ViewModelFactories.detail(paperId, container))
                    val state by vm.state.collectAsStateWithLifecycle()
                    AdaptiveSinglePane(maxContentWidth = Dp.Infinity) { HtmlScreen(state.paper, navController::navigateUp) {
                        navController.navigate("pdf/${Uri.encode(paperId)}")
                    } }
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
                    AdaptiveSinglePane(maxContentWidth = Dp.Infinity) { PdfScreen(vm, navController::navigateUp) }
                }
            }
        }
    }
}

private data class PaperBrowserNavigation(
    val selections: Map<String, String>,
    val select: (String, String?) -> Unit,
    val readHtml: (String) -> Unit,
    val readPdf: (String) -> Unit,
)

private val LocalPaperBrowserNavigation = staticCompositionLocalOf<PaperBrowserNavigation> { error("Missing paper browser navigation") }

@Composable
private fun PaperBrowserDestination(
    entry: NavBackStackEntry,
    container: AppContainer,
    list: @Composable ((Paper) -> Unit) -> Unit,
) {
    val navigation = LocalPaperBrowserNavigation.current
    val selectedId = navigation.selections[entry.id]
    val onPaper: (Paper) -> Unit = { navigation.select(entry.id, it.id) }
    val discovery = LocalDiscoveryNavigation.current
    val detailStore: PaperDetailStore = viewModel()
    detailStore.select(selectedId)
    CompositionLocalProvider(LocalDiscoveryNavigation provides discovery?.copy(paper = onPaper), LocalSelectedPaperId provides selectedId) {
        AdaptivePaperBrowser(selectedId, { navigation.select(entry.id, null) }, list = { list(onPaper) }) { id ->
            // Reset scrolling for a different paper and retain it through resizing.
            androidx.compose.runtime.key(id) {
                val vm: DetailViewModel = viewModel(viewModelStoreOwner = detailStore, factory = ViewModelFactories.detail(id, container))
                AdaptiveSinglePane {
                    DetailScreen(vm, { navigation.select(entry.id, null) }, { navigation.readPdf(id) }, { navigation.readHtml(id) })
                }
            }
        }
    }
}

/** Retain one detail model through resizing, rather than every paper ever opened. */
internal class PaperDetailStore : ViewModel(), ViewModelStoreOwner {
    override val viewModelStore = ViewModelStore()
    private var selectedId: String? = null

    fun select(id: String?) {
        if (selectedId != id) {
            viewModelStore.clear()
            selectedId = id
        }
    }

    override fun onCleared() { viewModelStore.clear() }
}
