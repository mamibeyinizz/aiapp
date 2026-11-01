package com.example.utils

import com.google.firebase.Timestamp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Utility for formatting shift dates, times, and computing duration
 * strictly using Firestore timestamps and respecting timezones.
 */
object ShiftDateTimeUtils {
    val TURKISH_LOCALE: Locale = Locale.forLanguageTag("tr-TR")
    val TURKEY_TIMEZONE: TimeZone = TimeZone.getTimeZone("Europe/Istanbul")

    // Maximum realistic single shift duration (30 hours) to prevent impossible values like 735 hours.
    const val MAX_VALID_SHIFT_DURATION_MS = 30 * 3600 * 1000L

    /**
     * Checks if two dates fall on the same calendar day in Europe/Istanbul timezone.
     */
    fun isSameCalendarDay(d1: Date, d2: Date, tz: TimeZone = TURKEY_TIMEZONE): Boolean {
        val cal1 = Calendar.getInstance(tz).apply { time = d1 }
        val cal2 = Calendar.getInstance(tz).apply { time = d2 }
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
    }

    /**
     * Checks if two dates fall in the same calendar year in Europe/Istanbul timezone.
     */
    fun isSameYear(d1: Date, d2: Date, tz: TimeZone = TURKEY_TIMEZONE): Boolean {
        val cal1 = Calendar.getInstance(tz).apply { time = d1 }
        val cal2 = Calendar.getInstance(tz).apply { time = d2 }
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR)
    }

    /**
     * Formats the header date for a shift card, e.g. "2 Eylül 2026, Çarşamba".
     * If timestamp is null, returns "Veri eksik".
     */
    fun formatShiftHeaderDate(start: Timestamp?): String {
        val startDate = start?.toDate() ?: return "Veri eksik"
        val sdf = SimpleDateFormat("d MMMM yyyy, EEEE", TURKISH_LOCALE).apply {
            timeZone = TURKEY_TIMEZONE
        }
        return sdf.format(startDate)
    }

    data class ShiftTimesDisplay(
        val girisStr: String,
        val cikisStr: String,
        val durationStr: String,
        val isValidDuration: Boolean
    )

    /**
     * Calculates and formats entry, exit, and total duration strictly from timestamps.
     *
     * Rules:
     * - Same day:
     *     Giriş: "2 Eylül 2026 · 05:25"
     *     Çıkış: "2 Eylül 2026 · 21:04"
     *     Süre:  "15 saat 39 dakika"
     * - Crossing midnight:
     *     Giriş: "2 Eylül 2026 · 23:00"
     *     Çıkış: "3 Eylül 2026 · 07:00"
     *     Süre:  "8 saat"
     * - Open shift:
     *     Çıkış: "Devam ediyor"
     *     Süre:  "Devam ediyor" (or live duration if currentMs provided)
     * - Missing exit timestamp on non-open shift:
     *     Çıkış: "Veri eksik"
     *     Süre:  "Veri eksik"
     * - Corrupt data (negative or impossible > 30 hours like 735 hours):
     *     Süre:  "Veri eksik"
     */
    fun formatShiftTimes(
        start: Timestamp?,
        end: Timestamp?,
        isOpen: Boolean = false,
        currentMs: Long? = null
    ): ShiftTimesDisplay {
        val sdfFull = SimpleDateFormat("d MMMM yyyy · HH:mm", TURKISH_LOCALE).apply {
            timeZone = TURKEY_TIMEZONE
        }

        if (start == null) {
            val endFormatted = if (end != null) {
                sdfFull.format(end.toDate())
            } else if (isOpen) {
                "Devam ediyor"
            } else {
                "Veri eksik"
            }
            return ShiftTimesDisplay(
                girisStr = "Veri eksik",
                cikisStr = endFormatted,
                durationStr = "Veri eksik",
                isValidDuration = false
            )
        }

        val startDate = start.toDate()
        val girisStr = sdfFull.format(startDate)

        if (end == null) {
            return if (isOpen) {
                val liveDuration = if (currentMs != null) {
                    formatLiveDuration(start, currentMs)
                } else {
                    "Devam ediyor"
                }
                ShiftTimesDisplay(
                    girisStr = girisStr,
                    cikisStr = "Devam ediyor",
                    durationStr = liveDuration,
                    isValidDuration = false
                )
            } else {
                // Missing exit timestamp on finished/closed shift
                ShiftTimesDisplay(
                    girisStr = girisStr,
                    cikisStr = "Veri eksik",
                    durationStr = "Veri eksik",
                    isValidDuration = false
                )
            }
        }

        val endDate = end.toDate()
        val cikisStr = sdfFull.format(endDate)
        val diffMs = endDate.time - startDate.time

        // Corrupt data: negative duration or unrealistic duration (> 30 hours, e.g. 735 hours)
        val isCorrupted = diffMs < 0 || diffMs > MAX_VALID_SHIFT_DURATION_MS
        val durationStr = if (isCorrupted) {
            "Veri eksik"
        } else {
            formatDurationDetailed(diffMs)
        }

        return ShiftTimesDisplay(
            girisStr = girisStr,
            cikisStr = cikisStr,
            durationStr = durationStr,
            isValidDuration = !isCorrupted
        )
    }

    /**
     * Formats duration into full Turkish words:
     * - "23 saniye"
     * - "59 saniye"
     * - "1 dakika 1 saniye"
     * - "2 dakika 37 saniye"
     * - "1 saat"
     * - "8 saat"
     * - "15 saat 39 dakika"
     * - "1 saat 12 dakika 37 saniye"
     * Never returns "0 dk" for valid timestamps.
     */
    fun formatDurationDetailed(diffMs: Long): String {
        if (diffMs < 0 || diffMs > MAX_VALID_SHIFT_DURATION_MS) {
            return "Veri eksik"
        }
        val totalSeconds = diffMs / 1000L
        val hours = totalSeconds / 3600L
        val minutes = (totalSeconds % 3600L) / 60L
        val seconds = totalSeconds % 60L

        return when {
            hours > 0 && minutes > 0 && seconds > 0 -> "$hours saat $minutes dakika $seconds saniye"
            hours > 0 && minutes > 0 -> "$hours saat $minutes dakika"
            hours > 0 && seconds > 0 -> "$hours saat $seconds saniye"
            hours > 0 -> "$hours saat"
            minutes > 0 && seconds > 0 -> "$minutes dakika $seconds saniye"
            minutes > 0 -> "$minutes dakika"
            else -> "$seconds saniye"
        }
    }

    /**
     * Formats live duration between timestamp and currentMs.
     */
    fun formatLiveDuration(start: Timestamp?, currentMs: Long = System.currentTimeMillis()): String {
        if (start == null) return "Veri eksik"
        val startMs = start.toDate().time
        val diffMs = currentMs - startMs
        if (diffMs < 0 || diffMs > MAX_VALID_SHIFT_DURATION_MS) {
            return "Veri eksik"
        }
        return formatDurationDetailed(diffMs)
    }

    /**
     * Formats live break status string:
     * - "Molada — 23 saniye"
     * - "Molada — 2 dakika 37 saniye"
     */
    fun formatLiveBreakStatus(molaBaslangic: Timestamp?, currentMs: Long = System.currentTimeMillis()): String {
        if (molaBaslangic == null) return "Molada"
        val duration = formatLiveDuration(molaBaslangic, currentMs)
        return if (duration == "Veri eksik") "Molada" else "Molada — $duration"
    }

    /**
     * Formats live shift status string:
     * - "Vardiya açık — 23 saniye"
     * - "Vardiya açık — 2 dakika 37 saniye"
     * - "Vardiya açık — 1 saat 12 dakika 37 saniye"
     */
    fun formatLiveShiftStatus(shiftStart: Timestamp?, currentMs: Long = System.currentTimeMillis()): String {
        if (shiftStart == null) return "Vardiya açık"
        val duration = formatLiveDuration(shiftStart, currentMs)
        return if (duration == "Veri eksik") "Vardiya açık" else "Vardiya açık — $duration"
    }

    /**
     * Formats break entry and exit times with date and duration:
     * - "2 Eylül · 09:48 → 2 Eylül · 09:48 (23 saniye)"
     * - "2 Eylül · 09:48 → 2 Eylül · 09:50 (2 dakika 37 saniye)"
     * - "2 Eylül · 09:48 → Devam ediyor (23 saniye)" (if currentMs passed)
     * - "2 Eylül · 09:48 → Devam ediyor" (if ongoing and no currentMs)
     * - "Veri eksik" (if corrupt/missing)
     */
    fun formatBreakDisplay(breakStart: Timestamp?, breakEnd: Timestamp?, currentMs: Long? = null): String {
        if (breakStart == null) return "Veri eksik"
        val startDate = breakStart.toDate()
        val sdfDate = SimpleDateFormat("d MMMM · HH:mm", TURKISH_LOCALE).apply {
            timeZone = TURKEY_TIMEZONE
        }
        val startStr = sdfDate.format(startDate)

        if (breakEnd == null) {
            return if (currentMs != null) {
                val liveDiffMs = (currentMs - startDate.time).coerceAtLeast(0L)
                if (liveDiffMs > 24 * 3600 * 1000L) {
                    "$startStr → Veri eksik"
                } else {
                    "$startStr → Devam ediyor (${formatDurationDetailed(liveDiffMs)})"
                }
            } else {
                "$startStr → Devam ediyor"
            }
        }

        val endDate = breakEnd.toDate()
        val diffMs = endDate.time - startDate.time

        if (diffMs < 0 || diffMs > 24 * 3600 * 1000L) {
            return "$startStr → Veri eksik"
        }

        val endStr = sdfDate.format(endDate)
        val durationStr = formatDurationDetailed(diffMs)
        return "$startStr → $endStr ($durationStr)"
    }

    /**
     * Backward-compatible delegation.
     */
    fun formatDurationMs(diffMs: Long): String = formatDurationDetailed(diffMs)

    /**
     * Backward-compatible delegation.
     */
    fun formatBreakDurationMs(diffMs: Long): String = formatDurationDetailed(diffMs)
}
