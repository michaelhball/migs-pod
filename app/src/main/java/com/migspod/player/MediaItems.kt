package com.migspod.player

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.migspod.MigsPodApp
import com.migspod.data.Episode
import com.migspod.data.Podcast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

// Plenty for a lockscreen/notification; feed artwork is often 3000px (~36MB decoded).
private const val ARTWORK_SIZE_PX = 512

internal fun buildMediaItem(episode: Episode, podcast: Podcast?, artwork: ByteArray? = null): MediaItem =
    MediaItem.Builder()
        .setMediaId(episode.id)
        .setUri(episode.downloadPath ?: episode.audioUrl)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(episode.title)
                .setArtist(podcast?.title ?: "")
                // Artwork goes in as raw bytes, NOT as a URI. OEM bitmap loaders
                // frequently fail to resolve a remote artworkUri for the
                // lockscreen/notification; once they report "no art" the cards stay
                // blank. setArtworkData is the reliable source.
                .apply { artwork?.let { setArtworkData(it, MediaMetadata.PICTURE_TYPE_FRONT_COVER) } }
                .build()
        )
        .build()

/** Podcast artwork as JPEG bytes (via Coil, so it's cached/shared with the UI). */
internal suspend fun loadArtworkBytes(url: String?): ByteArray? {
    if (url.isNullOrEmpty()) return null
    return withContext(Dispatchers.IO) {
        runCatching {
            val context = MigsPodApp.instance
            val request = ImageRequest.Builder(context)
                .data(url)
                .size(ARTWORK_SIZE_PX)
                .allowHardware(false) // need a readable software bitmap to compress
                .build()
            val result = context.imageLoader.execute(request) as? SuccessResult
            val bitmap = (result?.drawable as? BitmapDrawable)?.bitmap ?: return@runCatching null
            val out = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            out.toByteArray().takeIf { it.isNotEmpty() }
        }.getOrNull()
    }
}
