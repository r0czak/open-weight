package com.openweight.app.home

import com.openweight.app.workout.blocks.PlannedDay
import com.openweight.app.workout.blocks.ScheduleResolver
import com.openweight.app.workout.blocks.ScheduledBlock
import com.openweight.app.workout.data.WorkoutSummary
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/** A workout as the week view shows it. */
data class DayWorkout(val id: String, val name: String, val exerciseCount: Int)

/** One day of the Home week view. Meals join here once the meal calendar exists. */
data class WeekDay(
  val date: LocalDate,
  val isToday: Boolean,
  /** The block covering this day, with the week number in it; null outside every block. */
  val block: PlannedDay.InBlock?,
  /** Null on a rest day or outside every block. */
  val workout: DayWorkout?,
)

object WeekPlan {
  /** Weeks run Monday to Sunday. */
  fun mondayOf(date: LocalDate): LocalDate = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

  fun days(
    monday: LocalDate,
    today: LocalDate,
    blocks: List<ScheduledBlock>,
    workouts: List<WorkoutSummary>,
  ): List<WeekDay> {
    val byId = workouts.associateBy { it.id }
    return ScheduleResolver.plannedDays(blocks, monday, 7).map { planned ->
      val inBlock = planned as? PlannedDay.InBlock
      WeekDay(
        date = planned.date,
        isToday = planned.date == today,
        block = inBlock,
        workout =
          inBlock?.workoutId?.let { id -> byId[id]?.let { DayWorkout(it.id, it.name, it.exerciseCount) } },
      )
    }
  }
}
