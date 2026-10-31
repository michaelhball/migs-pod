package com.migspod.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.migspod.data.Episode
import com.migspod.data.Podcast
import com.migspod.data.PodcastRepository
import com.migspod.ui.startDownload
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewEpisodesScreen(
    repository: PodcastRepository,
    onEpisodeClick: (String) -> Unit,
) {
    val episodes by repository.getRecentEpisodes().collectAsStateWithLifecycle(emptyList())
    var isRefreshing by remember { mutableStateOf(false) }
    val downloadingIds by repository.downloadingIds.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    // Cache podcast metadata for display — single batch query
    var podcastCache by remember { mutableStateOf<Map<String, Podcast>>(emptyMap()) }
    LaunchedEffect(episodes) {
        val feedUrls = episodes.map { it.podcastFeedUrl }.distinct()
        val missing = feedUrls.filter { it !in podcastCache }
        if (missing.isNotEmpty()) {
            val fetched = repository.getPodcasts(missing)
            podcastCache = podcastCache + fetched
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("New Episodes") })

        if (episodes.isEmpty() && !isRefreshing) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "No episodes yet",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Subscribe to podcasts and pull down to refresh",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 48.dp),
                )
            }
        } else {
            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = {
                    scope.launch {
                        isRefreshing = true
                        repository.refreshAllPodcasts()
                        isRefreshing = false
                    }
                },
                modifier = Modifier.fillMaxSize(),
            ) {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(episodes, key = { it.id }, contentType = { "episode" }) { episode ->
                        val podcast = podcastCache[episode.podcastFeedUrl]
                        NewEpisodeRow(
                            episode = episode,
                            podcastTitle = podcast?.title ?: "",
                            podcastArtworkUrl = podcast?.artworkUrl ?: "",
                            isDownloading = episode.id in downloadingIds,
                            onClick = { onEpisodeClick(episode.id) },
                            onDownload = { startDownload(repository, episode.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NewEpisodeRow(
    episode: Episode,
    podcastTitle: String,
    podcastArtworkUrl: String,
    isDownloading: Boolean,
    onClick: () -> Unit,
    onDownload: () -> Unit,
) {
    val dateFormat = remember { SimpleDateFormat("MMM d", Locale.getDefault()) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = podcastArtworkUrl,
            contentDescription = null,
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Crop,
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = episode.title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.alpha(if (episode.isCompleted) 0.5f else 1f),
            )
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (episode.isCompleted) {
                    Text(
                        text = "✓ Played · ",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Text(
                    text = podcastTitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (episode.publishedDate > 0) {
                    Text(
                        text = " · ${dateFormat.format(Date(episode.publishedDate))}",
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
                        .fillMaxWidth(0.4f)
                        .height(3.dp),
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        if (episode.downloadPath != null) {
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = "Downloaded",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
        } else if (isDownloading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
            )
        } else {
            IconButton(onClick = onDownload) {
                Icon(
                    Icons.Default.Download,
                    contentDescription = "Download",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
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
