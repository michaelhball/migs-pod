package com.migspod.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RssFeedParserInstrumentedTest {

    private val sampleFeed = """
        <?xml version="1.0" encoding="UTF-8"?>
        <rss version="2.0" xmlns:itunes="http://www.itunes.com/dtds/podcast-1.0.dtd">
          <channel>
            <title>Test Podcast</title>
            <description><![CDATA[<p>A great podcast about testing</p>]]></description>
            <itunes:author>Test Author</itunes:author>
            <itunes:image href="https://example.com/artwork.jpg"/>
            <item>
              <title>Episode 1</title>
              <guid>ep-001</guid>
              <description>First episode description</description>
              <enclosure url="https://example.com/ep1.mp3" type="audio/mpeg" length="12345"/>
              <pubDate>Mon, 01 Jan 2024 12:00:00 +0000</pubDate>
              <itunes:duration>1:30:00</itunes:duration>
            </item>
            <item>
              <title>Episode 2</title>
              <guid>ep-002</guid>
              <description>Second episode</description>
              <enclosure url="https://example.com/ep2.mp3" type="audio/mpeg" length="12345"/>
              <pubDate>Mon, 08 Jan 2024 12:00:00 +0000</pubDate>
              <itunes:duration>45:30</itunes:duration>
            </item>
            <item>
              <title>Video Episode</title>
              <guid>ep-003</guid>
              <enclosure url="https://example.com/ep3.mp4" type="video/mp4" length="99999"/>
            </item>
          </channel>
        </rss>
    """.trimIndent()

    @Test
    fun parse_feedMetadata() {
        val feed = RssFeedParser.parse(sampleFeed.byteInputStream())
        assertEquals("Test Podcast", feed.title)
        assertEquals("Test Author", feed.author)
        assertEquals("https://example.com/artwork.jpg", feed.artworkUrl)
        assertTrue(feed.description.contains("testing"))
    }

    @Test
    fun parse_extractsAudioEpisodesOnly() {
        val feed = RssFeedParser.parse(sampleFeed.byteInputStream())
        // Should have 2 audio episodes, not the video one
        assertEquals(2, feed.episodes.size)
        assertEquals("ep-001", feed.episodes[0].guid)
        assertEquals("ep-002", feed.episodes[1].guid)
    }

    @Test
    fun parse_episodeDetails() {
        val feed = RssFeedParser.parse(sampleFeed.byteInputStream())
        val ep1 = feed.episodes[0]
        assertEquals("Episode 1", ep1.title)
        assertEquals("https://example.com/ep1.mp3", ep1.audioUrl)
        assertEquals("First episode description", ep1.description)
        assertTrue(ep1.publishedDate > 0)
        assertEquals((1 * 3600 + 30 * 60) * 1000L, ep1.durationMs)
    }

    @Test
    fun parse_episodeDurationMinutesSeconds() {
        val feed = RssFeedParser.parse(sampleFeed.byteInputStream())
        val ep2 = feed.episodes[1]
        assertEquals((45 * 60 + 30) * 1000L, ep2.durationMs)
    }

    @Test
    fun parse_stripsHtmlFromDescription() {
        val feed = RssFeedParser.parse(sampleFeed.byteInputStream())
        // The CDATA description has <p> tags that should be stripped
        assertTrue(!feed.description.contains("<p>"))
        assertTrue(feed.description.contains("testing"))
    }

    @Test
    fun parse_guidFallsBackToAudioUrl() {
        val feedXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0">
              <channel>
                <title>No Guid Podcast</title>
                <item>
                  <title>No Guid Episode</title>
                  <enclosure url="https://example.com/noguid.mp3" type="audio/mpeg" length="1"/>
                </item>
              </channel>
            </rss>
        """.trimIndent()
        val feed = RssFeedParser.parse(feedXml.byteInputStream())
        assertEquals(1, feed.episodes.size)
        assertEquals("https://example.com/noguid.mp3", feed.episodes[0].guid)
    }

    @Test
    fun parse_emptyFeed() {
        val feedXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0">
              <channel>
                <title>Empty Podcast</title>
              </channel>
            </rss>
        """.trimIndent()
        val feed = RssFeedParser.parse(feedXml.byteInputStream())
        assertEquals("Empty Podcast", feed.title)
        assertEquals(0, feed.episodes.size)
    }

    @Test
    fun parse_showNotesAndLooseDates() {
        val feedXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0">
              <channel>
                <title>Notes Podcast</title>
                <item>
                  <title>Episode</title>
                  <pubDate>05 Jan 2024 10:00 GMT</pubDate>
                  <description><![CDATA[<p>Tom &amp; Jerry&rsquo;s show.</p><p>Part two.</p>]]></description>
                  <enclosure url="https://example.com/ep.mp3" type="audio/mpeg" length="1"/>
                </item>
              </channel>
            </rss>
        """.trimIndent()
        val episode = RssFeedParser.parse(feedXml.byteInputStream()).episodes.single()
        assertEquals("Tom & Jerry’s show.\n\nPart two.", episode.description)
        assertEquals(1_704_448_800_000L, episode.publishedDate)
    }
}
