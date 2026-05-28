package com.migspod.ui

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.migspod.data.AppDatabase
import com.migspod.data.Episode
import com.migspod.data.Podcast
import com.migspod.data.PodcastRepository
import com.migspod.ui.screens.DownloadsScreen
import com.migspod.ui.theme.MigsPodTheme
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class DownloadsScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var db: AppDatabase
    private lateinit var repository: PodcastRepository

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = PodcastRepository(db.podcastDao(), db.episodeDao())
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun emptyStateShowsMessage() {
        composeTestRule.setContent {
            MigsPodTheme {
                DownloadsScreen(repository = repository, onEpisodeClick = {})
            }
        }
        composeTestRule.onNodeWithText("No downloads").assertIsDisplayed()
    }

    @Test
    fun showsDownloadedEpisodesGroupedByPodcast() = runTest {
        db.podcastDao().insertPodcast(Podcast(feedUrl = "feed-a", title = "Alpha Podcast"))
        db.podcastDao().insertPodcast(Podcast(feedUrl = "feed-b", title = "Beta Podcast"))
        db.episodeDao().insertEpisodes(listOf(
            Episode(
                id = "a-1", podcastFeedUrl = "feed-a", title = "Alpha Ep Old",
                publishedDate = 1000, downloadPath = "/fake/a1.mp3",
            ),
            Episode(
                id = "b-1", podcastFeedUrl = "feed-b", title = "Beta Ep Newer",
                publishedDate = 3000, downloadPath = "/fake/b1.mp3",
            ),
            Episode(
                id = "a-2", podcastFeedUrl = "feed-a", title = "Alpha Ep Mid",
                publishedDate = 2000, downloadPath = "/fake/a2.mp3",
            ),
        ))

        composeTestRule.setContent {
            MigsPodTheme {
                DownloadsScreen(repository = repository, onEpisodeClick = {})
            }
        }

        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodesWithText("Beta Podcast").fetchSemanticsNodes().isNotEmpty()
        }
        // Beta Podcast should appear first (has the newest episode at date 3000)
        composeTestRule.onNodeWithText("Beta Podcast").assertIsDisplayed()
        composeTestRule.onNodeWithText("Beta Ep Newer").assertIsDisplayed()
        composeTestRule.onNodeWithText("Alpha Podcast").assertIsDisplayed()
        composeTestRule.onNodeWithText("Alpha Ep Old").assertIsDisplayed()
        composeTestRule.onNodeWithText("Alpha Ep Mid").assertIsDisplayed()
    }

    @Test
    fun nonDownloadedEpisodesDoNotAppear() = runTest {
        db.podcastDao().insertPodcast(Podcast(feedUrl = "feed-1", title = "Test Pod"))
        db.episodeDao().insertEpisodes(listOf(
            Episode(id = "ep-1", podcastFeedUrl = "feed-1", title = "Downloaded Ep", downloadPath = "/fake.mp3"),
            Episode(id = "ep-2", podcastFeedUrl = "feed-1", title = "Not Downloaded Ep"),
        ))

        composeTestRule.setContent {
            MigsPodTheme {
                DownloadsScreen(repository = repository, onEpisodeClick = {})
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Downloaded Ep").assertIsDisplayed()
        composeTestRule.onNodeWithText("Not Downloaded Ep").assertDoesNotExist()
    }

    @Test
    fun sortToggleSwitchesBetweenGroupedAndDateView() = runTest {
        db.podcastDao().insertPodcast(Podcast(feedUrl = "feed-a", title = "Alpha Podcast"))
        db.podcastDao().insertPodcast(Podcast(feedUrl = "feed-b", title = "Beta Podcast"))
        db.episodeDao().insertEpisodes(listOf(
            Episode(id = "a-1", podcastFeedUrl = "feed-a", title = "Alpha Ep",
                publishedDate = 1000, downloadPath = "/fake/a1.mp3"),
            Episode(id = "b-1", podcastFeedUrl = "feed-b", title = "Beta Ep",
                publishedDate = 2000, downloadPath = "/fake/b1.mp3"),
        ))

        composeTestRule.setContent {
            MigsPodTheme {
                DownloadsScreen(repository = repository, onEpisodeClick = {})
            }
        }

        // Default is grouped — should show podcast headers
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodesWithText("Beta Podcast").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("Beta Podcast").assertIsDisplayed()

        // Toggle shows "By date" button, tap it to switch
        composeTestRule.onNodeWithText("By date").assertIsDisplayed()
        composeTestRule.onNodeWithText("By date").performClick()
        composeTestRule.waitForIdle()

        // Now in date mode — button should say "By podcast"
        composeTestRule.onNodeWithText("By podcast").assertIsDisplayed()

        // Episodes still visible
        composeTestRule.onNodeWithText("Alpha Ep").assertIsDisplayed()
        composeTestRule.onNodeWithText("Beta Ep").assertIsDisplayed()
    }

    @Test
    fun deleteButtonIsVisible() = runTest {
        db.podcastDao().insertPodcast(Podcast(feedUrl = "feed-1", title = "Test Pod"))
        db.episodeDao().insertEpisodes(listOf(
            Episode(id = "ep-1", podcastFeedUrl = "feed-1", title = "Ep One", downloadPath = "/fake.mp3"),
        ))

        composeTestRule.setContent {
            MigsPodTheme {
                DownloadsScreen(repository = repository, onEpisodeClick = {})
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithContentDescription("Delete download").assertIsDisplayed()
    }
}
