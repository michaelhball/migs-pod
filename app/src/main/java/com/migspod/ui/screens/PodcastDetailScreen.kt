package com.migspod.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.migspod.MigsPodApp as App
import com.migspod.data.Episode
import com.migspod.ui.components.LinkifiedText
import com.migspod.data.Podcast
import com.migspod.data.PodcastRepository
import com.migspod.player.PlayerController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PodcastDetailScreen(
    feedUrl: String,
    repository: PodcastRepository,
    onBack: () -> Unit,
    onEpisodeClick: (String) -> Unit,
) {
    val episodes by repository.getEpisodesForPodcast(feedUrl).collectAsStateWithLifecycle(emptyList())
    var podcast by remember { mutableStateOf<Podcast?>(null) }
    var isRefreshing by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var downloadingIds by remember { mutableStateOf(emptySet<String>()) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    LaunchedEffect(feedUrl) {
        podcast = withContext(Dispatchers.IO) { repository.getPodcast(feedUrl) }
    }

    // Clear the "Downloading…" hint once a download lands (downloadPath set).
    LaunchedEffect(episodes) {
        if (downloadingIds.isNotEmpty()) {
            downloadingIds = downloadingIds.filter { id ->
                episodes.find { it.id == id }?.downloadPath == null
            }.toSet()
        }
    }

    fun doRefresh() {
        scope.launch {
            isRefreshing = true
            repository.refreshPodcast(feedUrl)
            podcast = withContext(Dispatchers.IO) { repository.getPodcast(feedUrl) }
            isRefreshing = false
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(podcast?.title ?: "", maxLines = 1, overflow = TextOverflow.Ellipsis) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            },
            actions = {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "More")
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("Unsubscribe") },
                        onClick = {
                            showMenu = false
                            scope.launch {
                                podcast?.let { repository.unsubscribe(it) }
                                onBack()
                            }
                        }
                    )
                }
            }
        )

        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { doRefresh() },
            modifier = Modifier.fillMaxSize(),
        ) {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                // Podcast header as first item
                item(key = "header") {
                    podcast?.let { p -> PodcastHeader(podcast = p) }
                }

                // Episodes
                items(episodes, key = { it.id }, contentType = { "episode" }) { episode ->
                    EpisodeRow(
                        episode = episode,
                        repository = repository,
                        isDownloading = episode.id in downloadingIds,
                        onClick = { onEpisodeClick(episode.id) },
                        onDownload = {
                            downloadingIds = downloadingIds + episode.id
                            val appCtx = context.applicationContext
                            App.instance.appScope.launch {
                                repository.downloadEpisode(appCtx, episode.id)
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun PodcastHeader(podcast: Podcast) {
    var expanded by remember(podcast.feedUrl) { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.Top,
    ) {
        AsyncImage(
            model = podcast.artworkUrl,
            contentDescription = null,
            modifier = Modifier
                .size(100.dp)
                .clip(RoundedCornerShape(12.dp)),
            contentScale = ContentScale.Crop,
        )
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = podcast.author,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            LinkifiedText(
                text = podcast.description,
                style = MaterialTheme.typography.bodySmall,
                maxLines = if (expanded) Int.MAX_VALUE else 4,
                overflow = if (expanded) TextOverflow.Visible else TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.clickable { expanded = !expanded },
            )
        }
    }
}

@Composable
private fun EpisodeRow(
    episode: Episode,
    repository: PodcastRepository,
    isDownloading: Boolean,
    onClick: () -> Unit,
    onDownload: () -> Unit,
) {
    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }
    var menuOpen by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val separator = @Composable {
        Text(
            text = " · ",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 16.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = episode.title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface,
                // Dim listened episodes so they read as "done" at a glance.
                modifier = Modifier.alpha(if (episode.isCompleted) 0.5f else 1f),
            )
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (episode.isCompleted) {
                    Text(
                        text = "✓ Played",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    separator()
                }
                if (episode.downloadPath != null) {
                    Text(
                        text = "Downloaded",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    separator()
                } else if (isDownloading) {
                    Text(
                        text = "Downloading…",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    separator()
                }
                if (episode.publishedDate > 0) {
                    Text(
                        text = dateFormat.format(Date(episode.publishedDate)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (episode.durationMs > 0) {
                    Text(
                        text = " · ${formatDuration(episode.durationMs)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (episode.playbackPositionMs > 0 && !episode.isCompleted) {
                Spacer(Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = {
                        if (episode.durationMs > 0) {
                            episode.playbackPositionMs.toFloat() / episode.durationMs.toFloat()
                        } else 0f
                    },
                    modifier = Modifier
                        .fillMaxWidth(0.5f)
                        .height(3.dp),
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(Icons.Default.MoreVert, contentDescription = "Episode options")
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text(if (episode.playbackPositionMs > 0 && !episode.isCompleted) "Resume" else "Play") },
                    onClick = {
                        menuOpen = false
                        scope.launch { PlayerController.playEpisode(episode) }
                    },
                )
                if (episode.downloadPath == null && !isDownloading && episode.audioUrl.isNotEmpty()) {
                    DropdownMenuItem(
                        text = { Text("Download") },
                        onClick = {
                            menuOpen = false
                            onDownload()
                        },
                    )
                }
                if (episode.downloadPath != null) {
                    DropdownMenuItem(
                        text = { Text("Delete download") },
                        onClick = {
                            menuOpen = false
                            App.instance.appScope.launch { repository.deleteDownload(episode.id) }
                        },
                    )
                }
                DropdownMenuItem(
                    text = { Text(if (episode.isCompleted) "Mark unplayed" else "Mark played") },
                    onClick = {
                        menuOpen = false
                        App.instance.appScope.launch {
                            repository.markCompleted(episode.id, !episode.isCompleted)
                        }
                    },
                )
            }
        }
    }
}

private fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}
