package com.migspod.data

import org.junit.Assert.assertEquals
import org.junit.Test

class RssFeedParserTest {

    // --- Duration parsing ---

    @Test
    fun parseDuration_seconds() {
        assertEquals(1234_000L, RssFeedParser.parseDuration("1234"))
    }

    @Test
    fun parseDuration_minutesSeconds() {
        assertEquals((25 * 60 + 30) * 1000L, RssFeedParser.parseDuration("25:30"))
    }

    @Test
    fun parseDuration_hoursMinutesSeconds() {
        assertEquals((1 * 3600 + 15 * 60 + 45) * 1000L, RssFeedParser.parseDuration("1:15:45"))
    }

    @Test
    fun parseDuration_zero() {
        assertEquals(0L, RssFeedParser.parseDuration("0"))
    }

    @Test
    fun parseDuration_empty() {
        assertEquals(0L, RssFeedParser.parseDuration(""))
    }

    @Test
    fun parseDuration_whitespace() {
        assertEquals(60_000L, RssFeedParser.parseDuration("  60  "))
    }

    @Test
    fun parseDuration_invalidFormat() {
        assertEquals(0L, RssFeedParser.parseDuration("abc"))
    }

    @Test
    fun parseDuration_colonWithInvalidParts() {
        // "a:b:c" -> all parts toLongOrNull return null -> 0
        assertEquals(0L, RssFeedParser.parseDuration("a:b:c"))
    }

    // --- Date parsing ---

    @Test
    fun parseDate_rfc2822() {
        val result = RssFeedParser.parseDate("Mon, 01 Jan 2024 12:00:00 +0000")
        assert(result > 0) { "Expected a positive timestamp, got $result" }
    }

    @Test
    fun parseDate_rfc2822WithTimezone() {
        val result = RssFeedParser.parseDate("Wed, 15 Mar 2023 08:30:00 GMT")
        assert(result > 0) { "Expected a positive timestamp, got $result" }
    }

    @Test
    fun parseDate_iso8601() {
        val result = RssFeedParser.parseDate("2024-01-15T10:30:00+0000")
        assert(result > 0) { "Expected a positive timestamp, got $result" }
    }

    @Test
    fun parseDate_invalid() {
        assertEquals(0L, RssFeedParser.parseDate("not a date"))
    }

    @Test
    fun parseDate_empty() {
        assertEquals(0L, RssFeedParser.parseDate(""))
    }

    // --- HTML stripping ---

    @Test
    fun stripHtml_basic() {
        assertEquals("Hello world", RssFeedParser.stripHtml("<p>Hello world</p>"))
    }

    @Test
    fun stripHtml_nested() {
        assertEquals(
            "Bold and italic",
            RssFeedParser.stripHtml("<p><b>Bold</b> and <i>italic</i></p>")
        )
    }

    @Test
    fun stripHtml_noTags() {
        assertEquals("Plain text", RssFeedParser.stripHtml("Plain text"))
    }

    @Test
    fun stripHtml_empty() {
        assertEquals("", RssFeedParser.stripHtml(""))
    }

    @Test
    fun stripHtml_onlyTags() {
        assertEquals("", RssFeedParser.stripHtml("<br/><hr/>"))
    }

    @Test
    fun stripHtml_withAttributes() {
        assertEquals(
            "A link",
            RssFeedParser.stripHtml("<a href=\"https://example.com\">A link</a>")
        )
    }
}
