package com.openweight.app.core

import java.time.Instant
import java.util.UUID
import javax.inject.Inject

/** Source of "now" and of new record ids, swappable in tests for predictable values. */
interface TimeAndIds {
  fun now(): Instant

  fun newId(): String
}

class SystemTimeAndIds @Inject constructor() : TimeAndIds {
  override fun now(): Instant = Instant.now()

  override fun newId(): String = UUID.randomUUID().toString()
}
