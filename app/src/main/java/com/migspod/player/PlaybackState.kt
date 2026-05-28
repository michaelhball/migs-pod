package com.migspod.player

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Global playback state shared between the service and UI.
 */
object PlaybackState {
    val player = MutableStateFlow<ExoPlayer?>(null)
    val currentEpisodeId = MutableStateFlow<String?>(null)
    val currentMediaItem = MutableStateFlow<MediaItem?>(null)
    val playbackState = MutableStateFlow(Player.STATE_IDLE)
}
