package com.migspod.ui

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.migspod.data.AppDatabase
import com.migspod.data.Podcast
import com.migspod.data.PodcastRepository
import com.migspod.ui.screens.LibraryScreen
import com.migspod.ui.theme.MigsPodTheme
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class LibraryScreenTest {

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
    fun emptyLibraryShowsMessage() {
        composeTestRule.setContent {
            MigsPodTheme {
                LibraryScreen(repository = repository, onPodcastClick = {})
            }
        }
        composeTestRule.onNodeWithText("No podcasts yet").assertIsDisplayed()
    }

    @Test
    fun subscribedPodcastsAppearInGrid() = runTest {
        db.podcastDao().insertPodcast(
            Podcast(feedUrl = "feed-1", title = "My Cool Podcast", author = "Host")
        )
        db.podcastDao().insertPodcast(
            Podcast(feedUrl = "feed-2", title = "Another Show", author = "Host 2")
        )

        composeTestRule.setContent {
            MigsPodTheme {
                LibraryScreen(repository = repository, onPodcastClick = {})
            }
        }

        composeTestRule.onNodeWithText("My Cool Podcast").assertIsDisplayed()
        composeTestRule.onNodeWithText("Another Show").assertIsDisplayed()
    }
}
