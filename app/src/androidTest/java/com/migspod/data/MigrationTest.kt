package com.migspod.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MigrationTest {

    private val testDb = "migration-test.db"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    @Test
    fun migrate3To4KeepsDataAndAddsFeedValidators() {
        helper.createDatabase(testDb, 3).apply {
            execSQL(
                "INSERT INTO podcasts (feedUrl, title, author, description, artworkUrl, lastRefreshed) " +
                    "VALUES ('feed-1', 'Show', 'Author', 'Desc', 'https://art', 5)"
            )
            execSQL(
                "INSERT INTO episodes (id, podcastFeedUrl, title, description, audioUrl, publishedDate, " +
                    "durationMs, playbackPositionMs, isCompleted, downloadPath) " +
                    "VALUES ('ep-1', 'feed-1', 'Ep', '', 'https://a.mp3', 1, 60000, 12345, 1, '/files/x.mp3')"
            )
            close()
        }

        // Validates the migrated schema against the exported v4 schema.
        val db = helper.runMigrationsAndValidate(testDb, 4, true, AppDatabase.MIGRATION_3_4)

        db.query("SELECT title, feedEtag, feedLastModified FROM podcasts").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("Show", c.getString(0))
            assertNull(c.getString(1))
            assertNull(c.getString(2))
        }
        db.query("SELECT playbackPositionMs, isCompleted, downloadPath FROM episodes").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(12345L, c.getLong(0))
            assertEquals(1, c.getInt(1))
            assertEquals("/files/x.mp3", c.getString(2))
        }
    }
}
