package com.openweight.app.testing

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.openweight.app.core.TimeAndIds
import com.openweight.app.core.database.OpenWeightDatabase
import java.time.Instant
import java.time.LocalDate
import java.util.concurrent.Executors

/** In-memory database for repository tests. */
fun inMemoryDatabase(): OpenWeightDatabase =
  Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), OpenWeightDatabase::class.java)
    .setQueryExecutor(Executors.newSingleThreadExecutor())
    .allowMainThreadQueries()
    .build()

/** Predictable ids ("id-1", "id-2"…), a clock that ticks one second per call, and a fixed today. */
class FakeTimeAndIds(
  private var now: Instant = Instant.parse("2026-11-08T10:00:00Z"),
  var today: LocalDate = LocalDate.of(2026, 11, 8),
) : TimeAndIds {
  private var next = 0

  override fun now(): Instant = now.also { now = now.plusSeconds(1) }

  override fun today(): LocalDate = today

  override fun newId(): String = "id-${++next}"
}
