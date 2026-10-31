package com.migspod.ui

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.migspod.data.AppDatabase
import com.migspod.data.Episode
import com.migspod.data.Podcast
import com.migspod.data.PodcastRepository
import com.migspod.player.PlaybackState
import com.migspod.ui.components.MiniPlayer
import com.migspod.ui.theme.MigsPodTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

class MiniPlayerTest {

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
        runBlocking {
            db.podcastDao().upsertPodcast(Podcast(feedUrl = "feed-1", title = "Science Hour"))
            db.episodeDao().insertEpisodes(listOf(Episode(id = "ep-1", podcastFeedUrl = "feed-1", title = "Black Holes")))
        }
    }

    @After
    fun teardown() {
        PlaybackState.currentEpisodeId.value = null
        db.close()
    }

    @Test
    fun pausedPlayerCanBeClosed() {
        PlaybackState.currentEpisodeId.value = "ep-1"
        composeTestRule.setContent {
            MigsPodTheme {
                MiniPlayer(repository = repository, onClick = {})
            }
        }
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodesWithText("Black Holes").fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule.onNodeWithContentDescription("Close player").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Close player").performClick()
        composeTestRule.waitForIdle()

        assertNull(PlaybackState.currentEpisodeId.value)
        composeTestRule.onNodeWithContentDescription("Close player").assertDoesNotExist()
    }
}
