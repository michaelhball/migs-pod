package com.migspod.player

import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import com.migspod.MigsPodApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val MEDIA_NOTIFICATION_ID = 1001

// One writer, in order: a periodic position save can never land after the
// end-of-episode reset. (Plain Dispatchers.IO doesn't preserve ordering.)
private val dbWrites = Dispatchers.IO.limitedParallelism(1)

@OptIn(UnstableApi::class)
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
            .setCallback(SessionCallback())
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

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int,
            ) {
                when (reason) {
                    // Seeks while paused aren't covered by the periodic save.
                    Player.DISCONTINUITY_REASON_SEEK -> saveCurrentPosition()
                    // Switching or closing episodes: keep where the outgoing one got
                    // to. Not at the natural end — that one was just reset and marked played.
                    Player.DISCONTINUITY_REASON_REMOVE -> {
                        val episodeId = oldPosition.mediaItem?.mediaId ?: return
                        if (exoPlayer.playbackState != Player.STATE_ENDED) {
                            savePosition(episodeId, oldPosition.positionMs)
                        }
                    }
                }
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

            override fun onPlayerError(error: PlaybackException) {
                // A paused restore failing (e.g. offline at launch) isn't worth a toast;
                // tapping play retries and reports then.
                if (!exoPlayer.playWhenReady) return
                MigsPodApp.instance.showToast(playbackErrorMessage(error))
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
        // The player's own item rather than PlaybackState's id, which the UI flips
        // slightly before the player actually switches episodes.
        val episodeId = p.currentMediaItem?.mediaId ?: return
        savePosition(episodeId, p.currentPosition)
    }

    // App scope, not the service's: the final save in onDestroy must outlive it.
    private fun savePosition(episodeId: String, positionMs: Long) {
        MigsPodApp.instance.appScope.launch(dbWrites) {
            MigsPodApp.instance.database.episodeDao().updatePlaybackPosition(episodeId, positionMs)
        }
    }

    private fun markCurrentCompleted() {
        val episodeId = player?.currentMediaItem?.mediaId ?: return
        MigsPodApp.instance.appScope.launch(dbWrites) {
            val db = MigsPodApp.instance.database
            db.episodeDao().markCompleted(episodeId, true)
            db.episodeDao().updatePlaybackPosition(episodeId, 0)
        }
    }

    private fun playbackErrorMessage(error: PlaybackException): String = when (error.errorCode) {
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ->
            "No connection — couldn't play this episode"
        PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
        PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND ->
            "This episode's audio isn't available"
        else -> "Couldn't play this episode"
    }

    private inner class SessionCallback : MediaSession.Callback {
        // A headphone/Bluetooth "play" (or the system's resume card) after the app was
        // killed lands here with nothing loaded: hand back the last unfinished episode
        // at its saved position. Needs the MediaButtonReceiver in the manifest.
        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            isForPlayback: Boolean,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            val result = SettableFuture.create<MediaSession.MediaItemsWithStartPosition>()
            serviceScope.launch {
                val last = PlayerController.loadLastEpisode()
                if (last == null) {
                    result.setException(UnsupportedOperationException("Nothing to resume"))
                    return@launch
                }
                val (episode, podcast) = last
                val item = buildMediaItem(episode, podcast, loadArtworkBytes(podcast?.artworkUrl))
                result.set(MediaSession.MediaItemsWithStartPosition(listOf(item), 0, episode.playbackPositionMs))
            }
            return result
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
        // Nothing is loaded any more; let the next connection restore cleanly.
        PlaybackState.currentEpisodeId.value = null
        super.onDestroy()
    }
}
