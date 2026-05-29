package com.migspod.player

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import androidx.core.net.toUri
import com.migspod.MigsPodApp
import com.migspod.data.Episode
import com.migspod.data.Podcast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object PlayerController {

    private const val PREFS = "migspod_player"
    private const val KEY_LAST_EPISODE = "last_episode_id"

    private var mediaController: MediaController? = null

    fun connect(context: Context) {
        val sessionToken = SessionToken(
            context,
            ComponentName(context, PlaybackService::class.java)
        )
        val future = MediaController.Builder(context, sessionToken).buildAsync()
        future.addListener(
            {
                val controller = future.get()
                mediaController = controller
                // Cold start: nothing is loaded yet. Restore the last episode the
                // user was listening to (paused, at its saved position) so the
                // mini-player reappears and a tap resumes where they left off.
                if (controller.mediaItemCount == 0 &&
                    PlaybackState.currentEpisodeId.value == null
                ) {
                    restoreLastEpisode()
                }
            },
            MoreExecutors.directExecutor()
        )
    }

    suspend fun playEpisode(episode: Episode) {
        PlaybackState.currentEpisodeId.value = episode.id
        saveLastEpisode(episode.id)

        val podcast = withContext(Dispatchers.IO) {
            MigsPodApp.instance.database.podcastDao().getPodcast(episode.podcastFeedUrl)
        }

        val controller = mediaController ?: return
        controller.setMediaItem(buildMediaItem(episode, podcast))
        if (episode.playbackPositionMs > 0) {
            controller.seekTo(episode.playbackPositionMs)
        }
        controller.prepare()
        controller.play()
    }

    fun togglePlayPause() {
        val controller = mediaController ?: return
        // Use playWhenReady (intent) rather than isPlaying (actual): while the
        // player is buffering after a seek, isPlaying is false even though the
        // user means it to be playing, which would otherwise flip play→pause.
        if (controller.playWhenReady) {
            controller.pause()
        } else {
            controller.play()
        }
    }

    fun seekTo(positionMs: Long) {
        mediaController?.seekTo(positionMs)
    }

    fun seekForward(ms: Long = 30_000) {
        val controller = mediaController ?: return
        controller.seekTo(controller.currentPosition + ms)
    }

    fun seekBack(ms: Long = 10_000) {
        val controller = mediaController ?: return
        controller.seekTo(maxOf(0, controller.currentPosition - ms))
    }

    fun stop() {
        mediaController?.stop()
        PlaybackState.currentEpisodeId.value = null
    }

    // --- Internal ---

    private fun buildMediaItem(episode: Episode, podcast: Podcast?): MediaItem =
        MediaItem.Builder()
            .setMediaId(episode.id)
            .setUri(episode.downloadPath ?: episode.audioUrl)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(episode.title)
                    .setArtist(podcast?.title ?: "")
                    .setArtworkUri(
                        podcast?.artworkUrl?.takeIf { it.isNotEmpty() }?.toUri()
                    )
                    .build()
            )
            .build()

    private fun saveLastEpisode(episodeId: String) {
        MigsPodApp.instance
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LAST_EPISODE, episodeId)
            .apply()
    }

    private fun restoreLastEpisode() {
        val id = MigsPodApp.instance
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_LAST_EPISODE, null) ?: return

        MigsPodApp.instance.appScope.launch {
            val db = MigsPodApp.instance.database
            val episode = db.episodeDao().getEpisode(id) ?: return@launch
            // Don't resurrect a finished episode or one never actually started.
            if (episode.isCompleted || episode.playbackPositionMs <= 0) return@launch
            val podcast = db.podcastDao().getPodcast(episode.podcastFeedUrl)
            withContext(Dispatchers.Main) {
                val controller = mediaController ?: return@withContext
                // Something may have started playing while we were loading.
                if (controller.mediaItemCount > 0) return@withContext
                PlaybackState.currentEpisodeId.value = episode.id
                controller.setMediaItem(buildMediaItem(episode, podcast))
                controller.seekTo(episode.playbackPositionMs)
                controller.prepare() // ready but paused (playWhenReady stays false)
            }
        }
    }
}
