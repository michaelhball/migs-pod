package com.migspod.data

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

private val appSchemes = listOf("feed://", "itpc://", "pcast://", "podcast://")

/**
 * Turns what someone pastes into a fetchable feed URL: trims it, maps podcast-app
 * schemes (feed://, itpc://…) to https, and assumes https when there's no scheme.
 * Returns null if it still isn't a valid http(s) URL with a dotted host.
 */
fun normalizeFeedUrl(input: String): String? {
    var url = input.trim()
    if (url.isEmpty() || url.any { it.isWhitespace() }) return null
    if (url.startsWith("feed:http", ignoreCase = true)) url = url.substring("feed:".length)
    appSchemes.firstOrNull { url.startsWith(it, ignoreCase = true) }?.let {
        url = "https://" + url.substring(it.length)
    }
    if (!url.contains("://")) url = "https://$url"
    val parsed = url.toHttpUrlOrNull() ?: return null
    if ('.' !in parsed.host && parsed.host != "localhost") return null
    return url
}

/** A feed's address without its scheme: the http and https forms are one feed. */
fun String.withoutScheme(): String = substringAfter("://")

/** [feedUrl] under the other of http and https, or null for any other scheme. */
fun otherScheme(feedUrl: String): String? = when {
    feedUrl.startsWith("https://", ignoreCase = true) -> "http://" + feedUrl.withoutScheme()
    feedUrl.startsWith("http://", ignoreCase = true) -> "https://" + feedUrl.withoutScheme()
    else -> null
}

/** Whether a search query is really a pasted feed URL rather than search terms. */
fun looksLikeFeedUrl(query: String): Boolean {
    val trimmed = query.trim()
    if (trimmed.contains("://")) return normalizeFeedUrl(trimmed) != null
    // Scheme-less: require a dotted host followed by a path, e.g. feeds.example.com/show
    return trimmed.contains('/') && normalizeFeedUrl(trimmed) != null
}
