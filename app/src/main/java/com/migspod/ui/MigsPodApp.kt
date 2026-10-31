package com.migspod.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.RssFeed
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.migspod.MigsPodApp as App
import com.migspod.player.PlaybackState
import com.migspod.ui.screens.DownloadsScreen
import com.migspod.ui.screens.EpisodeDetailSheet
import com.migspod.ui.screens.LibraryScreen
import com.migspod.ui.screens.NewEpisodesScreen
import com.migspod.ui.screens.NowPlayingScreen
import com.migspod.ui.screens.PodcastDetailScreen
import com.migspod.ui.screens.SearchScreen
import com.migspod.ui.components.BatteryOptimizationNudge
import com.migspod.ui.components.MiniPlayer
import java.net.URLDecoder
import java.net.URLEncoder

private data class EpisodeSheetTarget(val episodeId: String, val showDeleteAction: Boolean)

sealed class Screen(val route: String, val label: String) {
    object Library : Screen("library", "Library")
    object NewEpisodes : Screen("new_episodes", "New")
    object Downloads : Screen("downloads", "Downloads")
    object Search : Screen("search", "Search")
    object PodcastDetail : Screen("podcast/{feedUrl}", "Podcast") {
        fun createRoute(feedUrl: String): String {
            return "podcast/${URLEncoder.encode(feedUrl, "UTF-8")}"
        }
    }
    object NowPlaying : Screen("now_playing", "Now Playing")
}

private val bottomBarRoutes = listOf(
    Screen.Search.route, Screen.Library.route,
    Screen.NewEpisodes.route, Screen.Downloads.route,
)

// Standard bottom-nav behaviour: each tab keeps its state (scroll, search results)
// and re-tapping the current tab doesn't stack a new copy.
private fun NavController.navigateToTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

private fun NavController.openNowPlaying() {
    navigate(Screen.NowPlaying.route) { launchSingleTop = true }
}

// Back can fire twice (button + swipe, or a swipe racing the episode ending); a
// second blind pop would take the screen underneath with it.
private fun NavController.popIfCurrent(route: String) {
    if (currentDestination?.route == route) popBackStack()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MigsPodApp() {
    val navController = rememberNavController()
    val repository = App.instance.repository
    val currentEpisodeId by PlaybackState.currentEpisodeId.collectAsStateWithLifecycle()
    var sheetTarget by remember { mutableStateOf<EpisodeSheetTarget?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val showBottomBar = currentRoute in bottomBarRoutes

    // The mini-player shows everywhere except Now Playing itself, including the
    // podcast screen (where the tab bar is hidden) so playback started there is visible.
    val showMiniPlayer = currentEpisodeId != null && currentRoute != Screen.NowPlaying.route

    Scaffold(
        bottomBar = {
            Column {
                if (showMiniPlayer) {
                    MiniPlayer(
                        repository = repository,
                        onClick = { navController.openNowPlaying() },
                        // With no tab bar below, it must clear the system gesture area itself.
                        windowInsets = if (showBottomBar) WindowInsets(0) else WindowInsets.navigationBars,
                    )
                }
                if (showBottomBar) {
                    NavigationBar {
                        NavigationBarItem(
                            icon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                            label = { Text("Search") },
                            selected = currentRoute == Screen.Search.route,
                            onClick = { navController.navigateToTab(Screen.Search.route) }
                        )
                        NavigationBarItem(
                            icon = { Icon(Icons.Default.Home, contentDescription = "Library") },
                            label = { Text("Library") },
                            selected = currentRoute == Screen.Library.route,
                            onClick = { navController.navigateToTab(Screen.Library.route) }
                        )
                        NavigationBarItem(
                            icon = { Icon(Icons.Default.RssFeed, contentDescription = "New") },
                            label = { Text("New") },
                            selected = currentRoute == Screen.NewEpisodes.route,
                            onClick = { navController.navigateToTab(Screen.NewEpisodes.route) }
                        )
                        NavigationBarItem(
                            icon = { Icon(Icons.Default.Download, contentDescription = "Downloads") },
                            label = { Text("Downloads") },
                            selected = currentRoute == Screen.Downloads.route,
                            onClick = { navController.navigateToTab(Screen.Downloads.route) }
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            NavHost(
                navController = navController,
                startDestination = Screen.Library.route,
            ) {
                composable(Screen.Library.route) {
                    LibraryScreen(
                        repository = repository,
                        onPodcastClick = { feedUrl ->
                            navController.navigate(Screen.PodcastDetail.createRoute(feedUrl))
                        }
                    )
                }
                composable(Screen.NewEpisodes.route) {
                    NewEpisodesScreen(
                        repository = repository,
                        onEpisodeClick = { episodeId ->
                            sheetTarget = EpisodeSheetTarget(episodeId, showDeleteAction = true)
                        },
                    )
                }
                composable(Screen.Downloads.route) {
                    DownloadsScreen(
                        repository = repository,
                        onEpisodeClick = { episodeId ->
                            sheetTarget = EpisodeSheetTarget(episodeId, showDeleteAction = false)
                        },
                    )
                }
                composable(Screen.Search.route) {
                    SearchScreen(
                        repository = repository,
                        onSubscribed = {
                            navController.navigate(Screen.Library.route) {
                                popUpTo(Screen.Library.route) { inclusive = true }
                            }
                        },
                        onResultClick = { feedUrl ->
                            navController.navigate(Screen.PodcastDetail.createRoute(feedUrl))
                        },
                    )
                }
                composable(
                    route = Screen.PodcastDetail.route,
                    arguments = listOf(navArgument("feedUrl") { type = NavType.StringType })
                ) { backStackEntry ->
                    val feedUrl = URLDecoder.decode(
                        backStackEntry.arguments?.getString("feedUrl") ?: "",
                        "UTF-8"
                    )
                    PodcastDetailScreen(
                        feedUrl = feedUrl,
                        repository = repository,
                        onBack = { navController.popIfCurrent(Screen.PodcastDetail.route) },
                        onEpisodeClick = { episodeId ->
                            sheetTarget = EpisodeSheetTarget(episodeId, showDeleteAction = true)
                        },
                    )
                }
                composable(Screen.NowPlaying.route) {
                    NowPlayingScreen(
                        repository = repository,
                        onBack = { navController.popIfCurrent(Screen.NowPlaying.route) },
                        onShowDetails = {
                            currentEpisodeId?.let { id ->
                                sheetTarget = EpisodeSheetTarget(id, showDeleteAction = true)
                            }
                        },
                    )
                }
            }
        }
    }

    // One-time prompt to exempt the app from battery optimization so
    // background playback survives the app being swiped from recents.
    BatteryOptimizationNudge()

    // Episode detail bottom sheet
    val target = sheetTarget
    if (target != null) {
        EpisodeDetailSheet(
            episodeId = target.episodeId,
            repository = repository,
            sheetState = sheetState,
            showDeleteAction = target.showDeleteAction,
            onDismiss = { sheetTarget = null },
            onNowPlaying = {
                sheetTarget = null
                navController.openNowPlaying()
            },
        )
    }
}
