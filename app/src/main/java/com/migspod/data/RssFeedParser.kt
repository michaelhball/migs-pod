package com.migspod.data

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset

data class ParsedFeed(
    val title: String,
    val author: String,
    val description: String,
    val artworkUrl: String,
    val episodes: List<ParsedEpisode>,
)

data class ParsedEpisode(
    val guid: String,
    val title: String,
    val description: String,
    val audioUrl: String,
    val publishedDate: Long,
    val durationMs: Long,
)

object RssFeedParser {

    private val htmlTagRegex = Regex("<[^>]*>")
    private val lineBreakTagRegex = Regex("""(?i)<br\s*/?>""")
    private val blockEndTagRegex = Regex("""(?i)</(p|div|h[1-6]|blockquote|ul|ol|tr|table)\s*>""")
    private val listItemTagRegex = Regex("""(?i)<li(\s[^>]*)?>""")
    private val anchorRegex = Regex("""(?is)<a\s[^>]*?href\s*=\s*["']([^"']*)["'][^>]*>(.*?)</a\s*>""")
    private val entityRegex = Regex("""&(#[0-9]{1,7}|#[xX][0-9a-fA-F]{1,6}|[a-zA-Z][a-zA-Z0-9]{1,31});""")
    private val spaceRunRegex = Regex("""[ \t\u000B\f\u00A0]+""")
    private val spaceAroundNewlineRegex = Regex(""" *\n *""")
    private val extraNewlinesRegex = Regex("""\n{3,}""")

    private val namedEntities = mapOf(
        "amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'",
        "nbsp" to " ", "rsquo" to "’", "lsquo" to "‘", "rdquo" to "”", "ldquo" to "“",
        "sbquo" to "‚", "bdquo" to "„", "ndash" to "–", "mdash" to "—", "hellip" to "…",
        "bull" to "•", "middot" to "·", "laquo" to "«", "raquo" to "»", "copy" to "©",
        "reg" to "®", "trade" to "™", "deg" to "°", "euro" to "€", "pound" to "£",
        "cent" to "¢", "times" to "×", "frac12" to "½", "aacute" to "á", "agrave" to "à",
        "acirc" to "â", "auml" to "ä", "aring" to "å", "ccedil" to "ç", "eacute" to "é",
        "egrave" to "è", "ecirc" to "ê", "euml" to "ë", "iacute" to "í", "iuml" to "ï",
        "ntilde" to "ñ", "oacute" to "ó", "ocirc" to "ô", "ouml" to "ö", "oslash" to "ø",
        "uacute" to "ú", "ugrave" to "ù", "uuml" to "ü", "szlig" to "ß", "Eacute" to "É",
        "Auml" to "Ä", "Ouml" to "Ö", "Uuml" to "Ü",
    )

    // "05 Jan 2024 10:00[:00] <zone>" — the weekday prefix is ignored on purpose:
    // feeds frequently publish one that doesn't match the date.
    private val rfc822Regex = Regex(
        """(\d{1,2})\s+([A-Za-z]{3,})\.?,?\s+(\d{2}|\d{4})\s+(\d{1,2}):(\d{2})(?::(\d{2}))?(?:\.\d+)?\s*(.*)"""
    )
    private val numericOffsetRegex = Regex("""([+-])(\d{2}):?(\d{2})?""")
    private val isoOffsetWithoutColonRegex = Regex("""([+-]\d{2})(\d{2})$""")
    private val months = listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")
    private val zoneHours = mapOf(
        "GMT" to 0, "UT" to 0, "UTC" to 0, "Z" to 0,
        "EST" to -5, "EDT" to -4, "CST" to -6, "CDT" to -5,
        "MST" to -7, "MDT" to -6, "PST" to -8, "PDT" to -7,
        "BST" to 1, "CET" to 1, "CEST" to 2, "AEST" to 10, "AEDT" to 11,
    )

    fun parse(input: InputStream): ParsedFeed {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(input, null)
        parser.nextTag()

        var feedTitle = ""
        var feedAuthor = ""
        var feedDescription = ""
        var feedArtwork = ""
        val episodes = mutableListOf<ParsedEpisode>()
        var insideItem = false
        var insideChannel = false

        var itemGuid = ""
        var itemTitle = ""
        var itemDescription = ""
        var itemAudioUrl = ""
        var itemPubDate = 0L
        var itemDuration = 0L

        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> {
                    when (parser.name) {
                        "channel" -> insideChannel = true
                        "item" -> {
                            insideItem = true
                            itemGuid = ""
                            itemTitle = ""
                            itemDescription = ""
                            itemAudioUrl = ""
                            itemPubDate = 0L
                            itemDuration = 0L
                        }
                        "title" -> {
                            val text = parser.nextText() ?: ""
                            if (insideItem) itemTitle = text
                            else if (insideChannel && feedTitle.isEmpty()) feedTitle = text
                        }
                        "description" -> {
                            val text = parser.nextText() ?: ""
                            if (insideItem) itemDescription = htmlToText(text)
                            else if (insideChannel && feedDescription.isEmpty()) feedDescription = htmlToText(text)
                        }
                        "guid" -> {
                            if (insideItem) itemGuid = parser.nextText() ?: ""
                        }
                        "pubDate" -> {
                            if (insideItem) itemPubDate = parseDate(parser.nextText() ?: "")
                        }
                        "enclosure" -> {
                            if (insideItem) {
                                val type = parser.getAttributeValue(null, "type") ?: ""
                                if (type.startsWith("audio/")) {
                                    itemAudioUrl = parser.getAttributeValue(null, "url") ?: ""
                                }
                            }
                        }
                        "itunes:author" -> {
                            val text = parser.nextText() ?: ""
                            if (!insideItem && feedAuthor.isEmpty()) feedAuthor = text
                        }
                        "itunes:image" -> {
                            if (!insideItem && feedArtwork.isEmpty()) {
                                feedArtwork = parser.getAttributeValue(null, "href") ?: ""
                            }
                        }
                        "itunes:duration" -> {
                            if (insideItem) itemDuration = parseDuration(parser.nextText() ?: "")
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    when (parser.name) {
                        "item" -> {
                            if (itemAudioUrl.isNotEmpty()) {
                                if (itemGuid.isEmpty()) itemGuid = itemAudioUrl
                                episodes.add(
                                    ParsedEpisode(
                                        guid = itemGuid,
                                        title = itemTitle,
                                        description = itemDescription,
                                        audioUrl = itemAudioUrl,
                                        publishedDate = itemPubDate,
                                        durationMs = itemDuration,
                                    )
                                )
                            }
                            insideItem = false
                        }
                        "channel" -> insideChannel = false
                    }
                }
            }
        }

        return ParsedFeed(
            title = feedTitle,
            author = feedAuthor,
            description = feedDescription,
            artworkUrl = feedArtwork,
            episodes = episodes,
        )
    }

    internal fun parseDate(dateStr: String): Long {
        val trimmed = dateStr.trim()
        if (trimmed.isEmpty()) return 0L
        return try {
            if (trimmed.length >= 10 && trimmed[4] == '-' && trimmed[7] == '-') parseIsoDate(trimmed)
            else parseRfc822Date(trimmed)
        } catch (_: Exception) {
            0L
        }
    }

    private fun parseRfc822Date(dateStr: String): Long {
        val match = rfc822Regex.find(dateStr) ?: return 0L
        val (day, monthName, yearStr, hour, minute, second, zone) = match.destructured
        val month = months.indexOf(monthName.take(3).lowercase()) + 1
        if (month == 0) return 0L
        val year = yearStr.toInt().let { if (yearStr.length == 2) 2000 + it else it }
        val dateTime = LocalDateTime.of(
            year, month, day.toInt(), hour.toInt(), minute.toInt(), second.ifEmpty { "0" }.toInt()
        )
        return dateTime.toInstant(parseZone(zone)).toEpochMilli()
    }

    private fun parseZone(zone: String): ZoneOffset {
        numericOffsetRegex.find(zone)?.let { m ->
            val (sign, hours, minutes) = m.destructured
            val totalMinutes = hours.toInt() * 60 + minutes.ifEmpty { "0" }.toInt()
            return ZoneOffset.ofTotalSeconds((if (sign == "-") -totalMinutes else totalMinutes) * 60)
        }
        val hours = zoneHours[zone.trim().substringBefore(' ').uppercase()] ?: 0
        return ZoneOffset.ofHours(hours)
    }

    private fun parseIsoDate(dateStr: String): Long {
        val normalized = dateStr.replaceFirst(' ', 'T').replace(isoOffsetWithoutColonRegex, "$1:$2")
        if (normalized.length == 10) {
            return LocalDate.parse(normalized).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
        }
        return try {
            OffsetDateTime.parse(normalized).toInstant().toEpochMilli()
        } catch (_: Exception) {
            LocalDateTime.parse(normalized).toInstant(ZoneOffset.UTC).toEpochMilli()
        }
    }

    internal fun parseDuration(durationStr: String): Long {
        // Could be seconds like "1234" or HH:MM:SS / MM:SS
        val trimmed = durationStr.trim()
        if (trimmed.contains(":")) {
            val parts = trimmed.split(":")
            return when (parts.size) {
                3 -> {
                    val h = parts[0].toLongOrNull() ?: 0
                    val m = parts[1].toLongOrNull() ?: 0
                    val s = parts[2].toLongOrNull() ?: 0
                    (h * 3600 + m * 60 + s) * 1000
                }
                2 -> {
                    val m = parts[0].toLongOrNull() ?: 0
                    val s = parts[1].toLongOrNull() ?: 0
                    (m * 60 + s) * 1000
                }
                else -> 0L
            }
        }
        val seconds = trimmed.toLongOrNull() ?: 0L
        return seconds * 1000
    }

    /**
     * Show notes arrive as HTML. Convert to readable plain text: keep paragraph and
     * line breaks, keep link targets (so they can be tapped), decode entities.
     */
    internal fun htmlToText(html: String): String {
        if (!htmlTagRegex.containsMatchIn(html)) {
            return normalizeWhitespace(decodeEntities(html))
        }
        val text = html
            .replace('\n', ' ')
            .replace(anchorRegex) { m -> anchorText(m.groupValues[1].trim(), m.groupValues[2]) }
            .replace(lineBreakTagRegex, "\n")
            .replace(blockEndTagRegex, "\n\n")
            .replace(listItemTagRegex, "\n• ")
            .replace(htmlTagRegex, "")
        return normalizeWhitespace(decodeEntities(text))
    }

    private fun anchorText(href: String, innerHtml: String): String {
        val text = innerHtml.replace(htmlTagRegex, "").trim()
        if (!href.startsWith("http://", ignoreCase = true) && !href.startsWith("https://", ignoreCase = true)) {
            return text
        }
        val bareHref = href.substringAfter("://").removePrefix("www.").trimEnd('/')
        val bareText = text.substringAfter("://").removePrefix("www.").trimEnd('/')
        return when {
            text.isEmpty() || bareText.equals(bareHref, ignoreCase = true) -> href
            else -> "$text ($href)"
        }
    }

    private fun decodeEntities(text: String): String {
        if (!text.contains('&')) return text
        return text.replace(entityRegex) { m ->
            val name = m.groupValues[1]
            val codePoint = when {
                name.startsWith("#x") || name.startsWith("#X") -> name.substring(2).toIntOrNull(16)
                name.startsWith("#") -> name.substring(1).toIntOrNull()
                else -> return@replace namedEntities[name] ?: m.value
            }
            if (codePoint != null && Character.isValidCodePoint(codePoint)) {
                String(Character.toChars(codePoint))
            } else {
                m.value
            }
        }
    }

    private fun normalizeWhitespace(text: String): String = text
        .replace("\r\n", "\n")
        .replace('\r', '\n')
        .replace(spaceRunRegex, " ")
        .replace(spaceAroundNewlineRegex, "\n")
        .replace(extraNewlinesRegex, "\n\n")
        .trim()
}
