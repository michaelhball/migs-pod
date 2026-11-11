package com.migspod.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.core.content.edit
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.migspod.data.AppDatabase
import com.migspod.data.Episode
import com.migspod.data.Podcast
import com.migspod.data.PodcastRepository
import com.migspod.player.PlaybackState
import com.migspod.ui.screens.DOWNLOADS_PREFS
import com.migspod.ui.screens.DownloadsScreen
import com.migspod.ui.theme.MigsPodTheme
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

class DownloadsScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var db: AppDatabase
    private lateinit var repository: PodcastRepository

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences(DOWNLOADS_PREFS, Context.MODE_PRIVATE).edit(commit = true) { clear() }
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = PodcastRepository(db.podcastDao(), db.episodeDao(), File(context.cacheDir, "test-downloads"))
    }

    @After
    fun teardown() {
        PlaybackState.currentEpisodeId.value = null
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
        db.podcastDao().upsertPodcast(Podcast(feedUrl = "feed-a", title = "Alpha Podcast"))
        db.podcastDao().upsertPodcast(Podcast(feedUrl = "feed-b", title = "Beta Podcast"))
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
            composeTestRule.onAllNodesWithText("Beta Ep Newer").fetchSemanticsNodes().isNotEmpty()
        }
        // Default is by date; switch to grouped.
        composeTestRule.onNodeWithText("By podcast").performClick()
        composeTestRule.waitForIdle()
        // Beta Podcast should appear first (has the newest episode at date 3000)
        composeTestRule.onNodeWithText("Beta Podcast").assertIsDisplayed()
        composeTestRule.onNodeWithText("Beta Ep Newer").assertIsDisplayed()
        composeTestRule.onNodeWithText("Alpha Podcast").assertIsDisplayed()
        composeTestRule.onNodeWithText("Alpha Ep Old").assertIsDisplayed()
        composeTestRule.onNodeWithText("Alpha Ep Mid").assertIsDisplayed()
    }

    @Test
    fun nonDownloadedEpisodesDoNotAppear() = runTest {
        db.podcastDao().upsertPodcast(Podcast(feedUrl = "feed-1", title = "Test Pod"))
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
        db.podcastDao().upsertPodcast(Podcast(feedUrl = "feed-a", title = "Alpha Podcast"))
        db.podcastDao().upsertPodcast(Podcast(feedUrl = "feed-b", title = "Beta Podcast"))
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

        // Default is by date — the toggle offers "By podcast"
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodesWithText("Beta Ep").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("By podcast").assertIsDisplayed()
        composeTestRule.onNodeWithText("By podcast").performClick()
        composeTestRule.waitForIdle()

        // Now grouped — podcast headers, and the toggle offers "By date"
        composeTestRule.onNodeWithText("By date").assertIsDisplayed()
        composeTestRule.onNodeWithText("Beta Podcast").assertIsDisplayed()

        // Episodes still visible
        composeTestRule.onNodeWithText("Alpha Ep").assertIsDisplayed()
        composeTestRule.onNodeWithText("Beta Ep").assertIsDisplayed()
    }

    @Test
    fun keepsTheChosenViewAfterLeavingAndReturning() = runTest {
        db.podcastDao().upsertPodcast(Podcast(feedUrl = "feed-a", title = "Alpha Podcast"))
        db.episodeDao().insertEpisodes(listOf(
            Episode(id = "a-1", podcastFeedUrl = "feed-a", title = "Alpha Ep",
                publishedDate = 1000, downloadPath = "/fake/a1.mp3"),
        ))

        var onDownloadsTab by mutableStateOf(true)
        composeTestRule.setContent {
            MigsPodTheme {
                if (onDownloadsTab) DownloadsScreen(repository = repository, onEpisodeClick = {})
            }
        }

        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodesWithText("Alpha Ep").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("By podcast").performClick()
        composeTestRule.waitForIdle()

        onDownloadsTab = false
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Alpha Ep").assertDoesNotExist()
        onDownloadsTab = true

        // Still grouped: the toggle offers "By date" and the podcast header shows.
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodesWithText("Alpha Ep").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("By date").assertIsDisplayed()
        composeTestRule.onNodeWithText("1 downloaded").assertIsDisplayed()
    }

    @Test
    fun opensOnTheEpisodeLoadedInThePlayerAndHighlightsIt() = runTest {
        db.podcastDao().upsertPodcast(Podcast(feedUrl = "feed-a", title = "Alpha Podcast"))
        db.episodeDao().insertEpisodes((1..60).map { n ->
            Episode(id = "a-$n", podcastFeedUrl = "feed-a", title = "Alpha Ep $n.",
                publishedDate = 100_000L - n, downloadPath = "/fake/a$n.mp3")
        })
        PlaybackState.currentEpisodeId.value = "a-45"

        composeTestRule.setContent {
            MigsPodTheme {
                DownloadsScreen(repository = repository, onEpisodeClick = {})
            }
        }

        // Row 45 of 60 is far below the first screenful, so it only exists once scrolled to.
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodesWithText("Alpha Ep 45.").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("Alpha Ep 45.").assertIsDisplayed().assertIsSelected()
        composeTestRule.onNodeWithText("Alpha Ep 44.").assertIsDisplayed().assertIsNotSelected()
        composeTestRule.onNodeWithText("Alpha Ep 1.").assertDoesNotExist()

        // The grouped view lands on it too.
        composeTestRule.onNodeWithText("By podcast").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodesWithText("Alpha Ep 45.").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("Alpha Ep 45.").assertIsDisplayed().assertIsSelected()
        composeTestRule.onNodeWithText("Alpha Ep 1.").assertDoesNotExist()
    }

    @Test
    fun deleteButtonIsVisible() = runTest {
        db.podcastDao().upsertPodcast(Podcast(feedUrl = "feed-1", title = "Test Pod"))
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
