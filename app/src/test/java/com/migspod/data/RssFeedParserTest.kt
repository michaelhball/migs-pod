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
    fun parseDuration_fractionalSeconds() {
        assertEquals(1_234_500L, RssFeedParser.parseDuration("1234.5"))
        assertEquals(3_723_500L, RssFeedParser.parseDuration("01:02:03.5"))
        assertEquals(62_250L, RssFeedParser.parseDuration("1:02.25"))
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

    // 2024-01-05T10:00:00Z
    private val jan5At10Utc = 1_704_448_800_000L

    @Test
    fun parseDate_rfc2822() {
        assertEquals(1_704_110_400_000L, RssFeedParser.parseDate("Mon, 01 Jan 2024 12:00:00 +0000"))
    }

    @Test
    fun parseDate_rfc2822WithTimezone() {
        assertEquals(1_678_869_000_000L, RssFeedParser.parseDate("Wed, 15 Mar 2023 08:30:00 GMT"))
    }

    @Test
    fun parseDate_singleDigitDay() {
        assertEquals(jan5At10Utc, RssFeedParser.parseDate("Fri, 5 Jan 2024 10:00:00 +0000"))
    }

    @Test
    fun parseDate_noWeekday() {
        assertEquals(jan5At10Utc, RssFeedParser.parseDate("05 Jan 2024 10:00:00 +0000"))
    }

    @Test
    fun parseDate_wrongWeekdayIsIgnored() {
        // Jan 5 2024 was a Friday; feeds get this wrong surprisingly often.
        assertEquals(jan5At10Utc, RssFeedParser.parseDate("Mon, 05 Jan 2024 10:00:00 +0000"))
    }

    @Test
    fun parseDate_noSeconds() {
        assertEquals(jan5At10Utc, RssFeedParser.parseDate("Fri, 05 Jan 2024 10:00 +0000"))
    }

    @Test
    fun parseDate_negativeOffset() {
        assertEquals(jan5At10Utc, RssFeedParser.parseDate("Fri, 05 Jan 2024 05:00:00 -0500"))
    }

    @Test
    fun parseDate_offsetWithColon() {
        assertEquals(jan5At10Utc, RssFeedParser.parseDate("Fri, 05 Jan 2024 11:00:00 +01:00"))
    }

    @Test
    fun parseDate_namedZones() {
        assertEquals(jan5At10Utc, RssFeedParser.parseDate("Fri, 05 Jan 2024 05:00:00 EST"))
        assertEquals(jan5At10Utc, RssFeedParser.parseDate("Fri, 05 Jan 2024 02:00:00 PST"))
        assertEquals(jan5At10Utc, RssFeedParser.parseDate("Fri, 05 Jan 2024 10:00:00 UT"))
        assertEquals(jan5At10Utc, RssFeedParser.parseDate("Fri, 05 Jan 2024 10:00:00 UTC"))
    }

    @Test
    fun parseDate_missingZoneAssumesUtc() {
        assertEquals(jan5At10Utc, RssFeedParser.parseDate("Fri, 05 Jan 2024 10:00:00"))
    }

    @Test
    fun parseDate_unknownZoneAssumesUtc() {
        assertEquals(jan5At10Utc, RssFeedParser.parseDate("Fri, 05 Jan 2024 10:00:00 XYZ"))
    }

    @Test
    fun parseDate_fullMonthNameAndCase() {
        assertEquals(jan5At10Utc, RssFeedParser.parseDate("Friday, 05 January 2024 10:00:00 GMT"))
        assertEquals(jan5At10Utc, RssFeedParser.parseDate("fri, 05 JAN 2024 10:00:00 gmt"))
    }

    @Test
    fun parseDate_twoDigitYear() {
        assertEquals(jan5At10Utc, RssFeedParser.parseDate("Fri, 05 Jan 24 10:00:00 +0000"))
    }

    @Test
    fun parseDate_extraWhitespace() {
        assertEquals(jan5At10Utc, RssFeedParser.parseDate("  Fri,  05 Jan 2024   10:00:00   +0000 "))
    }

    @Test
    fun parseDate_iso8601() {
        assertEquals(1_705_314_600_000L, RssFeedParser.parseDate("2024-01-15T10:30:00+0000"))
    }

    @Test
    fun parseDate_iso8601Variants() {
        assertEquals(jan5At10Utc, RssFeedParser.parseDate("2024-01-05T10:00:00Z"))
        assertEquals(jan5At10Utc, RssFeedParser.parseDate("2024-01-05T11:00:00+01:00"))
        assertEquals(jan5At10Utc, RssFeedParser.parseDate("2024-01-05T10:00:00.000Z"))
        assertEquals(jan5At10Utc, RssFeedParser.parseDate("2024-01-05T10:00:00"))
        assertEquals(jan5At10Utc, RssFeedParser.parseDate("2024-01-05 10:00:00"))
        assertEquals(1_704_412_800_000L, RssFeedParser.parseDate("2024-01-05"))
    }

    @Test
    fun parseDate_invalidDayOfMonth() {
        assertEquals(0L, RssFeedParser.parseDate("Fri, 32 Jan 2024 10:00:00 +0000"))
    }

    @Test
    fun parseDate_invalid() {
        assertEquals(0L, RssFeedParser.parseDate("not a date"))
    }

    @Test
    fun parseDate_empty() {
        assertEquals(0L, RssFeedParser.parseDate(""))
    }

    // --- HTML to text ---

    @Test
    fun htmlToText_basic() {
        assertEquals("Hello world", RssFeedParser.htmlToText("<p>Hello world</p>"))
    }

    @Test
    fun htmlToText_nested() {
        assertEquals(
            "Bold and italic",
            RssFeedParser.htmlToText("<p><b>Bold</b> and <i>italic</i></p>")
        )
    }

    @Test
    fun htmlToText_noTags() {
        assertEquals("Plain text", RssFeedParser.htmlToText("Plain text"))
    }

    @Test
    fun htmlToText_plainTextKeepsLineBreaks() {
        assertEquals("Line one\nLine two", RssFeedParser.htmlToText("Line one\nLine two"))
    }

    @Test
    fun htmlToText_empty() {
        assertEquals("", RssFeedParser.htmlToText(""))
    }

    @Test
    fun htmlToText_onlyTags() {
        assertEquals("", RssFeedParser.htmlToText("<br/><hr/>"))
    }

    @Test
    fun htmlToText_paragraphsDoNotRunTogether() {
        assertEquals("One.\n\nTwo.", RssFeedParser.htmlToText("<p>One.</p><p>Two.</p>"))
    }

    @Test
    fun htmlToText_lineBreaks() {
        assertEquals("One\nTwo", RssFeedParser.htmlToText("One<br>Two"))
        assertEquals("One\nTwo", RssFeedParser.htmlToText("One<BR />Two"))
    }

    @Test
    fun htmlToText_sourceNewlinesInsideHtmlAreSpaces() {
        assertEquals("Hello world", RssFeedParser.htmlToText("<p>Hello\n   world</p>"))
    }

    @Test
    fun htmlToText_listItems() {
        assertEquals(
            "Topics:\n\n• One\n• Two",
            RssFeedParser.htmlToText("<p>Topics:</p><ul><li>One</li><li>Two</li></ul>")
        )
    }

    @Test
    fun htmlToText_namedEntities() {
        assertEquals(
            "Tom & Jerry’s “show” — part 1…",
            RssFeedParser.htmlToText("<p>Tom &amp; Jerry&rsquo;s &ldquo;show&rdquo; &mdash; part&nbsp;1&hellip;</p>")
        )
    }

    @Test
    fun htmlToText_numericEntities() {
        assertEquals("It's ’ ’", RssFeedParser.htmlToText("It&#39;s &#8217; &#x2019;"))
    }

    @Test
    fun htmlToText_unknownEntityLeftAlone() {
        assertEquals("a &bogus; b", RssFeedParser.htmlToText("a &bogus; b"))
    }

    @Test
    fun htmlToText_escapedTagsStayLiteral() {
        assertEquals("Use <b> for bold", RssFeedParser.htmlToText("<p>Use &lt;b&gt; for bold</p>"))
    }

    @Test
    fun htmlToText_linkKeepsItsUrl() {
        assertEquals(
            "A link (https://example.com)",
            RssFeedParser.htmlToText("<a href=\"https://example.com\">A link</a>")
        )
    }

    @Test
    fun htmlToText_linkWhoseTextIsTheUrl() {
        assertEquals(
            "Visit https://megaphone.fm/adchoices",
            RssFeedParser.htmlToText("Visit <a href=\"https://megaphone.fm/adchoices\">megaphone.fm/adchoices</a>")
        )
    }

    @Test
    fun htmlToText_nonWebLinkDropsUrl() {
        assertEquals(
            "Email us",
            RssFeedParser.htmlToText("<a href=\"mailto:hi@example.com\">Email us</a>")
        )
    }
}
