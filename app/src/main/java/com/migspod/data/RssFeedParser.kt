package com.migspod.data

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Locale

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

    private val dateFormatPatterns = listOf(
        "EEE, dd MMM yyyy HH:mm:ss Z",
        "EEE, dd MMM yyyy HH:mm:ss zzz",
        "yyyy-MM-dd'T'HH:mm:ssZ",
        "yyyy-MM-dd'T'HH:mm:ss.SSSZ",
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
                            if (insideItem) itemDescription = stripHtml(text)
                            else if (insideChannel && feedDescription.isEmpty()) feedDescription = stripHtml(text)
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
        for (pattern in dateFormatPatterns) {
            try {
                return SimpleDateFormat(pattern, Locale.US).parse(dateStr)?.time ?: 0L
            } catch (_: Exception) { }
        }
        return 0L
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

    internal fun stripHtml(html: String): String {
        return html.replace(htmlTagRegex, "").trim()
    }
}
