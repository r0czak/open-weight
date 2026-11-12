package com.openweight.app.home

import com.openweight.app.workout.blocks.ScheduledBlock
import com.openweight.app.workout.data.WorkoutSummary
import com.openweight.app.workout.session.DoneWorkout
import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class WeekPlanTest {
  private fun date(s: String) = LocalDate.parse(s)

  @Test
  fun weeksRunMondayToSunday() {
    assertEquals(date("2026-11-02"), WeekPlan.mondayOf(date("2026-11-02"))) // Monday itself
    assertEquals(date("2026-11-02"), WeekPlan.mondayOf(date("2026-11-08"))) // Sunday
    assertEquals(date("2026-12-28"), WeekPlan.mondayOf(date("2027-01-01"))) // across the new year
  }

  @Test
  fun daysCarryWorkoutsBlocksAndToday() {
    val intro =
      ScheduledBlock(
        "b1",
        "Intro",
        date("2026-11-03"),
        date("2026-11-30"),
        mapOf(DayOfWeek.TUESDAY to "pushA", DayOfWeek.WEDNESDAY to "pullA"),
      )
    val workouts =
      listOf(
        WorkoutSummary("pushA", "Push A", "Push", 7, 19, 1),
        WorkoutSummary("pullA", "Pull A", "Pull", 7, 17, 1),
      )

    val days = WeekPlan.days(date("2026-11-02"), today = date("2026-11-04"), listOf(intro), workouts)

    assertEquals((2..8).map { date("2026-11-0$it") }, days.map { it.date })
    assertEquals(listOf(null, "Push A", "Pull A", null, null, null, null), days.map { it.workout?.name })
    assertEquals(null, days[0].block) // Monday 2 Nov is before the block starts
    assertEquals("Intro", days[1].block?.blockName)
    assertEquals(listOf(false, false, true, false, false, false, false), days.map { it.isToday })
    assertEquals(7, days[1].workout?.exerciseCount)
  }

  @Test
  fun pastPlannedDays_areDoneOrMissed_otherWorkoutsAreListed() {
    val block = ScheduledBlock("b1", "Intro", date("2026-11-02"), date("2026-11-30"), DayOfWeek.entries.associateWith { "pushA" })
    val workouts = listOf(WorkoutSummary("pushA", "Push A", "Push", 7, 19, 1))
    val done =
      mapOf(
        date("2026-11-02") to listOf(DoneWorkout("s1", "pushA", "Push A")),
        date("2026-11-03") to listOf(DoneWorkout("s2", null, "Old arms day")),
      )

    val days = WeekPlan.days(date("2026-11-02"), today = date("2026-11-04"), listOf(block), workouts, done = done)

    assertEquals(listOf(true, false, false), days.take(3).map { it.workoutDone })
    // Tuesday's plan wasn't trained (something else was); today and later aren't missed yet.
    assertEquals(listOf(false, true, false, false), days.take(4).map { it.workoutMissed })
    assertEquals(listOf("Old arms day"), days[1].otherDone.map { it.workoutName })
  }
}

