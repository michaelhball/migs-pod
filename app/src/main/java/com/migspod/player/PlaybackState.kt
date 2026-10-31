package com.migspod.player

import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Global playback state shared between the service and UI.
 */
object PlaybackState {
    val player = MutableStateFlow<ExoPlayer?>(null)
    val currentEpisodeId = MutableStateFlow<String?>(null)
}
