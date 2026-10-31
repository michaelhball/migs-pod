package com.migspod.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DatabaseTest {

    private lateinit var db: AppDatabase
    private lateinit var podcastDao: PodcastDao
    private lateinit var episodeDao: EpisodeDao
    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        podcastDao = db.podcastDao()
        episodeDao = db.episodeDao()
    }

    @After
    fun teardown() {
        db.close()
    }

    // --- Podcast CRUD ---

    @Test
    fun insertAndGetPodcast() = runTest {
        val podcast = Podcast(
            feedUrl = "https://example.com/feed.xml",
            title = "Test Podcast",
            author = "Author",
        )
        podcastDao.upsertPodcast(podcast)
        val result = podcastDao.getPodcast("https://example.com/feed.xml")
        assertNotNull(result)
        assertEquals("Test Podcast", result!!.title)
    }

    @Test
    fun getAllPodcastsSortedByTitle() = runTest {
        podcastDao.upsertPodcast(Podcast(feedUrl = "url-b", title = "Bravo"))
        podcastDao.upsertPodcast(Podcast(feedUrl = "url-a", title = "Alpha"))
        podcastDao.upsertPodcast(Podcast(feedUrl = "url-c", title = "Charlie"))
        val all = podcastDao.getAllPodcasts().first()
        assertEquals(listOf("Alpha", "Bravo", "Charlie"), all.map { it.title })
    }

    @Test
    fun deletePodcast() = runTest {
        val podcast = Podcast(feedUrl = "https://example.com/feed.xml", title = "Test")
        podcastDao.upsertPodcast(podcast)
        podcastDao.deletePodcast(podcast)
        assertNull(podcastDao.getPodcast("https://example.com/feed.xml"))
    }

    @Test
    fun deletePodcastCascadesEpisodes() = runTest {
        val podcast = Podcast(feedUrl = "feed-1", title = "Test")
        podcastDao.upsertPodcast(podcast)
        episodeDao.insertEpisodes(listOf(
            Episode(id = "ep-1", podcastFeedUrl = "feed-1", title = "Ep 1"),
            Episode(id = "ep-2", podcastFeedUrl = "feed-1", title = "Ep 2"),
        ))
        podcastDao.deletePodcast(podcast)
        val episodes = episodeDao.getEpisodesForPodcast("feed-1").first()
        assertTrue(episodes.isEmpty())
    }

    // --- Episode operations ---

    @Test
    fun insertEpisodesIgnoresDuplicates() = runTest {
        podcastDao.upsertPodcast(Podcast(feedUrl = "feed-1", title = "Test"))
        episodeDao.insertEpisodes(listOf(
            Episode(id = "ep-1", podcastFeedUrl = "feed-1", title = "Original Title"),
        ))
        // Insert again with different title — should be ignored
        episodeDao.insertEpisodes(listOf(
            Episode(id = "ep-1", podcastFeedUrl = "feed-1", title = "New Title"),
        ))
        val ep = episodeDao.getEpisode("ep-1")
        assertEquals("Original Title", ep!!.title)
    }

    @Test
    fun updatePlaybackPosition() = runTest {
        podcastDao.upsertPodcast(Podcast(feedUrl = "feed-1", title = "Test"))
        episodeDao.insertEpisodes(listOf(
            Episode(id = "ep-1", podcastFeedUrl = "feed-1", title = "Ep 1"),
        ))
        episodeDao.updatePlaybackPosition("ep-1", 30_000)
        val ep = episodeDao.getEpisode("ep-1")
        assertEquals(30_000L, ep!!.playbackPositionMs)
    }

    @Test
    fun markCompleted() = runTest {
        podcastDao.upsertPodcast(Podcast(feedUrl = "feed-1", title = "Test"))
        episodeDao.insertEpisodes(listOf(
            Episode(id = "ep-1", podcastFeedUrl = "feed-1", title = "Ep 1"),
        ))
        episodeDao.markCompleted("ep-1", true)
        val ep = episodeDao.getEpisode("ep-1")
        assertTrue(ep!!.isCompleted)
    }

    @Test
    fun getInProgressEpisodes() = runTest {
        podcastDao.upsertPodcast(Podcast(feedUrl = "feed-1", title = "Test"))
        episodeDao.insertEpisodes(listOf(
            Episode(id = "ep-1", podcastFeedUrl = "feed-1", title = "In Progress", publishedDate = 2000),
            Episode(id = "ep-2", podcastFeedUrl = "feed-1", title = "Not Started", publishedDate = 1000),
            Episode(id = "ep-3", podcastFeedUrl = "feed-1", title = "Completed", publishedDate = 3000),
        ))
        episodeDao.updatePlaybackPosition("ep-1", 5000)
        episodeDao.updatePlaybackPosition("ep-3", 10000)
        episodeDao.markCompleted("ep-3", true)

        val inProgress = episodeDao.getInProgressEpisodes().first()
        assertEquals(1, inProgress.size)
        assertEquals("ep-1", inProgress[0].id)
    }

    @Test
    fun episodesSortedByDateDescending() = runTest {
        podcastDao.upsertPodcast(Podcast(feedUrl = "feed-1", title = "Test"))
        episodeDao.insertEpisodes(listOf(
            Episode(id = "ep-old", podcastFeedUrl = "feed-1", title = "Old", publishedDate = 1000),
            Episode(id = "ep-new", podcastFeedUrl = "feed-1", title = "New", publishedDate = 3000),
            Episode(id = "ep-mid", podcastFeedUrl = "feed-1", title = "Mid", publishedDate = 2000),
        ))
        val episodes = episodeDao.getEpisodesForPodcast("feed-1").first()
        assertEquals(listOf("ep-new", "ep-mid", "ep-old"), episodes.map { it.id })
    }

    // --- New episodes / recent episodes ---

    @Test
    fun getRecentEpisodesAcrossPodcasts() = runTest {
        podcastDao.upsertPodcast(Podcast(feedUrl = "feed-1", title = "Podcast A"))
        podcastDao.upsertPodcast(Podcast(feedUrl = "feed-2", title = "Podcast B"))
        episodeDao.insertEpisodes(listOf(
            Episode(id = "a-1", podcastFeedUrl = "feed-1", title = "A ep", publishedDate = 1000),
            Episode(id = "b-1", podcastFeedUrl = "feed-2", title = "B ep", publishedDate = 3000),
            Episode(id = "a-2", podcastFeedUrl = "feed-1", title = "A ep2", publishedDate = 2000),
        ))
        val recent = episodeDao.getRecentEpisodes(10).first()
        assertEquals(3, recent.size)
        assertEquals("b-1", recent[0].id) // newest first
        assertEquals("a-2", recent[1].id)
        assertEquals("a-1", recent[2].id)
    }

    @Test
    fun getRecentEpisodesRespectsLimit() = runTest {
        podcastDao.upsertPodcast(Podcast(feedUrl = "feed-1", title = "Test"))
        episodeDao.insertEpisodes(listOf(
            Episode(id = "ep-1", podcastFeedUrl = "feed-1", title = "Ep 1", publishedDate = 1000),
            Episode(id = "ep-2", podcastFeedUrl = "feed-1", title = "Ep 2", publishedDate = 2000),
            Episode(id = "ep-3", podcastFeedUrl = "feed-1", title = "Ep 3", publishedDate = 3000),
        ))
        val recent = episodeDao.getRecentEpisodes(2).first()
        assertEquals(2, recent.size)
    }

    // --- Downloaded episodes ---

    @Test
    fun getDownloadedEpisodes() = runTest {
        podcastDao.upsertPodcast(Podcast(feedUrl = "feed-1", title = "Test"))
        episodeDao.insertEpisodes(listOf(
            Episode(id = "ep-1", podcastFeedUrl = "feed-1", title = "Downloaded", downloadPath = "/path/to/file.mp3"),
            Episode(id = "ep-2", podcastFeedUrl = "feed-1", title = "Not Downloaded"),
        ))
        val downloaded = episodeDao.getDownloadedEpisodes().first()
        assertEquals(1, downloaded.size)
        assertEquals("ep-1", downloaded[0].id)
    }

    // --- Refresh preserves user state (CRITICAL) ---

    @Test
    fun reinsertingEpisodePreservesPlaybackPosition() = runTest {
        podcastDao.upsertPodcast(Podcast(feedUrl = "feed-1", title = "Test"))
        episodeDao.insertEpisodes(listOf(
            Episode(id = "ep-1", podcastFeedUrl = "feed-1", title = "Ep 1"),
        ))
        episodeDao.updatePlaybackPosition("ep-1", 45_000)
        // Simulate a feed refresh re-inserting the same episode with no position
        episodeDao.insertEpisodes(listOf(
            Episode(id = "ep-1", podcastFeedUrl = "feed-1", title = "Ep 1 retitled", playbackPositionMs = 0),
        ))
        val ep = episodeDao.getEpisode("ep-1")
        assertEquals(45_000L, ep!!.playbackPositionMs)
    }

    @Test
    fun reinsertingEpisodePreservesCompletedFlag() = runTest {
        podcastDao.upsertPodcast(Podcast(feedUrl = "feed-1", title = "Test"))
        episodeDao.insertEpisodes(listOf(
            Episode(id = "ep-1", podcastFeedUrl = "feed-1", title = "Ep 1"),
        ))
        episodeDao.markCompleted("ep-1", true)
        episodeDao.insertEpisodes(listOf(
            Episode(id = "ep-1", podcastFeedUrl = "feed-1", title = "Ep 1", isCompleted = false),
        ))
        val ep = episodeDao.getEpisode("ep-1")
        assertTrue(ep!!.isCompleted)
    }

    @Test
    fun reinsertingEpisodePreservesDownloadPath() = runTest {
        podcastDao.upsertPodcast(Podcast(feedUrl = "feed-1", title = "Test"))
        episodeDao.insertEpisodes(listOf(
            Episode(id = "ep-1", podcastFeedUrl = "feed-1", title = "Ep 1"),
        ))
        episodeDao.updateDownloadPath("ep-1", "/downloads/ep1.mp3")
        episodeDao.insertEpisodes(listOf(
            Episode(id = "ep-1", podcastFeedUrl = "feed-1", title = "Ep 1", downloadPath = null),
        ))
        val ep = episodeDao.getEpisode("ep-1")
        assertEquals("/downloads/ep1.mp3", ep!!.downloadPath)
    }

    @Test
    fun markCompletedFalseReverses() = runTest {
        podcastDao.upsertPodcast(Podcast(feedUrl = "feed-1", title = "Test"))
        episodeDao.insertEpisodes(listOf(
            Episode(id = "ep-1", podcastFeedUrl = "feed-1", title = "Ep 1"),
        ))
        episodeDao.markCompleted("ep-1", true)
        episodeDao.markCompleted("ep-1", false)
        val ep = episodeDao.getEpisode("ep-1")
        assertFalse(ep!!.isCompleted)
    }

    @Test
    fun getPodcastsBatch() = runTest {
        podcastDao.upsertPodcast(Podcast(feedUrl = "feed-1", title = "Alpha"))
        podcastDao.upsertPodcast(Podcast(feedUrl = "feed-2", title = "Bravo"))
        podcastDao.upsertPodcast(Podcast(feedUrl = "feed-3", title = "Charlie"))
        val batch = podcastDao.getPodcasts(listOf("feed-1", "feed-3", "missing"))
        assertEquals(2, batch.size)
        assertEquals(setOf("Alpha", "Charlie"), batch.map { it.title }.toSet())
    }

    @Test
    fun updateDownloadPath() = runTest {
        podcastDao.upsertPodcast(Podcast(feedUrl = "feed-1", title = "Test"))
        episodeDao.insertEpisodes(listOf(
            Episode(id = "ep-1", podcastFeedUrl = "feed-1", title = "Ep 1"),
        ))
        episodeDao.updateDownloadPath("ep-1", "/downloads/ep1.mp3")
        val ep = episodeDao.getEpisode("ep-1")
        assertEquals("/downloads/ep1.mp3", ep!!.downloadPath)

        // Clear download
        episodeDao.updateDownloadPath("ep-1", null)
        val epCleared = episodeDao.getEpisode("ep-1")
        assertNull(epCleared!!.downloadPath)
    }

}
