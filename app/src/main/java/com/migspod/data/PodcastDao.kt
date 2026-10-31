package com.migspod.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface PodcastDao {
    @Query("SELECT * FROM podcasts ORDER BY title ASC")
    fun getAllPodcasts(): Flow<List<Podcast>>

    @Query("SELECT * FROM podcasts WHERE feedUrl = :feedUrl")
    suspend fun getPodcast(feedUrl: String): Podcast?

    @Query("SELECT * FROM podcasts WHERE feedUrl IN (:feedUrls)")
    suspend fun getPodcasts(feedUrls: List<String>): List<Podcast>

    // Upsert, never REPLACE: REPLACE deletes the old row, and the cascade would take
    // every episode (progress, played state, downloads) with it.
    @Upsert
    suspend fun upsertPodcast(podcast: Podcast)

    @Delete
    suspend fun deletePodcast(podcast: Podcast)

    @Update
    suspend fun updatePodcast(podcast: Podcast)
}

@Dao
interface EpisodeDao {
    @Query("SELECT * FROM episodes WHERE podcastFeedUrl = :feedUrl ORDER BY publishedDate DESC")
    fun getEpisodesForPodcast(feedUrl: String): Flow<List<Episode>>

    @Query("SELECT * FROM episodes WHERE id = :id")
    suspend fun getEpisode(id: String): Episode?

    @Query("SELECT * FROM episodes WHERE id = :id")
    fun getEpisodeFlow(id: String): Flow<Episode?>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertEpisodes(episodes: List<Episode>)

    @Query("SELECT * FROM episodes WHERE podcastFeedUrl = :feedUrl")
    suspend fun getEpisodesForPodcastOnce(feedUrl: String): List<Episode>

    @Query("""
        UPDATE episodes
        SET title = :title, description = :description, audioUrl = :audioUrl,
            publishedDate = :publishedDate, durationMs = :durationMs
        WHERE id = :id AND podcastFeedUrl = :feedUrl
    """)
    suspend fun updateFeedFields(
        id: String,
        feedUrl: String,
        title: String,
        description: String,
        audioUrl: String,
        publishedDate: Long,
        durationMs: Long,
    )

    /**
     * Inserts new episodes and refreshes the feed-owned fields of existing ones,
     * leaving listening state (position, played, download) untouched.
     */
    @Transaction
    suspend fun saveFeedEpisodes(feedUrl: String, episodes: List<Episode>) {
        val existing = getEpisodesForPodcastOnce(feedUrl).associateBy { it.id }
        insertEpisodes(episodes.filter { it.id !in existing })
        for (episode in episodes) {
            val old = existing[episode.id] ?: continue
            val changed = old.title != episode.title ||
                old.description != episode.description ||
                old.audioUrl != episode.audioUrl ||
                old.publishedDate != episode.publishedDate ||
                old.durationMs != episode.durationMs
            if (changed) {
                updateFeedFields(
                    episode.id, feedUrl, episode.title, episode.description,
                    episode.audioUrl, episode.publishedDate, episode.durationMs,
                )
            }
        }
    }

    @Query("UPDATE episodes SET playbackPositionMs = :positionMs WHERE id = :episodeId")
    suspend fun updatePlaybackPosition(episodeId: String, positionMs: Long)

    @Query("UPDATE episodes SET isCompleted = :completed WHERE id = :episodeId")
    suspend fun markCompleted(episodeId: String, completed: Boolean)

    @Query("UPDATE episodes SET downloadPath = :path WHERE id = :episodeId")
    suspend fun updateDownloadPath(episodeId: String, path: String?): Int

    @Query("SELECT downloadPath FROM episodes WHERE podcastFeedUrl = :feedUrl AND downloadPath IS NOT NULL")
    suspend fun getDownloadPathsForPodcast(feedUrl: String): List<String>

    @Query("SELECT * FROM episodes WHERE downloadPath IS NOT NULL")
    suspend fun getDownloadedEpisodesOnce(): List<Episode>

    @Query("""
        SELECT e.* FROM episodes e
        WHERE e.playbackPositionMs > 0 AND e.isCompleted = 0
        ORDER BY e.publishedDate DESC
    """)
    fun getInProgressEpisodes(): Flow<List<Episode>>

    @Query("""
        SELECT e.* FROM episodes e
        INNER JOIN podcasts p ON e.podcastFeedUrl = p.feedUrl
        ORDER BY e.publishedDate DESC
        LIMIT :limit
    """)
    fun getRecentEpisodes(limit: Int = 100): Flow<List<Episode>>

    @Query("SELECT * FROM episodes WHERE downloadPath IS NOT NULL ORDER BY publishedDate DESC")
    fun getDownloadedEpisodes(): Flow<List<Episode>>
}
