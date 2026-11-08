package com.openweight.app.workout.blocks

import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.SATURDAY
import java.time.DayOfWeek.TUESDAY
import java.time.DayOfWeek.WEDNESDAY
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScheduleResolverTest {
  private fun date(s: String) = LocalDate.parse(s)

  private val pushPull = mapOf(TUESDAY to "pushA", WEDNESDAY to "pullA", FRIDAY to "pushB", SATURDAY to "pullB")

  private val intro = ScheduledBlock("b1", "Intro", date("2026-11-03"), date("2026-11-30"), pushPull)
  private val core1 = ScheduledBlock("b2", "Core 1", date("2026-12-01"), date("2027-02-22"), pushPull)

  @Test
  fun weekdayPicksTheWorkout_otherDaysAreRest() {
    val tue = ScheduleResolver.plannedDay(listOf(intro, core1), date("2026-11-10")) as PlannedDay.InBlock
    assertEquals("pushA", tue.workoutId)
    assertEquals("Intro", tue.blockName)

    val thu = ScheduleResolver.plannedDay(listOf(intro, core1), date("2026-11-12")) as PlannedDay.InBlock
    assertNull(thu.workoutId)
  }

  @Test
  fun weeksCountFromTheBlockStart() {
    val first = ScheduleResolver.plannedDay(listOf(intro), date("2026-11-03")) as PlannedDay.InBlock
    val monday = ScheduleResolver.plannedDay(listOf(intro), date("2026-11-09")) as PlannedDay.InBlock
    val tuesday = ScheduleResolver.plannedDay(listOf(intro), date("2026-11-10")) as PlannedDay.InBlock
    val last = ScheduleResolver.plannedDay(listOf(intro), date("2026-11-30")) as PlannedDay.InBlock

    assertEquals(1, first.week)
    assertEquals(1, monday.week) // a week runs Tue–Mon when the block starts on a Tuesday
    assertEquals(2, tuesday.week)
    assertEquals(4, last.week)
    assertEquals(4, last.totalWeeks)
    assertEquals(12, core1.totalWeeks)
  }

  @Test
  fun datesOutsideEveryBlock_haveNoBlock() {
    assertEquals(PlannedDay.NoBlock(date("2026-11-02")), ScheduleResolver.plannedDay(listOf(intro), date("2026-11-02")))
    assertEquals(PlannedDay.NoBlock(date("2026-12-01")), ScheduleResolver.plannedDay(listOf(intro), date("2026-12-01")))
  }

  @Test
  fun overlappingBlocks_theLaterStartWins() {
    val deload = ScheduledBlock("d", "Deload", date("2026-11-23"), date("2026-11-29"), mapOf(TUESDAY to "easy"))

    val day = ScheduleResolver.plannedDay(listOf(intro, deload), date("2026-11-24")) as PlannedDay.InBlock
    assertEquals("Deload", day.blockName)
    assertEquals("easy", day.workoutId)
    assertEquals(listOf("Deload"), ScheduleResolver.overlapping(listOf(intro, deload), intro.startDate, intro.endDate, "b1").map { it.name })
    assertEquals(emptyList<ScheduledBlock>(), ScheduleResolver.overlapping(listOf(intro, core1), intro.startDate, intro.endDate, "b1"))
  }

  @Test
  fun plannedDays_crossIntoTheNextBlock() {
    val days = ScheduleResolver.plannedDays(listOf(intro, core1), date("2026-11-28"), 5)

    assertEquals(
      listOf("Intro:pullB", "Intro:null", "Intro:null", "Core 1:pushA", "Core 1:pullA"),
      days.map { d -> (d as PlannedDay.InBlock).let { "${it.blockName}:${it.workoutId}" } },
    )
  }
}
