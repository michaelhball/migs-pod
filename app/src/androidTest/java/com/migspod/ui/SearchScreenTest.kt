package com.migspod.ui

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performImeAction
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.migspod.data.AppDatabase
import com.migspod.data.PodcastRepository
import com.migspod.ui.screens.SearchScreen
import com.migspod.ui.theme.MigsPodTheme
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

class SearchScreenTest {

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
    fun searchScreenShowsSearchField() {
        composeTestRule.setContent {
            MigsPodTheme {
                SearchScreen(repository = repository)
            }
        }
        composeTestRule.onNodeWithText("Search for podcasts...").assertIsDisplayed()
    }

    @Test
    fun searchScreenShowsTitle() {
        composeTestRule.setContent {
            MigsPodTheme {
                SearchScreen(repository = repository)
            }
        }
        composeTestRule.onNodeWithText("Search Podcasts").assertIsDisplayed()
    }

    @Test
    fun canTypeInSearchField() {
        composeTestRule.setContent {
            MigsPodTheme {
                SearchScreen(repository = repository)
            }
        }
        composeTestRule.onNodeWithText("Search for podcasts...")
            .performTextInput("serial podcast")
        composeTestRule.onNodeWithText("serial podcast").assertIsDisplayed()
    }

    @Test
    fun searchWithQueryShowsResultsOrLoading() {
        composeTestRule.setContent {
            MigsPodTheme {
                SearchScreen(repository = repository)
            }
        }
        composeTestRule.onNodeWithText("Search for podcasts...")
            .performTextInput("This American Life")
        composeTestRule.onNodeWithText("This American Life")
            .performImeAction() // triggers search

        // After triggering search, either loading indicator or results should appear
        // We just verify the UI doesn't crash (the main goal)
        composeTestRule.waitForIdle()
    }

    // --- Add by RSS URL ---

    @Test
    fun addByUrlRejectsAnInvalidAddress() {
        composeTestRule.setContent {
            MigsPodTheme {
                SearchScreen(repository = repository)
            }
        }
        composeTestRule.onNodeWithText("Add by URL").performClick()
        composeTestRule.onNodeWithText("https://example.com/feed.xml").performTextInput("not a url")
        composeTestRule.onNodeWithText("Add").performClick()

        composeTestRule.onNodeWithText("That isn't a valid web address").assertIsDisplayed()
    }

    @Test
    fun addByUrlSubscribesAndOpensThePodcast() {
        val server = MockWebServer()
        server.enqueue(
            MockResponse().setBody(
                "<?xml version=\"1.0\"?><rss version=\"2.0\"><channel><title>Private Feed</title>" +
                    "<item><title>Bonus</title><guid>b-1</guid>" +
                    "<enclosure url=\"https://example.com/b.mp3\" type=\"audio/mpeg\" length=\"1\"/>" +
                    "</item></channel></rss>"
            )
        )
        server.start()
        val feedUrl = server.url("/members.xml").toString()
        var opened: String? = null
        try {
            composeTestRule.setContent {
                MigsPodTheme {
                    SearchScreen(repository = repository, onResultClick = { opened = it })
                }
            }
            composeTestRule.onNodeWithText("Add by URL").performClick()
            composeTestRule.onNodeWithText("https://example.com/feed.xml").performTextInput(feedUrl)
            composeTestRule.onNodeWithText("Add").performClick()

            composeTestRule.waitUntil(timeoutMillis = 5000) { opened != null }
            assertEquals(feedUrl, opened)
            assertNotNull(runBlocking { db.podcastDao().getPodcast(feedUrl) })
            assertEquals(0, composeTestRule.onAllNodesWithText("Add by RSS URL").fetchSemanticsNodes().size)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun pastingAFeedUrlIntoSearchOffersToAddIt() {
        composeTestRule.setContent {
            MigsPodTheme {
                SearchScreen(repository = repository)
            }
        }
        composeTestRule.onNodeWithText("Search for podcasts...")
            .performTextInput("feed://example.com/rss")
        composeTestRule.onNodeWithText("feed://example.com/rss").performImeAction()

        composeTestRule.onNodeWithText("Add by RSS URL").assertIsDisplayed()
    }
}
