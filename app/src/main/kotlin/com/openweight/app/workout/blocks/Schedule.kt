package com.openweight.app.workout.blocks

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** A training block as the schedule rules need it: dates and what each weekday holds. */
data class ScheduledBlock(
  val id: String,
  val name: String,
  val startDate: LocalDate,
  val endDate: LocalDate,
  /** Workout id per weekday; a missing day or null means rest. */
  val days: Map<DayOfWeek, String?>,
) {
  fun contains(date: LocalDate): Boolean = !date.isBefore(startDate) && !date.isAfter(endDate)

  /** Week 1 starts on [startDate]. */
  fun weekNumber(date: LocalDate): Int = (ChronoUnit.DAYS.between(startDate, date) / 7).toInt() + 1

  val totalWeeks: Int
    get() = ((ChronoUnit.DAYS.between(startDate, endDate) + 1 + 6) / 7).toInt()
}

/** What is planned on one date. */
sealed interface PlannedDay {
  val date: LocalDate

  /** No training block covers this date. */
  data class NoBlock(override val date: LocalDate) : PlannedDay

  data class InBlock(
    override val date: LocalDate,
    val blockId: String,
    val blockName: String,
    val week: Int,
    val totalWeeks: Int,
    /** Null on a rest day. */
    val workoutId: String?,
  ) : PlannedDay
}

/**
 * The schedule rule: the block whose dates contain the date is used, and the date's weekday picks
 * the workout (or rest). When blocks overlap, the one that started later wins.
 */
object ScheduleResolver {
  fun activeBlock(blocks: List<ScheduledBlock>, date: LocalDate): ScheduledBlock? =
    blocks.filter { it.contains(date) }.maxWithOrNull(compareBy({ it.startDate }, { it.id }))

  fun plannedDay(blocks: List<ScheduledBlock>, date: LocalDate): PlannedDay {
    val block = activeBlock(blocks, date) ?: return PlannedDay.NoBlock(date)
    return PlannedDay.InBlock(
      date = date,
      blockId = block.id,
      blockName = block.name,
      week = block.weekNumber(date),
      totalWeeks = block.totalWeeks,
      workoutId = block.days[date.dayOfWeek],
    )
  }

  fun plannedDays(blocks: List<ScheduledBlock>, from: LocalDate, count: Int): List<PlannedDay> =
    (0 until count).map { plannedDay(blocks, from.plusDays(it.toLong())) }

  /** Other blocks whose dates overlap [start]–[end]. */
  fun overlapping(
    blocks: List<ScheduledBlock>,
    start: LocalDate,
    end: LocalDate,
    exceptId: String? = null,
  ): List<ScheduledBlock> =
    blocks.filter { it.id != exceptId && !it.startDate.isAfter(end) && !it.endDate.isBefore(start) }
}
