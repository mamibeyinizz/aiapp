package com.example

import com.example.data.Shift
import com.example.data.ShiftBreak
import com.example.data.ShiftWithBreaks
import com.example.utils.ShiftDateTimeUtils
import com.google.firebase.Timestamp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class ShiftCalculationTest {

    private val turkeyTz: TimeZone = TimeZone.getTimeZone("Europe/Istanbul")

    private fun createTimestamp(
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int,
        second: Int = 0
    ): Timestamp {
        val cal = Calendar.getInstance(turkeyTz).apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month - 1)
            set(Calendar.DAY_OF_MONTH, day)
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, second)
            set(Calendar.MILLISECOND, 0)
        }
        return Timestamp(cal.time)
    }

    @Test
    fun test23SecondsBreak() {
        // 23 saniyelik mola
        val start = createTimestamp(2026, 9, 2, 9, 48, 0)
        val end = createTimestamp(2026, 9, 2, 9, 48, 23)

        val diffMs = end.toDate().time - start.toDate().time
        val durationStr = ShiftDateTimeUtils.formatDurationDetailed(diffMs)
        assertEquals("23 saniye", durationStr)

        val display = ShiftDateTimeUtils.formatBreakDisplay(start, end)
        assertEquals("2 Eylül · 09:48 → 2 Eylül · 09:48 (23 saniye)", display)
    }

    @Test
    fun test59SecondsBreak() {
        // 59 saniyelik mola
        val start = createTimestamp(2026, 9, 2, 9, 48, 0)
        val end = createTimestamp(2026, 9, 2, 9, 48, 59)

        val diffMs = end.toDate().time - start.toDate().time
        val durationStr = ShiftDateTimeUtils.formatDurationDetailed(diffMs)
        assertEquals("59 saniye", durationStr)

        val display = ShiftDateTimeUtils.formatBreakDisplay(start, end)
        assertEquals("2 Eylül · 09:48 → 2 Eylül · 09:48 (59 saniye)", display)
    }

    @Test
    fun test1Minute1SecondBreak() {
        // 1 dakika 1 saniyelik mola
        val start = createTimestamp(2026, 9, 2, 9, 48, 0)
        val end = createTimestamp(2026, 9, 2, 9, 49, 1)

        val diffMs = end.toDate().time - start.toDate().time
        val durationStr = ShiftDateTimeUtils.formatDurationDetailed(diffMs)
        assertEquals("1 dakika 1 saniye", durationStr)

        val display = ShiftDateTimeUtils.formatBreakDisplay(start, end)
        assertEquals("2 Eylül · 09:48 → 2 Eylül · 09:49 (1 dakika 1 saniye)", display)
    }

    @Test
    fun test2Minutes37SecondsBreak() {
        // 2 dakika 37 saniyelik mola
        val start = createTimestamp(2026, 9, 2, 9, 48, 0)
        val end = createTimestamp(2026, 9, 2, 9, 50, 37)

        val diffMs = end.toDate().time - start.toDate().time
        val durationStr = ShiftDateTimeUtils.formatDurationDetailed(diffMs)
        assertEquals("2 dakika 37 saniye", durationStr)

        val display = ShiftDateTimeUtils.formatBreakDisplay(start, end)
        assertEquals("2 Eylül · 09:48 → 2 Eylül · 09:50 (2 dakika 37 saniye)", display)
    }

    @Test
    fun test1HourShift() {
        // 1 saatlik vardiya
        val start = createTimestamp(2026, 9, 2, 8, 0, 0)
        val end = createTimestamp(2026, 9, 2, 9, 0, 0)

        val display = ShiftDateTimeUtils.formatShiftTimes(start, end, isOpen = false)
        assertEquals("2 Eylül 2026 · 08:00", display.girisStr)
        assertEquals("2 Eylül 2026 · 09:00", display.cikisStr)
        assertEquals("1 saat", display.durationStr)
        assertTrue(display.isValidDuration)
    }

    @Test
    fun test15HoursShift() {
        // 15 saatlik vardiya
        val start = createTimestamp(2026, 9, 2, 5, 0, 0)
        val end = createTimestamp(2026, 9, 2, 20, 0, 0)

        val display = ShiftDateTimeUtils.formatShiftTimes(start, end, isOpen = false)
        assertEquals("2 Eylül 2026 · 05:00", display.girisStr)
        assertEquals("2 Eylül 2026 · 20:00", display.cikisStr)
        assertEquals("15 saat", display.durationStr)
        assertTrue(display.isValidDuration)

        // 15 saat 39 dakika
        val endWithMins = createTimestamp(2026, 9, 2, 20, 39, 0)
        val displayWithMins = ShiftDateTimeUtils.formatShiftTimes(start, endWithMins, isOpen = false)
        assertEquals("15 saat 39 dakika", displayWithMins.durationStr)
    }

    @Test
    fun testNightShiftCrossingMidnight() {
        // Gece vardiyası: 2 Eylül 2026 23:00 -> 3 Eylül 2026 07:00 (8 saat)
        val start = createTimestamp(2026, 9, 2, 23, 0, 0)
        val end = createTimestamp(2026, 9, 3, 7, 0, 0)

        val display = ShiftDateTimeUtils.formatShiftTimes(start, end, isOpen = false)
        assertEquals("2 Eylül 2026 · 23:00", display.girisStr)
        assertEquals("3 Eylül 2026 · 07:00", display.cikisStr)
        assertEquals("8 saat", display.durationStr)
        assertTrue(display.isValidDuration)
    }

    @Test
    fun testOpenShiftLive() {
        // Açık vardiya
        val start = createTimestamp(2026, 9, 2, 5, 25, 0)
        val display = ShiftDateTimeUtils.formatShiftTimes(start, null, isOpen = true)

        assertEquals("2 Eylül 2026 · 05:25", display.girisStr)
        assertEquals("Devam ediyor", display.cikisStr)
        assertEquals("Devam ediyor", display.durationStr)
        assertFalse(display.isValidDuration)

        // Live calculation after 23 seconds
        val now23s = start.toDate().time + 23 * 1000L
        assertEquals("23 saniye", ShiftDateTimeUtils.formatLiveDuration(start, now23s))
        assertEquals("Vardiya açık — 23 saniye", ShiftDateTimeUtils.formatLiveShiftStatus(start, now23s))

        // Live calculation after 2 minutes 37 seconds
        val now2m37s = start.toDate().time + (2 * 60 + 37) * 1000L
        assertEquals("2 dakika 37 saniye", ShiftDateTimeUtils.formatLiveDuration(start, now2m37s))
        assertEquals("Vardiya açık — 2 dakika 37 saniye", ShiftDateTimeUtils.formatLiveShiftStatus(start, now2m37s))

        // Live calculation after 1 hour 12 minutes 37 seconds
        val now1h12m37s = start.toDate().time + (3600 + 12 * 60 + 37) * 1000L
        assertEquals("1 saat 12 dakika 37 saniye", ShiftDateTimeUtils.formatLiveDuration(start, now1h12m37s))
        assertEquals("Vardiya açık — 1 saat 12 dakika 37 saniye", ShiftDateTimeUtils.formatLiveShiftStatus(start, now1h12m37s))
    }

    @Test
    fun testOpenBreakLive() {
        // Açık mola
        val start = createTimestamp(2026, 9, 2, 9, 48, 0)

        // Live calculation after 23 seconds
        val now23s = start.toDate().time + 23 * 1000L
        assertEquals("Molada — 23 saniye", ShiftDateTimeUtils.formatLiveBreakStatus(start, now23s))
        assertEquals(
            "2 Eylül · 09:48 → Devam ediyor (23 saniye)",
            ShiftDateTimeUtils.formatBreakDisplay(start, null, now23s)
        )

        // Live calculation after 2 minutes 37 seconds
        val now2m37s = start.toDate().time + (2 * 60 + 37) * 1000L
        assertEquals("Molada — 2 dakika 37 saniye", ShiftDateTimeUtils.formatLiveBreakStatus(start, now2m37s))
        assertEquals(
            "2 Eylül · 09:48 → Devam ediyor (2 dakika 37 saniye)",
            ShiftDateTimeUtils.formatBreakDisplay(start, null, now2m37s)
        )

        // Without currentMs passed: static "Devam ediyor"
        assertEquals("2 Eylül · 09:48 → Devam ediyor", ShiftDateTimeUtils.formatBreakDisplay(start, null))
    }

    @Test
    fun testMissingEndAt() {
        // Eksik endAt (kapalı vardiyada çıkış timestamp'i eksik)
        val start = createTimestamp(2026, 9, 2, 5, 25, 0)
        val display = ShiftDateTimeUtils.formatShiftTimes(start, null, isOpen = false)

        assertEquals("2 Eylül 2026 · 05:25", display.girisStr)
        assertEquals("Veri eksik", display.cikisStr)
        assertEquals("Veri eksik", display.durationStr)
        assertFalse(display.isValidDuration)
    }

    @Test
    fun testMultiplePastShifts() {
        // Birden fazla geçmiş vardiya
        val s1Start = createTimestamp(2026, 9, 1, 8, 0, 0)
        val s1End = createTimestamp(2026, 9, 1, 16, 0, 0) // 8 saat

        val s2Start = createTimestamp(2026, 9, 2, 9, 0, 0)
        val s2End = createTimestamp(2026, 9, 2, 16, 0, 0) // 7 saat

        val shift1 = Shift(id = "s1", baslangic = s1Start, bitis = s1End, durum = "kapali")
        val shift2 = Shift(id = "s2", baslangic = s2Start, bitis = s2End, durum = "kapali")

        val shifts = listOf(shift1, shift2)
        val validShifts = shifts.filter {
            val start = it.baslangic?.toDate()?.time
            val end = it.bitis?.toDate()?.time
            start != null && end != null && end >= start && (end - start) <= ShiftDateTimeUtils.MAX_VALID_SHIFT_DURATION_MS
        }
        val totalGrossMs = validShifts.sumOf { it.bitis!!.toDate().time - it.baslangic!!.toDate().time }
        val totalStr = ShiftDateTimeUtils.formatDurationDetailed(totalGrossMs)
        assertEquals("15 saat", totalStr)
    }

    @Test
    fun testMultipleBreaksInOneShift() {
        // Bir vardiyada birden fazla mola
        val b1Start = createTimestamp(2026, 9, 2, 10, 0, 0)
        val b1End = createTimestamp(2026, 9, 2, 10, 0, 23) // 23 saniye

        val b2Start = createTimestamp(2026, 9, 2, 12, 0, 0)
        val b2End = createTimestamp(2026, 9, 2, 12, 2, 37) // 2 dakika 37 saniye

        val b3Start = createTimestamp(2026, 9, 2, 15, 0, 0)
        val b3End = createTimestamp(2026, 9, 2, 15, 15, 0) // 15 dakika

        val breaks = listOf(
            ShiftBreak(id = "b1", baslangic = b1Start, bitis = b1End),
            ShiftBreak(id = "b2", baslangic = b2Start, bitis = b2End),
            ShiftBreak(id = "b3", baslangic = b3Start, bitis = b3End)
        )

        val totalBreakMs = breaks.sumOf {
            val start = it.baslangic?.toDate()?.time
            val end = it.bitis?.toDate()?.time
            if (start != null && end != null && end >= start && (end - start) <= 24 * 3600 * 1000L) {
                end - start
            } else 0L
        }

        // 23s + 157s + 900s = 1080s = 18m
        val totalBreakStr = ShiftDateTimeUtils.formatDurationDetailed(totalBreakMs)
        assertEquals("18 dakika", totalBreakStr)
    }

    @Test
    fun testImpossibleDurationNeverShows700Hours() {
        // Abandoned shift ended 30 days later: 2 Eylül 05:25 -> 2 Ekim 21:04 (~735 hours)
        val start = createTimestamp(2026, 9, 2, 5, 25, 0)
        val end = createTimestamp(2026, 10, 2, 21, 4, 0)

        val display = ShiftDateTimeUtils.formatShiftTimes(start, end, isOpen = false)
        // Must show "Veri eksik" instead of fake 735 hours
        assertEquals("Veri eksik", display.durationStr)
        assertFalse(display.isValidDuration)

        val diffMs = end.toDate().time - start.toDate().time
        assertEquals("Veri eksik", ShiftDateTimeUtils.formatDurationDetailed(diffMs))
    }

    @Test
    fun testInvertedTimestamps() {
        val start = createTimestamp(2026, 9, 2, 21, 4, 0)
        val end = createTimestamp(2026, 9, 2, 5, 25, 0)

        val display = ShiftDateTimeUtils.formatShiftTimes(start, end, isOpen = false)
        assertEquals("Veri eksik", display.durationStr)
        assertFalse(display.isValidDuration)

        val diffMs = end.toDate().time - start.toDate().time
        assertEquals("Veri eksik", ShiftDateTimeUtils.formatDurationDetailed(diffMs))
    }

    @Test
    fun testMissingStartTimestamp() {
        val end = createTimestamp(2026, 9, 2, 21, 4, 0)

        val header = ShiftDateTimeUtils.formatShiftHeaderDate(null)
        assertEquals("Veri eksik", header)

        val display = ShiftDateTimeUtils.formatShiftTimes(null, end, isOpen = false)
        assertEquals("Veri eksik", display.girisStr)
        assertEquals("2 Eylül 2026 · 21:04", display.cikisStr)
        assertEquals("Veri eksik", display.durationStr)
        assertFalse(display.isValidDuration)
    }
}
