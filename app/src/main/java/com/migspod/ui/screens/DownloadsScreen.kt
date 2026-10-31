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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.migspod.ui.deleteDownload
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class PodcastGroup(
    val podcast: Podcast,
    val episodes: List<Episode>,
    val newestDate: Long,
)

private enum class SortMode(val label: String) {
    GROUPED("By podcast"),
    BY_DATE("By date"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
    repository: PodcastRepository,
    onEpisodeClick: (String) -> Unit,
) {
    val downloadedEpisodes by repository.getDownloadedEpisodes().collectAsStateWithLifecycle(emptyList())
    var sortMode by rememberSaveable { mutableStateOf(SortMode.BY_DATE) }

    // Cache podcast metadata — single batch query
    var podcastCache by remember { mutableStateOf<Map<String, Podcast>>(emptyMap()) }
    LaunchedEffect(downloadedEpisodes) {
        val feedUrls = downloadedEpisodes.map { it.podcastFeedUrl }.distinct()
        val missing = feedUrls.filter { it !in podcastCache }
        if (missing.isNotEmpty()) {
            val fetched = repository.getPodcasts(missing)
            podcastCache = podcastCache + fetched
        }
    }

    // Group by podcast, sorted by newest episode date per group
    val groups = remember(downloadedEpisodes, podcastCache) {
        downloadedEpisodes
            .groupBy { it.podcastFeedUrl }
            .mapNotNull { (feedUrl, episodes) ->
                val podcast = podcastCache[feedUrl] ?: return@mapNotNull null
                PodcastGroup(
                    podcast = podcast,
                    episodes = episodes.sortedByDescending { it.publishedDate },
                    newestDate = episodes.maxOf { it.publishedDate },
                )
            }
            .sortedByDescending { it.newestDate }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Downloads") },
            actions = {
                if (downloadedEpisodes.isNotEmpty()) {
                    TextButton(onClick = {
                        sortMode = if (sortMode == SortMode.GROUPED) SortMode.BY_DATE else SortMode.GROUPED
                    }) {
                        Text(
                            if (sortMode == SortMode.GROUPED) "By date" else "By podcast",
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }
        )

        if (downloadedEpisodes.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "No downloads",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Download episodes from the episode menu for offline listening",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 48.dp),
                )
            }
        } else if (sortMode == SortMode.GROUPED) {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                groups.forEach { group ->
                    item(key = "header-${group.podcast.feedUrl}") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AsyncImage(
                                model = group.podcast.artworkUrl,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.Crop,
                            )
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = group.podcast.title,
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                Text(
                                    text = "${group.episodes.size} downloaded",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }

                    items(group.episodes, key = { it.id }, contentType = { "episode" }) { episode ->
                        DownloadedEpisodeRow(
                            episode = episode,
                            showPodcastInfo = false,
                            podcastTitle = "",
                            podcastArtworkUrl = "",
                            indented = true,
                            onClick = { onEpisodeClick(episode.id) },
                            onDeleteDownload = { deleteDownload(repository, episode.id) },
                        )
                    }
                }
            }
        } else {
            // Flat date-sorted view
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(downloadedEpisodes, key = { it.id }, contentType = { "episode" }) { episode ->
                    val podcast = podcastCache[episode.podcastFeedUrl]
                    DownloadedEpisodeRow(
                        episode = episode,
                        showPodcastInfo = true,
                        podcastTitle = podcast?.title ?: "",
                        podcastArtworkUrl = podcast?.artworkUrl ?: "",
                        indented = false,
                        onClick = { onEpisodeClick(episode.id) },
                        onDeleteDownload = { deleteDownload(repository, episode.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun DownloadedEpisodeRow(
    episode: Episode,
    showPodcastInfo: Boolean,
    podcastTitle: String,
    podcastArtworkUrl: String,
    indented: Boolean,
    onClick: () -> Unit,
    onDeleteDownload: () -> Unit,
) {
    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(
                start = if (indented) 68.dp else 16.dp,
                end = 16.dp,
                top = 6.dp,
                bottom = 6.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showPodcastInfo) {
            AsyncImage(
                model = podcastArtworkUrl,
                contentDescription = null,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop,
            )
            Spacer(Modifier.width(12.dp))
        }
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
            Row {
                if (episode.isCompleted) {
                    Text(
                        text = "✓ Played · ",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                if (showPodcastInfo && podcastTitle.isNotEmpty()) {
                    Text(
                        text = podcastTitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
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
        IconButton(onClick = onDeleteDownload) {
            Icon(
                Icons.Default.Delete,
                contentDescription = "Delete download",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}
