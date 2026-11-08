package com.openweight.app.core.database

import androidx.room.TypeConverter
import java.time.Instant
import java.time.LocalDate

/** Dates are stored as ISO-8601 text (sortable, readable); instants as epoch milliseconds. */
class Converters {
  @TypeConverter fun localDateToString(value: LocalDate?): String? = value?.toString()

  @TypeConverter fun stringToLocalDate(value: String?): LocalDate? = value?.let(LocalDate::parse)

  @TypeConverter fun instantToLong(value: Instant?): Long? = value?.toEpochMilli()

  @TypeConverter fun longToInstant(value: Long?): Instant? = value?.let(Instant::ofEpochMilli)
}
