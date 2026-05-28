package com.migspod.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

class PodcastRepository(
    private val podcastDao: PodcastDao,
    private val episodeDao: EpisodeDao,
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
            val feed = fetchAndParseFeed(feedUrl)
            val podcast = Podcast(
                feedUrl = feedUrl,
                title = feed.title,
                author = feed.author,
                description = feed.description,
                artworkUrl = feed.artworkUrl,
                lastRefreshed = System.currentTimeMillis(),
            )
            podcastDao.insertPodcast(podcast)
            val episodes = feed.episodes.map { ep ->
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
            episodeDao.insertEpisodes(episodes)
            Result.success(podcast)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getPodcast(feedUrl: String): Podcast? = podcastDao.getPodcast(feedUrl)

    suspend fun getPodcasts(feedUrls: List<String>): Map<String, Podcast> {
        return podcastDao.getPodcasts(feedUrls).associateBy { it.feedUrl }
    }

    suspend fun unsubscribe(podcast: Podcast) {
        podcastDao.deletePodcast(podcast)
    }

    suspend fun refreshPodcast(feedUrl: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val feed = fetchAndParseFeed(feedUrl)
            val episodes = feed.episodes.map { ep ->
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
            episodeDao.insertEpisodes(episodes) // IGNORE strategy keeps existing data
            val existing = podcastDao.getPodcast(feedUrl)
                ?: return@withContext Result.success(Unit) // unsubscribed during refresh
            podcastDao.updatePodcast(
                existing.copy(
                    lastRefreshed = System.currentTimeMillis(),
                    title = feed.title,
                    author = feed.author,
                    description = feed.description,
                    artworkUrl = feed.artworkUrl,
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

    fun getInProgressEpisodes(): Flow<List<Episode>> = episodeDao.getInProgressEpisodes()

    suspend fun updatePlaybackPosition(episodeId: String, positionMs: Long) {
        episodeDao.updatePlaybackPosition(episodeId, positionMs)
    }

    suspend fun markCompleted(episodeId: String, completed: Boolean) {
        episodeDao.markCompleted(episodeId, completed)
    }

    // --- New episodes feed ---

    fun getRecentEpisodes(limit: Int = 100): Flow<List<Episode>> =
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

    suspend fun downloadEpisode(context: Context, episodeId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val episode = episodeDao.getEpisode(episodeId)
                ?: return@withContext Result.failure(Exception("Episode not found"))
            if (episode.audioUrl.isEmpty()) {
                return@withContext Result.failure(Exception("No audio URL"))
            }

            val downloadsDir = File(context.filesDir, "downloads")
            downloadsDir.mkdirs()
            val fileName = "${episodeId.hashCode()}.mp3"
            val destFile = File(downloadsDir, fileName)

            val request = Request.Builder().url(episode.audioUrl).build()
            val response = client.newCall(request).execute()
            response.use { resp ->
                val body = resp.body ?: return@withContext Result.failure(Exception("Empty response"))
                destFile.outputStream().use { output ->
                    body.byteStream().use { input ->
                        input.copyTo(output)
                    }
                }
            }

            episodeDao.updateDownloadPath(episodeId, destFile.absolutePath)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
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

    // --- Search ---

    suspend fun searchPodcasts(query: String): Result<List<SearchResult>> = withContext(Dispatchers.IO) {
        try {
            val url = "https://itunes.apple.com/search?term=${
                java.net.URLEncoder.encode(query, "UTF-8")
            }&media=podcast&limit=25"
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            val body = response.use { it.body?.string() } ?: return@withContext Result.success(emptyList())
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

    private fun fetchAndParseFeed(feedUrl: String): ParsedFeed {
        val request = Request.Builder().url(feedUrl).build()
        val response = client.newCall(request).execute()
        val body = response.body ?: throw Exception("Empty response from feed")
        return body.byteStream().use { RssFeedParser.parse(it) }
    }
}

@androidx.compose.runtime.Immutable
data class SearchResult(
    val feedUrl: String,
    val title: String,
    val author: String,
    val artworkUrl: String,
)
