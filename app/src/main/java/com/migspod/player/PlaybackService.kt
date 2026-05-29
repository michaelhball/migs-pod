package com.migspod.player

import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.migspod.MigsPodApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private var player: ExoPlayer? = null
    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)
    private var positionSaveJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        val exoPlayer = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                true // handleAudioFocus
            )
            .setHandleAudioBecomingNoisy(true)
            .build()

        player = exoPlayer
        mediaSession = MediaSession.Builder(this, exoPlayer).build()

        // Save playback position periodically
        exoPlayer.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) {
                    startPositionSaving()
                } else {
                    stopPositionSaving()
                    saveCurrentPosition()
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                PlaybackState.currentMediaItem.value = mediaItem
                PlaybackState.currentEpisodeId.value = mediaItem?.mediaId
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                PlaybackState.playbackState.value = playbackState
                if (playbackState == Player.STATE_ENDED) {
                    markCurrentCompleted()
                    // No autoplay: the episode is done, so tear the player down.
                    // Clearing the media item lets Media3 drop the foreground
                    // notification, and nulling the episode id hides the mini-player.
                    PlaybackState.currentEpisodeId.value = null
                    player?.clearMediaItems()
                }
            }

        })

        PlaybackState.player.value = exoPlayer
    }

    private fun startPositionSaving() {
        positionSaveJob?.cancel()
        positionSaveJob = serviceScope.launch {
            while (true) {
                delay(5000)
                saveCurrentPosition()
            }
        }
    }

    private fun stopPositionSaving() {
        positionSaveJob?.cancel()
        positionSaveJob = null
    }

    private fun saveCurrentPosition() {
        val p = player ?: return
        val episodeId = PlaybackState.currentEpisodeId.value ?: return
        val position = p.currentPosition
        serviceScope.launch(Dispatchers.IO) {
            val repo = MigsPodApp.instance.database
            repo.episodeDao().updatePlaybackPosition(episodeId, position)
        }
    }

    private fun markCurrentCompleted() {
        val episodeId = PlaybackState.currentEpisodeId.value ?: return
        serviceScope.launch(Dispatchers.IO) {
            val db = MigsPodApp.instance.database
            db.episodeDao().markCompleted(episodeId, true)
            db.episodeDao().updatePlaybackPosition(episodeId, 0)
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val p = player
        if (p != null && !p.playWhenReady) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        stopPositionSaving()
        saveCurrentPosition()
        serviceJob.cancel()
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        player = null
        PlaybackState.player.value = null
        super.onDestroy()
    }
}
