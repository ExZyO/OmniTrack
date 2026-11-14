package com.omnitrack.app.util

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 1:1 Nuvio Mobile AirDateUtils Upstream.
 * Provides exact calendar day calculation, relative air date badge strings,
 * countdown formatting, and upcoming release status.
 */
object AirDateUtils {

    fun todayIsoDate(): String {
        return try {
            LocalDate.now().toString()
        } catch (_: Exception) {
            "2026-11-13"
        }
    }

    fun daysUntilExplicitRelease(
        todayIsoDate: String,
        releasedDate: String?
    ): Int? {
        val startDate = isoCalendarDateOrNull(todayIsoDate) ?: return null
        val targetDate = isoCalendarDateOrNull(releasedDate) ?: return null
        return (isoEpochDay(targetDate) - isoEpochDay(startDate)).toInt()
    }

    fun isoCalendarDateOrNull(value: String?): String? {
        val datePart = value
            ?.trim()
            ?.substringBefore('T')
            ?.takeIf { it.length >= 10 }
            ?.substring(0, 10)
            ?: return null
        val parts = datePart.split('-')
        if (parts.size != 3) return null
        val year = parts[0].toIntOrNull() ?: return null
        val month = parts[1].toIntOrNull()?.takeIf { it in 1..12 } ?: return null
        val day = parts[2].toIntOrNull()?.takeIf { it in 1..31 } ?: return null
        val normalizedYear = year.toString().padStart(4, '0')
        val normalizedMonth = month.toString().padStart(2, '0')
        val normalizedDay = day.toString().padStart(2, '0')
        return "$normalizedYear-$normalizedMonth-$normalizedDay"
    }

    fun isoEpochDay(date: String): Long {
        val year = date.substring(0, 4).toLong()
        val month = date.substring(5, 7).toLong()
        val day = date.substring(8, 10).toLong()

        val adjustedYear = year - if (month <= 2L) 1L else 0L
        val era = if (adjustedYear >= 0L) adjustedYear / 400L else (adjustedYear - 399L) / 400L
        val yearOfEra = adjustedYear - era * 400L
        val adjustedMonth = month + if (month > 2L) -3L else 9L
        val dayOfYear = (153L * adjustedMonth + 2L) / 5L + day - 1L
        val dayOfEra = yearOfEra * 365L + yearOfEra / 4L - yearOfEra / 100L + dayOfYear
        return era * 146_097L + dayOfEra - 719_468L
    }

    fun isUpcoming(releasedIso: String?, todayIso: String = todayIsoDate()): Boolean {
        val days = daysUntilExplicitRelease(todayIso, releasedIso) ?: return false
        return days >= 0
    }

    /**
     * Relative badge text matching Nuvio's exact cw_airs_today / cw_airs_in_days format.
     */
    fun computeAirDateBadgeText(
        releasedIso: String?,
        todayIso: String = todayIsoDate(),
        compact: Boolean = false
    ): String? {
        if (releasedIso.isNullOrBlank()) return null
        val daysUntil = daysUntilExplicitRelease(todayIso, releasedIso) ?: return null

        return when {
            daysUntil < 0 -> null
            daysUntil == 0 -> if (compact) "Today" else "Airs today"
            daysUntil == 1 -> if (compact) "Tomorrow" else "Airs tomorrow"
            daysUntil in 2..7 -> if (compact) "${daysUntil}d" else "Airs in $daysUntil days"
            else -> {
                val formatted = formatReleaseDateWithoutYear(releasedIso)
                if (compact) formatted else "Airs $formatted"
            }
        }
    }

    fun formatReleaseDateWithoutYear(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        return try {
            val iso = isoCalendarDateOrNull(raw) ?: return raw
            val parsed = LocalDate.parse(iso)
            val formatter = DateTimeFormatter.ofPattern("MMM d", Locale.US)
            formatter.format(parsed)
        } catch (_: Exception) {
            raw.take(10)
        }
    }

    fun formatAiringFullDate(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        return try {
            val iso = isoCalendarDateOrNull(raw) ?: return raw
            val parsed = LocalDate.parse(iso)
            val formatter = DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy", Locale.US)
            formatter.format(parsed)
        } catch (_: Exception) {
            raw
        }
    }

    fun formatCountdown(seconds: Long): String {
        if (seconds <= 0) return "Airing now"
        val days = seconds / 86400L
        val hours = (seconds % 86400L) / 3600L
        val minutes = (seconds % 3600L) / 60L

        return when {
            days > 0 -> "${days}d ${hours}h"
            hours > 0 -> "${hours}h ${minutes}m"
            else -> "${minutes}m ${seconds % 60}s"
        }
    }
}
