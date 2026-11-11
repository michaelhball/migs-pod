package com.migspod.player

import android.content.ComponentName
import android.content.Context
import androidx.core.content.edit
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.migspod.MigsPodApp
import com.migspod.data.Episode
import com.migspod.data.Podcast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

object PlayerController {

    private const val PREFS = "migspod_player"
    private const val KEY_LAST_EPISODE = "last_episode_id"

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null

    /**
     * Binds to the playback service. Called from the activity's onStart and paired
     * with [disconnect] in onStop, so the binding never outlives the visible UI.
     */
    fun connect(context: Context) {
        if (controllerFuture != null) return
        val appContext = context.applicationContext
        val sessionToken = SessionToken(appContext, ComponentName(appContext, PlaybackService::class.java))
        val future = MediaController.Builder(appContext, sessionToken).buildAsync()
        controllerFuture = future
        future.addListener(
            {
                val controller = runCatching { future.get() }.getOrNull() ?: return@addListener
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

    fun disconnect() {
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        mediaController = null
    }

    suspend fun playEpisode(episode: Episode) {
        val controller = awaitController() ?: return
        // Already loaded (e.g. "Resume" from its own sheet): just carry on. Reloading
        // would rebuffer and jump back to the last saved position (up to 5s stale).
        if (controller.currentMediaItem?.episodeId == episode.id) {
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

    /** Stop and unload, and forget the episode so it isn't restored on next launch. */
    fun close() {
        MigsPodApp.instance.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            remove(KEY_LAST_EPISODE)
        }
        stop()
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

    /** The last episode played, if it's unfinished — what "resume" should bring back. */
    internal suspend fun loadLastEpisode(): Pair<Episode, Podcast?>? = withContext(Dispatchers.IO) {
        val id = MigsPodApp.instance
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_LAST_EPISODE, null) ?: return@withContext null
        val db = MigsPodApp.instance.database
        val episode = db.episodeDao().getEpisode(id) ?: return@withContext null
        if (episode.isCompleted) return@withContext null
        episode to db.podcastDao().getPodcast(episode.podcastFeedUrl)
    }

    // --- Internal ---

    /** The connected controller, waiting for a connection still in progress. */
    private suspend fun awaitController(): MediaController? {
        mediaController?.let { return it }
        val future = controllerFuture ?: return null
        return suspendCancellableCoroutine { cont ->
            future.addListener(
                { cont.resume(runCatching { future.get() }.getOrNull()) },
                MoreExecutors.directExecutor(),
            )
        }
    }

    /**
     * Attaches the podcast artwork to the live media item once it's loaded, which
     * is what the system lockscreen / notification / Quick Settings actually render.
     */
    private fun embedArtwork(artworkUrl: String?, episodeId: String) {
        MigsPodApp.instance.appScope.launch {
            val bytes = loadArtworkBytes(artworkUrl) ?: return@launch
            withContext(Dispatchers.Main) {
                val controller = mediaController ?: return@withContext
                val current = controller.currentMediaItem ?: return@withContext
                if (current.episodeId != episodeId) return@withContext // moved on already
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

    private fun saveLastEpisode(episodeId: String) {
        MigsPodApp.instance.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putString(KEY_LAST_EPISODE, episodeId)
        }
    }

    private fun restoreLastEpisode() {
        MigsPodApp.instance.appScope.launch {
            val (episode, podcast) = loadLastEpisode() ?: return@launch
            // Don't resurrect one that was never actually started.
            if (episode.playbackPositionMs <= 0) return@launch
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
