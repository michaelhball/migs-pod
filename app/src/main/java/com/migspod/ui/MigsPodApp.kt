package com.migspod.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.migspod.MigsPodApp as App
import com.migspod.data.PodcastRepository
import com.migspod.player.PlayerController
import com.migspod.player.PlaybackState
import com.migspod.ui.screens.DownloadsScreen
import com.migspod.ui.screens.EpisodeDetailSheet
import com.migspod.ui.screens.LibraryScreen
import com.migspod.ui.screens.NewEpisodesScreen
import com.migspod.ui.screens.NowPlayingScreen
import com.migspod.ui.screens.PodcastDetailScreen
import com.migspod.ui.screens.SearchScreen
import com.migspod.ui.components.MiniPlayer
import java.net.URLDecoder
import java.net.URLEncoder

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MigsPodApp() {
    val context = LocalContext.current
    val navController = rememberNavController()
    val db = remember { App.instance.database }
    val repository = remember {
        PodcastRepository(db.podcastDao(), db.episodeDao())
    }
    val currentEpisodeId by PlaybackState.currentEpisodeId.collectAsStateWithLifecycle()
    var sheetEpisodeId by remember { mutableStateOf<String?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        PlayerController.connect(context)
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val showBottomBar = currentRoute in bottomBarRoutes

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                Column {
                    if (currentEpisodeId != null) {
                        MiniPlayer(
                            repository = repository,
                            onClick = { navController.navigate(Screen.NowPlaying.route) }
                        )
                    }
                    NavigationBar {
                        NavigationBarItem(
                            icon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                            label = { Text("Search") },
                            selected = currentRoute == Screen.Search.route,
                            onClick = {
                                navController.navigate(Screen.Search.route) {
                                    popUpTo(Screen.Library.route)
                                }
                            }
                        )
                        NavigationBarItem(
                            icon = { Icon(Icons.Default.Home, contentDescription = "Library") },
                            label = { Text("Library") },
                            selected = currentRoute == Screen.Library.route,
                            onClick = {
                                navController.navigate(Screen.Library.route) {
                                    popUpTo(Screen.Library.route) { inclusive = true }
                                }
                            }
                        )
                        NavigationBarItem(
                            icon = { Icon(Icons.Default.RssFeed, contentDescription = "New") },
                            label = { Text("New") },
                            selected = currentRoute == Screen.NewEpisodes.route,
                            onClick = {
                                navController.navigate(Screen.NewEpisodes.route) {
                                    popUpTo(Screen.Library.route)
                                }
                            }
                        )
                        NavigationBarItem(
                            icon = { Icon(Icons.Default.Download, contentDescription = "Downloads") },
                            label = { Text("Downloads") },
                            selected = currentRoute == Screen.Downloads.route,
                            onClick = {
                                navController.navigate(Screen.Downloads.route) {
                                    popUpTo(Screen.Library.route)
                                }
                            }
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
                        onEpisodeClick = { episodeId -> sheetEpisodeId = episodeId },
                    )
                }
                composable(Screen.Downloads.route) {
                    DownloadsScreen(
                        repository = repository,
                        onEpisodeClick = { episodeId -> sheetEpisodeId = episodeId },
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
                        onBack = { navController.popBackStack() },
                        onEpisodeClick = { episodeId -> sheetEpisodeId = episodeId },
                    )
                }
                composable(Screen.NowPlaying.route) {
                    NowPlayingScreen(
                        repository = repository,
                        onBack = { navController.popBackStack() }
                    )
                }
            }
        }
    }

    // Episode detail bottom sheet
    val episodeId = sheetEpisodeId
    if (episodeId != null) {
        EpisodeDetailSheet(
            episodeId = episodeId,
            repository = repository,
            sheetState = sheetState,
            onDismiss = { sheetEpisodeId = null },
            onNowPlaying = {
                sheetEpisodeId = null
                navController.navigate(Screen.NowPlaying.route)
            },
        )
    }
}
