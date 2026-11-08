package com.migspod.ui

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.migspod.data.AppDatabase
import com.migspod.data.Episode
import com.migspod.data.Podcast
import com.migspod.data.PodcastRepository
import com.migspod.ui.screens.EpisodeDetailContent
import com.migspod.ui.theme.MigsPodTheme
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

class EpisodeDetailScreenTest {

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
    fun takesFullHeightBeforeTheEpisodeHasLoaded() {
        composeTestRule.setContent {
            MigsPodTheme {
                Column(Modifier.width(300.dp).height(400.dp)) {
                    Box(Modifier.testTag("content")) {
                        EpisodeDetailContent(
                            episodeId = "not-loaded",
                            repository = repository,
                            onNowPlaying = {},
                        )
                    }
                }
            }
        }

        composeTestRule.onNodeWithTag("content").assertHeightIsEqualTo(400.dp)
    }

    @Test
    fun showsEpisodeTitleAndDescription() = runTest {
        db.podcastDao().upsertPodcast(
            Podcast(feedUrl = "feed-1", title = "Science Show", author = "Dr. Smith")
        )
        db.episodeDao().insertEpisodes(listOf(
            Episode(
                id = "ep-1",
                podcastFeedUrl = "feed-1",
                title = "The Big Bang",
                description = "A deep dive into the origins of the universe and what we know so far.",
                publishedDate = 1704067200000,
                durationMs = 3600_000,
            )
        ))

        composeTestRule.setContent {
            MigsPodTheme {
                EpisodeDetailContent(
                    episodeId = "ep-1",
                    repository = repository,
                    onNowPlaying = {},
                )
            }
        }

        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodesWithText("The Big Bang").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("The Big Bang").assertIsDisplayed()
        composeTestRule.onNodeWithText("Science Show").assertIsDisplayed()
        composeTestRule.onNodeWithText("A deep dive into the origins of the universe and what we know so far.").assertIsDisplayed()
    }

    @Test
    fun showsPlayButton() = runTest {
        db.podcastDao().upsertPodcast(Podcast(feedUrl = "feed-1", title = "Test"))
        db.episodeDao().insertEpisodes(listOf(
            Episode(id = "ep-1", podcastFeedUrl = "feed-1", title = "Ep 1")
        ))

        composeTestRule.setContent {
            MigsPodTheme {
                EpisodeDetailContent(
                    episodeId = "ep-1",
                    repository = repository,
                    onNowPlaying = {},
                )
            }
        }

        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodesWithText("Play").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("Play").assertIsDisplayed()
    }

    @Test
    fun showsDownloadButtonForNotDownloaded() = runTest {
        db.podcastDao().upsertPodcast(Podcast(feedUrl = "feed-1", title = "Test"))
        db.episodeDao().insertEpisodes(listOf(
            Episode(id = "ep-1", podcastFeedUrl = "feed-1", title = "Ep 1", audioUrl = "https://example.com/ep.mp3")
        ))

        composeTestRule.setContent {
            MigsPodTheme {
                EpisodeDetailContent(
                    episodeId = "ep-1",
                    repository = repository,
                    onNowPlaying = {},
                )
            }
        }

        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodesWithText("Download").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("Download").assertIsDisplayed()
    }

    @Test
    fun showsDeleteDownloadForDownloaded() = runTest {
        db.podcastDao().upsertPodcast(Podcast(feedUrl = "feed-1", title = "Test"))
        db.episodeDao().insertEpisodes(listOf(
            Episode(id = "ep-1", podcastFeedUrl = "feed-1", title = "Ep 1", downloadPath = "/fake.mp3")
        ))

        composeTestRule.setContent {
            MigsPodTheme {
                EpisodeDetailContent(
                    episodeId = "ep-1",
                    repository = repository,
                    onNowPlaying = {},
                )
            }
        }

        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodesWithText("Delete download").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("Delete download").assertIsDisplayed()
    }
}
