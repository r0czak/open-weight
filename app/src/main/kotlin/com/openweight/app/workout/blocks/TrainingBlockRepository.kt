package com.openweight.app.workout.blocks

import androidx.room.withTransaction
import com.openweight.app.core.TimeAndIds
import com.openweight.app.core.database.OpenWeightDatabase
import com.openweight.app.core.database.entity.BlockScheduleDayEntity
import com.openweight.app.core.database.entity.BlockWorkoutEntity
import com.openweight.app.core.database.entity.MAX_WORKOUTS_PER_BLOCK
import com.openweight.app.core.database.entity.TrainingBlockEntity
import java.time.DayOfWeek
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

data class BlockSummary(
  val id: String,
  val name: String,
  val startDate: LocalDate,
  val endDate: LocalDate,
  val workoutCount: Int,
)

data class BlockWorkout(val id: String, val name: String, val type: String)

data class BlockDetail(
  val id: String,
  val name: String,
  val startDate: LocalDate,
  val endDate: LocalDate,
  val notes: String,
  val workouts: List<BlockWorkout>,
  /** Every weekday, Monday first; null = rest. */
  val schedule: Map<DayOfWeek, String?>,
  /** Names of other blocks whose dates overlap this one. */
  val overlapsWith: List<String>,
) {
  val totalWeeks: Int
    get() = ScheduledBlock(id, name, startDate, endDate, schedule).totalWeeks
}

class TooManyWorkoutsException : IllegalStateException("At most $MAX_WORKOUTS_PER_BLOCK workouts per block")

/** Training blocks: dates, the workouts in them (at most 10) and their weekly schedule. */
@Singleton
class TrainingBlockRepository
@Inject
constructor(private val db: OpenWeightDatabase, private val clock: TimeAndIds) {
  private val dao = db.trainingBlockDao()

  fun blocks(): Flow<List<BlockSummary>> =
    dao.observeSummaries().map { rows ->
      rows.map { BlockSummary(it.id, it.name, it.startDate, it.endDate, it.workoutCount) }
    }

  /** Every block with its weekly schedule, as the schedule rules need them. */
  fun scheduledBlocks(): Flow<List<ScheduledBlock>> =
    combine(dao.observeAllBlocks(), dao.observeAllScheduleDays()) { blocks, days ->
      val byBlock = days.groupBy { it.blockId }
      blocks.map { b ->
        ScheduledBlock(
          id = b.id,
          name = b.name,
          startDate = b.startDate,
          endDate = b.endDate,
          days = byBlock[b.id].orEmpty().associate { it.dayOfWeek to it.workoutId },
        )
      }
    }

  fun block(id: String): Flow<BlockDetail?> =
    combine(
      dao.observeBlock(id),
      dao.observeBlockWorkouts(id),
      dao.observeScheduleDays(id),
      scheduledBlocks(),
    ) { block, workouts, days, all ->
      block?.let { b ->
        val byDay = days.associate { it.dayOfWeek to it.workoutId }
        BlockDetail(
          id = b.id,
          name = b.name,
          startDate = b.startDate,
          endDate = b.endDate,
          notes = b.notes,
          workouts = workouts.map { BlockWorkout(it.workoutId, it.name, it.type) },
          schedule = DayOfWeek.entries.associateWith { byDay[it] },
          overlapsWith = ScheduleResolver.overlapping(all, b.startDate, b.endDate, b.id).map { it.name },
        )
      }
    }

  /** Creates a block with an all-rest week and returns its id. */
  suspend fun createBlock(name: String, startDate: LocalDate, endDate: LocalDate): String {
    val cleanName = validate(name, startDate, endDate)
    return db.withTransaction {
      val now = clock.now()
      val id = clock.newId()
      dao.insertBlock(TrainingBlockEntity(id, cleanName, startDate, endDate, "", now, now))
      dao.insertScheduleDays(
        DayOfWeek.entries.map { BlockScheduleDayEntity(clock.newId(), id, it, null, now, now) }
      )
      id
    }
  }

  suspend fun updateBlockInfo(
    id: String,
    name: String,
    startDate: LocalDate,
    endDate: LocalDate,
    notes: String,
  ) {
    val cleanName = validate(name, startDate, endDate)
    db.withTransaction {
      val b = dao.getBlock(id) ?: return@withTransaction
      dao.updateBlock(
        b.copy(
          name = cleanName,
          startDate = startDate,
          endDate = endDate,
          notes = notes,
          updatedAt = clock.now(),
        )
      )
    }
  }

  /** Names of other blocks that would overlap these dates (the later-starting one wins). */
  suspend fun overlapsFor(id: String?, startDate: LocalDate, endDate: LocalDate): List<String> {
    return dao.observeAllBlocks().first()
      .filter { it.id != id && !it.startDate.isAfter(endDate) && !it.endDate.isBefore(startDate) }
      .map { it.name }
  }

  /**
   * Adds workouts to a block, in the given order, skipping ones already in it. Refused (nothing
   * added) when the block would hold more than [MAX_WORKOUTS_PER_BLOCK].
   */
  suspend fun addWorkouts(blockId: String, workoutIds: List<String>) {
    db.withTransaction {
      val existing = dao.getBlockWorkouts(blockId)
      val toAdd = workoutIds.distinct().filter { id -> existing.none { it.workoutId == id } }
      if (existing.size + toAdd.size > MAX_WORKOUTS_PER_BLOCK) throw TooManyWorkoutsException()
      val now = clock.now()
      val start = (existing.maxOfOrNull { it.position } ?: -1) + 1
      dao.insertBlockWorkouts(
        toAdd.mapIndexed { i, workoutId ->
          BlockWorkoutEntity(clock.newId(), blockId, workoutId, start + i, now, now)
        }
      )
      dao.touchBlock(blockId, now)
    }
  }

  /** Removes a workout from a block; the days it was scheduled on become rest days. */
  suspend fun removeWorkout(blockId: String, workoutId: String) {
    db.withTransaction {
      val now = clock.now()
      dao.clearScheduledWorkout(blockId, workoutId, now)
      dao.deleteBlockWorkout(blockId, workoutId)
      dao.updateBlockWorkouts(
        dao.getBlockWorkouts(blockId).mapIndexedNotNull { index, row ->
          if (row.position == index) null else row.copy(position = index, updatedAt = now)
        }
      )
      dao.touchBlock(blockId, now)
    }
  }

  /** How many days of the week a workout is scheduled on in a block (to warn before removing). */
  suspend fun scheduledDayCount(blockId: String, workoutId: String): Int =
    dao.observeScheduleDays(blockId).first().count { it.workoutId == workoutId }

  /** Sets what a weekday holds: a workout that is part of the block, or rest (null). */
  suspend fun setDay(blockId: String, day: DayOfWeek, workoutId: String?) {
    db.withTransaction {
      if (workoutId != null) {
        require(dao.getBlockWorkouts(blockId).any { it.workoutId == workoutId }) {
          "Only workouts in the block can be scheduled"
        }
      }
      val now = clock.now()
      if (dao.updateScheduleDay(blockId, day, workoutId, now) == 0) {
        dao.insertScheduleDays(listOf(BlockScheduleDayEntity(clock.newId(), blockId, day, workoutId, now, now)))
      }
      dao.touchBlock(blockId, now)
    }
  }

  /** Deletes a block and its schedule. The workouts stay in the library. */
  suspend fun deleteBlock(id: String) = dao.deleteBlock(id)

  private fun validate(name: String, startDate: LocalDate, endDate: LocalDate): String {
    val cleanName = name.trim()
    require(cleanName.isNotEmpty()) { "Name can't be empty" }
    require(!endDate.isBefore(startDate)) { "The block must end on or after its start date" }
    return cleanName
  }
}
