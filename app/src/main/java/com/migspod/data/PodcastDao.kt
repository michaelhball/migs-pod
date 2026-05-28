package com.migspod.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PodcastDao {
    @Query("SELECT * FROM podcasts ORDER BY title ASC")
    fun getAllPodcasts(): Flow<List<Podcast>>

    @Query("SELECT * FROM podcasts WHERE feedUrl = :feedUrl")
    suspend fun getPodcast(feedUrl: String): Podcast?

    @Query("SELECT * FROM podcasts WHERE feedUrl IN (:feedUrls)")
    suspend fun getPodcasts(feedUrls: List<String>): List<Podcast>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPodcast(podcast: Podcast)

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

    @Query("UPDATE episodes SET playbackPositionMs = :positionMs WHERE id = :episodeId")
    suspend fun updatePlaybackPosition(episodeId: String, positionMs: Long)

    @Query("UPDATE episodes SET isCompleted = :completed WHERE id = :episodeId")
    suspend fun markCompleted(episodeId: String, completed: Boolean)

    @Query("UPDATE episodes SET downloadPath = :path WHERE id = :episodeId")
    suspend fun updateDownloadPath(episodeId: String, path: String?)

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
