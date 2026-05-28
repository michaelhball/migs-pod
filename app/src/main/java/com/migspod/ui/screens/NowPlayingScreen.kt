package com.migspod.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Forward30
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.migspod.data.Episode
import com.migspod.data.PodcastRepository
import com.migspod.player.PlaybackState
import com.migspod.player.PlayerController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingScreen(
    repository: PodcastRepository,
    onBack: () -> Unit,
) {
    val currentEpisodeId by PlaybackState.currentEpisodeId.collectAsStateWithLifecycle()
    val player by PlaybackState.player.collectAsStateWithLifecycle()
    var episode by remember { mutableStateOf<Episode?>(null) }
    var podcastTitle by remember { mutableStateOf("") }
    var artworkUrl by remember { mutableStateOf("") }
    var currentPosition by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var isPlaying by remember { mutableStateOf(false) }
    var isSeeking by remember { mutableStateOf(false) }
    var seekPosition by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(currentEpisodeId) {
        val epId = currentEpisodeId ?: return@LaunchedEffect
        withContext(Dispatchers.IO) {
            episode = repository.getEpisode(epId)
            episode?.let { ep ->
                val podcast = repository.getPodcast(ep.podcastFeedUrl)
                podcastTitle = podcast?.title ?: ""
                artworkUrl = podcast?.artworkUrl ?: ""
            }
        }
    }

    // Poll player state
    LaunchedEffect(player) {
        while (true) {
            val p = player
            if (p != null) {
                if (!isSeeking) {
                    currentPosition = p.currentPosition
                }
                duration = p.duration.coerceAtLeast(0)
                isPlaying = p.isPlaying
            }
            delay(250)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Background artwork
        AsyncImage(
            model = artworkUrl,
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .drawWithCache {
                    val gradient = Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.3f),
                            Color.Black.copy(alpha = 0.85f),
                        ),
                    )
                    onDrawWithContent {
                        drawContent()
                        drawRect(gradient)
                    }
                },
            contentScale = ContentScale.Crop,
        )

        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                ),
            )

            // Push content toward bottom
            Spacer(Modifier.weight(1f))

            Column(
                modifier = Modifier.padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Title & podcast
                Text(
                    text = episode?.title ?: "",
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = podcastTitle,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(32.dp))

                // Seek bar
                val sliderValue = if (isSeeking) seekPosition
                else if (duration > 0) currentPosition.toFloat() / duration.toFloat()
                else 0f

                Slider(
                    value = sliderValue,
                    onValueChange = {
                        isSeeking = true
                        seekPosition = it
                    },
                    onValueChangeFinished = {
                        val seekMs = (seekPosition * duration).toLong()
                        PlayerController.seekTo(seekMs)
                        currentPosition = seekMs
                        isSeeking = false
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = Color.White,
                        inactiveTrackColor = Color.White.copy(alpha = 0.3f),
                    ),
                )

                // Time labels
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = formatTime(
                            if (isSeeking) (seekPosition * duration).toLong() else currentPosition
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.7f),
                    )
                    Text(
                        text = formatTime(duration),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.7f),
                    )
                }

                Spacer(Modifier.height(16.dp))

                // Playback controls: -10s, play/pause, +30s
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        onClick = { PlayerController.seekBack() },
                        modifier = Modifier.size(64.dp),
                    ) {
                        Icon(
                            Icons.Default.Replay10,
                            contentDescription = "Back 10s",
                            modifier = Modifier.size(40.dp),
                            tint = Color.White,
                        )
                    }
                    IconButton(
                        onClick = { PlayerController.togglePlayPause() },
                        modifier = Modifier.size(80.dp),
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            modifier = Modifier.size(64.dp),
                            tint = Color.White,
                        )
                    }
                    IconButton(
                        onClick = { PlayerController.seekForward() },
                        modifier = Modifier.size(64.dp),
                    ) {
                        Icon(
                            Icons.Default.Forward30,
                            contentDescription = "Forward 30s",
                            modifier = Modifier.size(40.dp),
                            tint = Color.White,
                        )
                    }
                }

                Spacer(Modifier.height(48.dp))
            }
        }
    }
}

internal fun formatTime(ms: Long): String {
    if (ms <= 0) return "0:00"
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.ROOT, "%d:%02d", minutes, seconds)
    }
}
