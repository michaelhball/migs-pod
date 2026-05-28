package com.migspod.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Test

class FormatTimeTest {

    @Test
    fun zero() {
        assertEquals("0:00", formatTime(0))
    }

    @Test
    fun negative() {
        assertEquals("0:00", formatTime(-1))
    }

    @Test
    fun secondsOnly() {
        assertEquals("0:09", formatTime(9_000))
    }

    @Test
    fun minutesAndSeconds() {
        assertEquals("5:30", formatTime(5 * 60_000 + 30_000))
    }

    @Test
    fun padSecondsBelowTen() {
        assertEquals("1:05", formatTime(65_000))
    }

    @Test
    fun hoursMinutesSeconds() {
        assertEquals("1:23:45", formatTime((1 * 3600 + 23 * 60 + 45) * 1000L))
    }

    @Test
    fun padMinutesInHourFormat() {
        assertEquals("2:05:09", formatTime((2 * 3600 + 5 * 60 + 9) * 1000L))
    }

    @Test
    fun subSecondTruncates() {
        assertEquals("0:01", formatTime(1999))
    }
}
