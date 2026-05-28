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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.migspod.data.Episode
import com.migspod.data.Podcast
import com.migspod.data.PodcastRepository
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
    val scope = rememberCoroutineScope()

    LaunchedEffect(feedUrl) {
        podcast = withContext(Dispatchers.IO) { repository.getPodcast(feedUrl) }
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
                        onClick = { onEpisodeClick(episode.id) },
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
            Text(
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
    onClick: () -> Unit,
) {
    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = episode.title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = if (episode.isCompleted) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (episode.downloadPath != null) {
                    Text(
                        text = "Downloaded",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = " · ",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
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
    }
}

private fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}
