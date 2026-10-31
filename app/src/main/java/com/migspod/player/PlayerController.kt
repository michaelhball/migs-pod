package com.migspod.player

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.google.common.util.concurrent.MoreExecutors
import com.migspod.MigsPodApp
import com.migspod.data.Episode
import com.migspod.data.Podcast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

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
        val controller = mediaController ?: return
        // Already loaded (e.g. "Resume" from its own sheet): just carry on. Reloading
        // would rebuffer and jump back to the last saved position (up to 5s stale).
        if (controller.currentMediaItem?.mediaId == episode.id) {
            controller.play()
            return
        }

        PlaybackState.currentEpisodeId.value = episode.id
        saveLastEpisode(episode.id)

        val podcast = withContext(Dispatchers.IO) {
            MigsPodApp.instance.database.podcastDao().getPodcast(episode.podcastFeedUrl)
        }

        // Pass the start position into setMediaItem rather than seekTo() afterwards —
        // a seek on a not-yet-prepared item via MediaController is dropped, which is
        // why resume was starting from 0.
        controller.setMediaItem(buildMediaItem(episode, podcast), episode.playbackPositionMs)
        controller.prepare()
        controller.play()
        embedArtwork(podcast?.artworkUrl, episode.id)
    }

    /** Stop playback if [episodeId] is what's currently loaded (e.g. its download was deleted). */
    fun stopIfCurrent(episodeId: String) {
        if (PlaybackState.currentEpisodeId.value == episodeId) {
            stop()
        }
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
        mediaController?.run {
            stop()
            // Unload the item too, so a later play can't resume a stale URI
            // (e.g. the file of a download that was just deleted).
            clearMediaItems()
        }
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
                    // Artwork is attached as raw bytes by embedArtwork() after the item
                    // loads — NOT as a URI. OEM bitmap loaders frequently fail to resolve
                    // a remote artworkUri for the lockscreen/notification; once they report
                    // "no art" the cards stay blank. setArtworkData is the reliable source.
                    .build()
            )
            .build()

    /**
     * Fetches the podcast artwork (via Coil, so it's cached/shared with the UI) and
     * attaches the decoded bytes to the live media item's metadata, which is what the
     * system lockscreen / notification / Quick Settings actually render.
     */
    private fun embedArtwork(artworkUrl: String?, episodeId: String) {
        val url = artworkUrl?.takeIf { it.isNotEmpty() } ?: return
        MigsPodApp.instance.appScope.launch {
            val bytes = loadArtworkBytes(url) ?: return@launch
            withContext(Dispatchers.Main) {
                val controller = mediaController ?: return@withContext
                val current = controller.currentMediaItem ?: return@withContext
                if (current.mediaId != episodeId) return@withContext // moved on already
                val metadata = current.mediaMetadata.buildUpon()
                    .setArtworkData(bytes, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
                    .build()
                controller.replaceMediaItem(
                    controller.currentMediaItemIndex,
                    current.buildUpon().setMediaMetadata(metadata).build(),
                )
            }
        }
    }

    private suspend fun loadArtworkBytes(url: String): ByteArray? = withContext(Dispatchers.IO) {
        runCatching {
            val context = MigsPodApp.instance
            val request = ImageRequest.Builder(context)
                .data(url)
                .allowHardware(false) // need a readable software bitmap to compress
                .build()
            val result = context.imageLoader.execute(request) as? SuccessResult
            val bitmap = (result?.drawable as? BitmapDrawable)?.bitmap ?: return@runCatching null
            val out = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            out.toByteArray().takeIf { it.isNotEmpty() }
        }.getOrNull()
    }

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
                controller.setMediaItem(
                    buildMediaItem(episode, podcast),
                    episode.playbackPositionMs,
                )
                controller.prepare() // ready but paused (playWhenReady stays false)
                embedArtwork(podcast?.artworkUrl, episode.id)
            }
        }
    }
}
