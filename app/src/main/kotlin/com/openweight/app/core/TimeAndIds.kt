package com.openweight.app.core

import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject

/** Source of "now", "today" and new record ids, swappable in tests for predictable values. */
interface TimeAndIds {
  fun now(): Instant

  /** Today in the phone's time zone. */
  fun today(): LocalDate

  fun newId(): String
}

class SystemTimeAndIds @Inject constructor() : TimeAndIds {
  override fun now(): Instant = Instant.now()

  override fun today(): LocalDate = LocalDate.now()

  override fun newId(): String = UUID.randomUUID().toString()
}
