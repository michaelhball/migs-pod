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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object PlayerController {

    private var mediaController: MediaController? = null

    fun connect(context: Context) {
        val sessionToken = SessionToken(
            context,
            ComponentName(context, PlaybackService::class.java)
        )
        val future = MediaController.Builder(context, sessionToken).buildAsync()
        future.addListener(
            { mediaController = future.get() },
            MoreExecutors.directExecutor()
        )
    }

    suspend fun playEpisode(episode: Episode) {
        PlaybackState.currentEpisodeId.value = episode.id

        val podcast = withContext(Dispatchers.IO) {
            MigsPodApp.instance.database.podcastDao().getPodcast(episode.podcastFeedUrl)
        }

        val mediaItem = MediaItem.Builder()
            .setMediaId(episode.id)
            .setUri(episode.downloadPath ?: episode.audioUrl)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(episode.title)
                    .setArtist(podcast?.title ?: "")
                    .setArtworkUri(
                        podcast?.artworkUrl?.let { it.toUri() }
                    )
                    .build()
            )
            .build()

        val controller = mediaController ?: return
        controller.setMediaItem(mediaItem)
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
}
