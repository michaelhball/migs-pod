package com.migspod.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FeedUrlTest {

    @Test
    fun keepsHttpsUrl() {
        assertEquals("https://feeds.example.com/show.xml", normalizeFeedUrl("https://feeds.example.com/show.xml"))
    }

    @Test
    fun keepsPlainHttp() {
        assertEquals("http://example.com/rss", normalizeFeedUrl("http://example.com/rss"))
    }

    @Test
    fun trimsWhitespace() {
        assertEquals("https://example.com/rss", normalizeFeedUrl("  https://example.com/rss \n"))
    }

    @Test
    fun addsHttpsWhenSchemeMissing() {
        assertEquals("https://example.com/rss", normalizeFeedUrl("example.com/rss"))
    }

    @Test
    fun rewritesPodcastAppSchemes() {
        assertEquals("https://example.com/rss", normalizeFeedUrl("feed://example.com/rss"))
        assertEquals("https://example.com/rss", normalizeFeedUrl("itpc://example.com/rss"))
        assertEquals("https://example.com/rss", normalizeFeedUrl("pcast://example.com/rss"))
        assertEquals("https://example.com/rss", normalizeFeedUrl("podcast://example.com/rss"))
    }

    @Test
    fun stripsFeedPrefixOnFullUrl() {
        assertEquals("https://example.com/rss", normalizeFeedUrl("feed:https://example.com/rss"))
    }

    @Test
    fun keepsQueryStrings() {
        assertEquals(
            "https://www.patreon.com/rss/show?auth=abc123",
            normalizeFeedUrl("https://www.patreon.com/rss/show?auth=abc123"),
        )
    }

    @Test
    fun allowsLocalhost() {
        assertEquals("http://localhost:8080/feed.xml", normalizeFeedUrl("http://localhost:8080/feed.xml"))
    }

    @Test
    fun rejectsNonsense() {
        assertNull(normalizeFeedUrl(""))
        assertNull(normalizeFeedUrl("   "))
        assertNull(normalizeFeedUrl("not a url"))
        assertNull(normalizeFeedUrl("ftp://example.com/rss"))
        assertNull(normalizeFeedUrl("radiolab"))
    }

    @Test
    fun looksLikeUrl() {
        assertEquals(true, looksLikeFeedUrl("https://example.com/rss"))
        assertEquals(true, looksLikeFeedUrl("feed://example.com/rss"))
        assertEquals(true, looksLikeFeedUrl("feeds.megaphone.fm/abc"))
        assertEquals(false, looksLikeFeedUrl("radiolab"))
        assertEquals(false, looksLikeFeedUrl("the daily"))
        assertEquals(false, looksLikeFeedUrl("99% invisible"))
    }

    @Test
    fun otherSchemeSwapsHttpAndHttps() {
        assertEquals("http://example.com/feed?x=1", otherScheme("https://example.com/feed?x=1"))
        assertEquals("https://example.com/feed", otherScheme("http://example.com/feed"))
        assertEquals(null, otherScheme("ftp://example.com/feed"))
    }
}
