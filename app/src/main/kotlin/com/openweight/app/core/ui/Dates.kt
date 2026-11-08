package com.openweight.app.core.ui

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private fun pattern(p: String) = DateTimeFormatter.ofPattern(p, Locale.getDefault())

/** "Tue 10 Nov". */
fun LocalDate.formatDayShort(): String = format(pattern("EEE d MMM"))

/** "10 Nov 2026". */
fun LocalDate.formatMedium(): String = format(pattern("d MMM yyyy"))

/** "3 – 30 Nov 2026", "3 Nov – 1 Dec 2026", "1 Dec 2026 – 22 Feb 2027". */
fun formatDateRange(start: LocalDate, end: LocalDate): String =
  when {
    start.year != end.year -> "${start.formatMedium()} – ${end.formatMedium()}"
    start.month != end.month -> "${start.format(pattern("d MMM"))} – ${end.formatMedium()}"
    else -> "${start.dayOfMonth} – ${end.formatMedium()}"
  }

/** Date pickers work in UTC milliseconds at midnight. */
fun LocalDate.toPickerMillis(): Long = toEpochDay() * MILLIS_PER_DAY

fun pickerMillisToDate(millis: Long): LocalDate = LocalDate.ofEpochDay(Math.floorDiv(millis, MILLIS_PER_DAY))

private const val MILLIS_PER_DAY = 86_400_000L
