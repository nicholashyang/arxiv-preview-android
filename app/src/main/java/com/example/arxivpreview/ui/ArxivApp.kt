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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
fun ArxivApp(application: Application, container: AppContainer) {
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
        else -> MainNavigation(application, container)
    }
}

@Composable
private fun MainNavigation(application: Application, container: AppContainer) {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val showBottomBar = destinations.any { it.route == currentRoute }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    destinations.forEach { destination ->
                        NavigationBarItem(
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
                SettingsScreen(vm, padding)
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
                    onReadPdf = {
                        navController.navigate("pdf/${Uri.encode(paperId)}")
                    },
                )
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
