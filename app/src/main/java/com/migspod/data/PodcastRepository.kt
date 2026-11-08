package com.migspod.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParserException
import java.io.File
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

class PodcastRepository(
    private val podcastDao: PodcastDao,
    private val episodeDao: EpisodeDao,
    private val downloadsDir: File,
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .connectionPool(ConnectionPool(5, 30, TimeUnit.SECONDS))
        .build()

    // --- Podcasts ---

    fun getAllPodcasts(): Flow<List<Podcast>> = podcastDao.getAllPodcasts()

    suspend fun subscribeToPodcast(feedUrl: String): Result<Podcast> = withContext(Dispatchers.IO) {
        try {
            val fetched = fetchFeed(feedUrl) ?: throw IOException("Empty response from feed")
            val feed = fetched.feed
            val podcast = Podcast(
                feedUrl = feedUrl,
                title = feed.title.ifBlank { feedUrl },
                author = feed.author,
                description = feed.description,
                artworkUrl = feed.artworkUrl,
                lastRefreshed = System.currentTimeMillis(),
                feedEtag = fetched.etag,
                feedLastModified = fetched.lastModified,
            )
            podcastDao.upsertPodcast(podcast)
            episodeDao.saveFeedEpisodes(feedUrl, feed.toEpisodes(feedUrl))
            Result.success(podcast)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getPodcast(feedUrl: String): Podcast? = podcastDao.getPodcast(feedUrl)

    suspend fun getPodcasts(feedUrls: List<String>): Map<String, Podcast> {
        return podcastDao.getPodcasts(feedUrls).associateBy { it.feedUrl }
    }

    suspend fun unsubscribe(podcast: Podcast) = withContext(Dispatchers.IO) {
        // The cascade removes the episode rows; the audio files are ours to clean up.
        val downloads = episodeDao.getDownloadPathsForPodcast(podcast.feedUrl)
        podcastDao.deletePodcast(podcast)
        downloads.forEach { File(it).delete() }
    }

    suspend fun refreshPodcast(feedUrl: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val cached = podcastDao.getPodcast(feedUrl)
                ?: return@withContext Result.success(Unit) // not subscribed
            val fetched = fetchFeed(feedUrl, cached)
            if (fetched == null) { // 304: nothing new
                podcastDao.updatePodcast(cached.copy(lastRefreshed = System.currentTimeMillis()))
                return@withContext Result.success(Unit)
            }
            val feed = fetched.feed
            episodeDao.saveFeedEpisodes(feedUrl, feed.toEpisodes(feedUrl))
            val existing = podcastDao.getPodcast(feedUrl)
                ?: return@withContext Result.success(Unit) // unsubscribed during refresh
            podcastDao.updatePodcast(
                existing.copy(
                    lastRefreshed = System.currentTimeMillis(),
                    title = feed.title.ifBlank { existing.title },
                    author = feed.author,
                    description = feed.description,
                    artworkUrl = feed.artworkUrl.ifBlank { existing.artworkUrl },
                    feedEtag = fetched.etag,
                    feedLastModified = fetched.lastModified,
                )
            )
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // --- Episodes ---

    fun getEpisodesForPodcast(feedUrl: String): Flow<List<Episode>> =
        episodeDao.getEpisodesForPodcast(feedUrl)

    suspend fun getEpisode(id: String): Episode? = episodeDao.getEpisode(id)

    fun getEpisodeFlow(id: String): Flow<Episode?> = episodeDao.getEpisodeFlow(id)

    suspend fun updatePlaybackPosition(episodeId: String, positionMs: Long) {
        episodeDao.updatePlaybackPosition(episodeId, positionMs)
    }

    suspend fun markCompleted(episodeId: String, completed: Boolean) {
        episodeDao.markCompleted(episodeId, completed)
    }

    // --- New episodes feed ---

    fun getRecentEpisodes(limit: Int = 500): Flow<List<Episode>> =
        episodeDao.getRecentEpisodes(limit)

    suspend fun refreshAllPodcasts(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val podcasts = podcastDao.getAllPodcasts().first()
            supervisorScope {
                podcasts.map { podcast ->
                    async { refreshPodcast(podcast.feedUrl) }
                }.awaitAll()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // --- Downloads ---

    fun getDownloadedEpisodes(): Flow<List<Episode>> = episodeDao.getDownloadedEpisodes()

    private val _downloadingIds = MutableStateFlow<Set<String>>(emptySet())

    /** Episodes with a download in flight, shared by every screen. */
    val downloadingIds: StateFlow<Set<String>> = _downloadingIds.asStateFlow()

    suspend fun downloadEpisode(episodeId: String): Result<Unit> = withContext(Dispatchers.IO) {
        var claimed = false
        _downloadingIds.update { claimed = episodeId !in it; it + episodeId }
        if (!claimed) return@withContext Result.success(Unit) // already downloading

        val destFile = downloadFileFor(episodeId)
        val partFile = File(destFile.path + PART_SUFFIX)
        try {
            val episode = episodeDao.getEpisode(episodeId) ?: throw IOException("Episode not found")
            if (episode.audioUrl.isEmpty()) throw IOException("Episode has no audio")

            downloadsDir.mkdirs()
            val request = Request.Builder().url(episode.audioUrl).build()
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) throw IOException("Server returned HTTP ${resp.code}")
                val body = resp.body ?: throw IOException("Empty response")
                // e.g. a Wi-Fi captive portal answering with its login page
                if (body.contentType()?.type == "text") throw IOException("Server didn't return audio")
                partFile.outputStream().use { output ->
                    body.byteStream().use { input -> input.copyTo(output) }
                }
            }
            if (!partFile.renameTo(destFile)) throw IOException("Couldn't save the download")
            if (episodeDao.updateDownloadPath(episodeId, destFile.absolutePath) == 0) {
                destFile.delete() // podcast was unsubscribed mid-download
            }
            Result.success(Unit)
        } catch (e: Exception) {
            partFile.delete()
            Result.failure(e)
        } finally {
            _downloadingIds.update { it - episodeId }
        }
    }

    suspend fun deleteDownload(episodeId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val episode = episodeDao.getEpisode(episodeId)
            episode?.downloadPath?.let { path ->
                val file = File(path)
                if (file.exists()) file.delete()
            }
            episodeDao.updateDownloadPath(episodeId, null)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Brings the downloads folder and the database back in line: forgets downloads
     * whose file is gone (e.g. after a backup restore) and deletes files nothing
     * references (interrupted downloads, older unsubscribes).
     */
    suspend fun reconcileDownloads() = withContext(Dispatchers.IO) {
        // Snapshot in-flight downloads first so one finishing mid-scan is never deleted.
        val inFlightNames = _downloadingIds.value.map { downloadFileFor(it).name }.toSet()
        // Canonical paths: a stored /data/data/… path and a listed /data/user/0/… one
        // are the same file, and must never be mistaken for an orphan.
        val referencedPaths = HashSet<String>()
        for (episode in episodeDao.getDownloadedEpisodesOnce()) {
            val path = episode.downloadPath ?: continue
            val file = File(path)
            if (file.exists()) referencedPaths += file.canonicalPath
            else episodeDao.updateDownloadPath(episode.id, null)
        }
        downloadsDir.listFiles()?.forEach { file ->
            val referenced = file.canonicalPath in referencedPaths
            val inFlight = file.name.removeSuffix(PART_SUFFIX) in inFlightNames
            if (!referenced && !inFlight) file.delete()
        }
    }

    private fun downloadFileFor(episodeId: String) = File(downloadsDir, "${episodeId.hashCode()}.mp3")

    // --- Search ---

    suspend fun searchPodcasts(query: String): Result<List<SearchResult>> = withContext(Dispatchers.IO) {
        try {
            val url = "https://itunes.apple.com/search?term=${
                java.net.URLEncoder.encode(query, "UTF-8")
            }&media=podcast&limit=25"
            val request = Request.Builder().url(url).build()
            val body = client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("Search isn't available right now")
                response.body?.string()
            } ?: return@withContext Result.success(emptyList())
            val json = JSONObject(body)
            val results = json.getJSONArray("results")
            val searchResults = mutableListOf<SearchResult>()
            for (i in 0 until results.length()) {
                val item = results.getJSONObject(i)
                searchResults.add(
                    SearchResult(
                        feedUrl = item.optString("feedUrl", ""),
                        title = item.optString("collectionName", ""),
                        author = item.optString("artistName", ""),
                        artworkUrl = item.optString("artworkUrl600",
                            item.optString("artworkUrl100", "")),
                    )
                )
            }
            Result.success(searchResults.filter { it.feedUrl.isNotEmpty() }.distinctBy { it.feedUrl })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // --- Internal ---

    private class FetchedFeed(val feed: ParsedFeed, val etag: String?, val lastModified: String?)

    /**
     * Fetches and parses [feedUrl]. With [cached] validators, returns null when the
     * server answers 304 Not Modified, so unchanged feeds cost a few hundred bytes.
     */
    private fun fetchFeed(feedUrl: String, cached: Podcast? = null): FetchedFeed? {
        val request = Request.Builder().url(feedUrl).apply {
            cached?.feedEtag?.let { header("If-None-Match", it) }
            cached?.feedLastModified?.let { header("If-Modified-Since", it) }
        }.build()
        client.newCall(request).execute().use { resp ->
            if (resp.code == 304) return null
            if (!resp.isSuccessful) throw IOException("Server returned HTTP ${resp.code}")
            val body = resp.body ?: throw IOException("Empty response from feed")
            val feed = body.byteStream().use { RssFeedParser.parse(it) }
            if (feed.title.isBlank() && feed.episodes.isEmpty()) throw IOException(NOT_A_FEED)
            return FetchedFeed(feed, resp.header("ETag"), resp.header("Last-Modified"))
        }
    }

    private fun ParsedFeed.toEpisodes(feedUrl: String) = episodes.map { ep ->
        Episode(
            id = ep.guid,
            podcastFeedUrl = feedUrl,
            title = ep.title,
            description = ep.description,
            audioUrl = ep.audioUrl,
            publishedDate = ep.publishedDate,
            durationMs = ep.durationMs,
        )
    }
}

private const val PART_SUFFIX = ".part"
private const val NOT_A_FEED = "That doesn't look like a podcast feed"

/** A short, human-readable reason for a failed network/feed operation. */
fun Throwable.toUserMessage(): String = when (this) {
    is UnknownHostException, is ConnectException -> "No internet connection"
    is SocketTimeoutException -> "The server took too long to respond"
    is XmlPullParserException -> NOT_A_FEED
    else -> message ?: "Something went wrong"
}

@androidx.compose.runtime.Immutable
data class SearchResult(
    val feedUrl: String,
    val title: String,
    val author: String,
    val artworkUrl: String,
)
