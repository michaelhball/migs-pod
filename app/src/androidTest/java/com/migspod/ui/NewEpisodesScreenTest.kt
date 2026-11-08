package com.migspod.ui

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.migspod.data.AppDatabase
import com.migspod.data.Episode
import com.migspod.data.Podcast
import com.migspod.data.PodcastRepository
import com.migspod.ui.screens.NewEpisodesScreen
import com.migspod.ui.theme.MigsPodTheme
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

class NewEpisodesScreenTest {

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
        repository = PodcastRepository(db.podcastDao(), db.episodeDao(), File(context.cacheDir, "test-downloads"))
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun emptyStateShowsMessage() {
        composeTestRule.setContent {
            MigsPodTheme {
                NewEpisodesScreen(repository = repository, onEpisodeClick = {})
            }
        }
        composeTestRule.onNodeWithText("No episodes yet").assertIsDisplayed()
    }

    @Test
    fun showsEpisodesFromMultiplePodcasts() = runTest {
        db.podcastDao().upsertPodcast(Podcast(feedUrl = "feed-1", title = "Podcast Alpha"))
        db.podcastDao().upsertPodcast(Podcast(feedUrl = "feed-2", title = "Podcast Beta"))
        db.episodeDao().insertEpisodes(listOf(
            Episode(
                id = "a-1",
                podcastFeedUrl = "feed-1",
                title = "Alpha Episode One",
                publishedDate = 1704067200000,
            ),
            Episode(
                id = "b-1",
                podcastFeedUrl = "feed-2",
                title = "Beta Episode One",
                publishedDate = 1704153600000,
            ),
        ))

        composeTestRule.setContent {
            MigsPodTheme {
                NewEpisodesScreen(repository = repository, onEpisodeClick = {})
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Alpha Episode One").assertIsDisplayed()
        composeTestRule.onNodeWithText("Beta Episode One").assertIsDisplayed()
    }

    @Test
    fun showsPodcastNamePerEpisode() = runTest {
        db.podcastDao().upsertPodcast(Podcast(feedUrl = "feed-1", title = "My Favorite Show"))
        db.episodeDao().insertEpisodes(listOf(
            Episode(
                id = "ep-1",
                podcastFeedUrl = "feed-1",
                title = "Great Episode",
                publishedDate = 1704067200000,
            ),
        ))

        composeTestRule.setContent {
            MigsPodTheme {
                NewEpisodesScreen(repository = repository, onEpisodeClick = {})
            }
        }

        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodesWithText("My Favorite Show").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("My Favorite Show").assertIsDisplayed()
    }

    @Test
    fun groupsEpisodesUnderTodayThisWeekAndEarlier() = runTest {
        val day = 86_400_000L
        val now = System.currentTimeMillis()
        db.podcastDao().upsertPodcast(Podcast(feedUrl = "f", title = "Show"))
        db.episodeDao().insertEpisodes(listOf(
            Episode(id = "t", podcastFeedUrl = "f", title = "Fresh", publishedDate = now),
            Episode(id = "w", podcastFeedUrl = "f", title = "Recent", publishedDate = now - 3 * day),
            Episode(id = "e", podcastFeedUrl = "f", title = "Old", publishedDate = now - 30 * day),
        ))
        composeTestRule.setContent {
            MigsPodTheme {
                NewEpisodesScreen(repository = repository, onEpisodeClick = {})
            }
        }

        composeTestRule.waitUntil(5000) {
            composeTestRule.onAllNodesWithText("Today").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("This Week").assertIsDisplayed()
        composeTestRule.onNodeWithText("Earlier").assertIsDisplayed()
    }
}
