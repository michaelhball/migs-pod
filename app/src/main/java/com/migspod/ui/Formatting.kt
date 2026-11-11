package com.migspod.ui

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dayFormat = DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())
private val dayAndYearFormat = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault())
private val fullDateFormat = DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.getDefault())

private fun DateTimeFormatter.formatMs(ms: Long): String =
    format(Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()))

/** "Nov 10" */
internal fun formatDay(ms: Long): String = dayFormat.formatMs(ms)

/** "Nov 10, 2026" */
internal fun formatDayAndYear(ms: Long): String = dayAndYearFormat.formatMs(ms)

/** "November 10, 2026" */
internal fun formatFullDate(ms: Long): String = fullDateFormat.formatMs(ms)

/** "1h 3m", or "48m" under an hour */
internal fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}
