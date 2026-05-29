package com.migspod.player

import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.migspod.MigsPodApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val MEDIA_NOTIFICATION_ID = 1001

class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private var player: ExoPlayer? = null
    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)
    private var positionSaveJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        // Install Media3's default notification provider eagerly. By default Media3
        // does this lazily once playback flips isPlaying; on some OEMs that lazy path
        // doesn't fire reliably and no media notification ever appears. Pinning it to
        // our own channel + a fixed id makes the behaviour deterministic.
        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this)
                .setChannelId(MigsPodApp.PLAYBACK_CHANNEL_ID)
                .setNotificationId(MEDIA_NOTIFICATION_ID)
                .build()
        )

        val exoPlayer = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                true // handleAudioFocus
            )
            .setHandleAudioBecomingNoisy(true)
            // Surfaces -10s / +30s (rewind/fast-forward) buttons in the media
            // notification and on the lockscreen, matching the in-app controls.
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(30_000)
            .build()

        player = exoPlayer

        // Replace the default skip-to-prev/next buttons (useless for single-episode
        // podcast playback — "previous" just restarts the episode) with rewind 10s /
        // forward 30s, on the notification and lockscreen. These map to the player's
        // built-in SEEK_BACK / SEEK_FORWARD commands (increments set above).
        val rewindButton = CommandButton.Builder(CommandButton.ICON_SKIP_BACK_10)
            .setPlayerCommand(Player.COMMAND_SEEK_BACK)
            .setDisplayName("Rewind 10 seconds")
            .build()
        val forwardButton = CommandButton.Builder(CommandButton.ICON_SKIP_FORWARD_30)
            .setPlayerCommand(Player.COMMAND_SEEK_FORWARD)
            .setDisplayName("Fast-forward 30 seconds")
            .build()

        mediaSession = MediaSession.Builder(this, exoPlayer)
            .setMediaButtonPreferences(listOf(rewindButton, forwardButton))
            .build()

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
