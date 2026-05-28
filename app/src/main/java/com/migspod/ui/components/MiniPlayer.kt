package com.migspod.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.migspod.data.PodcastRepository
import com.migspod.player.PlaybackState
import com.migspod.player.PlayerController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable
fun MiniPlayer(
    repository: PodcastRepository,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentEpisodeId by PlaybackState.currentEpisodeId.collectAsStateWithLifecycle()
    val player by PlaybackState.player.collectAsStateWithLifecycle()
    var episodeTitle by remember { mutableStateOf("") }
    var podcastTitle by remember { mutableStateOf("") }
    var artworkUrl by remember { mutableStateOf("") }
    var progress by remember { mutableFloatStateOf(0f) }
    var isPlaying by remember { mutableStateOf(false) }

    LaunchedEffect(currentEpisodeId) {
        val epId = currentEpisodeId ?: return@LaunchedEffect
        withContext(Dispatchers.IO) {
            val episode = repository.getEpisode(epId) ?: return@withContext
            episodeTitle = episode.title
            val podcast = repository.getPodcast(episode.podcastFeedUrl)
            podcastTitle = podcast?.title ?: ""
            artworkUrl = podcast?.artworkUrl ?: ""
        }
    }

    LaunchedEffect(player) {
        while (true) {
            val p = player
            if (p != null && p.duration > 0) {
                progress = p.currentPosition.toFloat() / p.duration.toFloat()
                isPlaying = p.isPlaying
            }
            delay(500)
        }
    }

    if (currentEpisodeId != null) {
        Surface(
            modifier = modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceVariant,
            tonalElevation = 4.dp,
        ) {
            Column {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().height(2.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onClick)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AsyncImage(
                        model = artworkUrl,
                        contentDescription = null,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(6.dp)),
                        contentScale = ContentScale.Crop,
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = episodeTitle,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = podcastTitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    IconButton(onClick = { PlayerController.togglePlayPause() }) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                        )
                    }
                }
            }
        }
    }
}
