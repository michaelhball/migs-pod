package com.migspod.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class EpisodeAgeTest {

    private val zone = ZoneId.of("Europe/Berlin")
    private val now = ZonedDateTime.of(2026, 11, 8, 15, 0, 0, 0, zone)

    private fun ageOf(published: ZonedDateTime) =
        episodeAge(published.toInstant().toEpochMilli(), now.toInstant().toEpochMilli(), zone)

    @Test
    fun startOfTodayIsToday() {
        assertEquals(EpisodeAge.TODAY, ageOf(now.withHour(0).withMinute(0)))
    }

    @Test
    fun laterTodayIsToday() {
        assertEquals(EpisodeAge.TODAY, ageOf(now.plusHours(2)))
    }

    @Test
    fun lateYesterdayIsThisWeek() {
        assertEquals(EpisodeAge.THIS_WEEK, ageOf(now.withHour(23).minusDays(1)))
    }

    @Test
    fun sixDaysAgoIsThisWeek() {
        assertEquals(EpisodeAge.THIS_WEEK, ageOf(now.minusDays(6).withHour(0).withMinute(0)))
    }

    @Test
    fun sevenDaysAgoIsEarlier() {
        assertEquals(EpisodeAge.EARLIER, ageOf(now.minusDays(7).withHour(23)))
    }

    @Test
    fun missingDateIsEarlier() {
        assertEquals(EpisodeAge.EARLIER, episodeAge(0, now.toInstant().toEpochMilli(), zone))
    }
}
