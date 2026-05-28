package com.migspod.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.migspod.MigsPodApp as App
import com.migspod.data.Podcast
import com.migspod.data.PodcastRepository
import com.migspod.player.PlayerController
import com.migspod.ui.components.LinkifiedText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EpisodeDetailSheet(
    episodeId: String,
    repository: PodcastRepository,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    showDeleteAction: Boolean = true,
    onDismiss: () -> Unit,
    onNowPlaying: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        EpisodeDetailContent(
            episodeId = episodeId,
            repository = repository,
            showDeleteAction = showDeleteAction,
            onNowPlaying = onNowPlaying,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EpisodeDetailContent(
    episodeId: String,
    repository: PodcastRepository,
    showDeleteAction: Boolean = true,
    onNowPlaying: () -> Unit,
) {
    val episodeFlow = remember(episodeId) { repository.getEpisodeFlow(episodeId) }
    val episode by episodeFlow.collectAsStateWithLifecycle(null)
    var podcast by remember { mutableStateOf<Podcast?>(null) }
    var isDownloading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val dateFormat = remember { SimpleDateFormat("MMMM d, yyyy", Locale.getDefault()) }

    LaunchedEffect(episode?.podcastFeedUrl) {
        val feedUrl = episode?.podcastFeedUrl ?: return@LaunchedEffect
        podcast = withContext(Dispatchers.IO) { repository.getPodcast(feedUrl) }
    }

    val ep = episode
    if (ep != null) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 32.dp),
        ) {
            // Artwork + title
            Row(verticalAlignment = Alignment.Top) {
                AsyncImage(
                    model = podcast?.artworkUrl ?: "",
                    contentDescription = null,
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop,
                )
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = ep.title,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = podcast?.title ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // Date + duration
            Row {
                if (ep.publishedDate > 0) {
                    Text(
                        text = dateFormat.format(Date(ep.publishedDate)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (ep.durationMs > 0) {
                    Text(
                        text = " · ${formatDurationLong(ep.durationMs)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Progress bar
            if (ep.playbackPositionMs > 0 && !ep.isCompleted && ep.durationMs > 0) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { ep.playbackPositionMs.toFloat() / ep.durationMs.toFloat() },
                    modifier = Modifier.fillMaxWidth().height(4.dp),
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "${formatTimestamp(ep.playbackPositionMs)} remaining",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(16.dp))

            // Play button
            Button(
                onClick = {
                    scope.launch {
                        PlayerController.playEpisode(ep)
                        onNowPlaying()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text(if (ep.playbackPositionMs > 0 && !ep.isCompleted) "Resume" else "Play")
            }

            Spacer(Modifier.height(8.dp))

            // Download button
            if (ep.downloadPath != null) {
                if (showDeleteAction) {
                    OutlinedButton(
                        onClick = {
                            App.instance.appScope.launch { repository.deleteDownload(ep.id) }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Delete download")
                    }
                }
            } else if (isDownloading) {
                OutlinedButton(
                    onClick = {},
                    enabled = false,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(4.dp))
                    Text("Downloading…")
                }
            } else if (ep.audioUrl.isNotEmpty()) {
                OutlinedButton(
                    onClick = {
                        isDownloading = true
                        val appCtx = context.applicationContext
                        App.instance.appScope.launch {
                            repository.downloadEpisode(appCtx, ep.id)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Download")
                }
            }

            // Description
            if (ep.description.isNotEmpty()) {
                Spacer(Modifier.height(20.dp))
                LinkifiedText(
                    text = ep.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun formatDurationLong(ms: Long): String {
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    return if (hours > 0) "${hours}h ${minutes}min" else "${minutes} min"
}

private fun formatTimestamp(ms: Long): String {
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds)
    else String.format(Locale.ROOT, "%d:%02d", minutes, seconds)
}
