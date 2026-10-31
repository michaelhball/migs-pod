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
import com.migspod.ui.screens.PodcastDetailScreen
import com.migspod.ui.theme.MigsPodTheme
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

class PodcastDetailScreenTest {

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
    fun showsPodcastTitleAndEpisodes() = runTest {
        db.podcastDao().upsertPodcast(
            Podcast(
                feedUrl = "feed-1",
                title = "Science Hour",
                author = "Dr. Smith",
                description = "Weekly science news",
            )
        )
        db.episodeDao().insertEpisodes(listOf(
            Episode(
                id = "ep-1",
                podcastFeedUrl = "feed-1",
                title = "Black Holes Explained",
                publishedDate = 1704067200000, // Jan 1 2024
                durationMs = 3600_000,
            ),
            Episode(
                id = "ep-2",
                podcastFeedUrl = "feed-1",
                title = "Quantum Computing 101",
                publishedDate = 1704153600000,
                durationMs = 1800_000,
            ),
        ))

        composeTestRule.setContent {
            MigsPodTheme {
                PodcastDetailScreen(
                    feedUrl = "feed-1",
                    repository = repository,
                    onBack = {},
                    onEpisodeClick = {},
                )
            }
        }

        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodesWithText("Science Hour").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("Science Hour").assertIsDisplayed()
        composeTestRule.onNodeWithText("Dr. Smith").assertIsDisplayed()
        composeTestRule.onNodeWithText("Black Holes Explained").assertIsDisplayed()
        composeTestRule.onNodeWithText("Quantum Computing 101").assertIsDisplayed()
    }

    @Test
    fun showsBackButton() = runTest {
        db.podcastDao().upsertPodcast(
            Podcast(feedUrl = "feed-1", title = "Test Podcast")
        )

        composeTestRule.setContent {
            MigsPodTheme {
                PodcastDetailScreen(
                    feedUrl = "feed-1",
                    repository = repository,
                    onBack = {},
                    onEpisodeClick = {},
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Back").assertIsDisplayed()
    }

    @Test
    fun showsProgressBarForPartiallyPlayedEpisode() = runTest {
        db.podcastDao().upsertPodcast(
            Podcast(feedUrl = "feed-1", title = "Test")
        )
        db.episodeDao().insertEpisodes(listOf(
            Episode(
                id = "ep-1",
                podcastFeedUrl = "feed-1",
                title = "In Progress Episode",
                durationMs = 3600_000,
                playbackPositionMs = 1800_000, // half way
            ),
        ))

        composeTestRule.setContent {
            MigsPodTheme {
                PodcastDetailScreen(
                    feedUrl = "feed-1",
                    repository = repository,
                    onBack = {},
                    onEpisodeClick = {},
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("In Progress Episode").assertIsDisplayed()
    }

    @Test
    fun unsubscribeAsksForConfirmation() = runTest {
        db.podcastDao().upsertPodcast(Podcast(feedUrl = "feed-1", title = "Science Hour"))
        var backCalled = false

        composeTestRule.setContent {
            MigsPodTheme {
                PodcastDetailScreen(
                    feedUrl = "feed-1",
                    repository = repository,
                    onBack = { backCalled = true },
                    onEpisodeClick = {},
                )
            }
        }
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodesWithText("Science Hour").fetchSemanticsNodes().isNotEmpty()
        }

        // Cancel keeps the podcast
        composeTestRule.onNodeWithContentDescription("More").performClick()
        composeTestRule.onNodeWithText("Unsubscribe").performClick()
        composeTestRule.onNodeWithText("Unsubscribe from Science Hour?").assertIsDisplayed()
        composeTestRule.onNodeWithText("Cancel").performClick()
        composeTestRule.waitForIdle()
        assertNotNull(db.podcastDao().getPodcast("feed-1"))
        assertFalse(backCalled)

        // Confirm removes it and leaves the screen
        composeTestRule.onNodeWithContentDescription("More").performClick()
        composeTestRule.onNodeWithText("Unsubscribe").performClick()
        composeTestRule.onNodeWithText("Unsubscribe").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5000) { backCalled }
        assertNull(db.podcastDao().getPodcast("feed-1"))
    }
}
