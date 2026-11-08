package com.openweight.app.transfer

import androidx.room.withTransaction
import com.openweight.app.core.TimeAndIds
import com.openweight.app.core.database.OpenWeightDatabase
import com.openweight.app.core.database.entity.BlockScheduleDayEntity
import com.openweight.app.core.database.entity.BlockWorkoutEntity
import com.openweight.app.core.database.entity.ExerciseEntity
import com.openweight.app.core.database.entity.MAX_WORKOUTS_PER_BLOCK
import com.openweight.app.core.database.entity.SetPlanEntity
import com.openweight.app.core.database.entity.TrainingBlockEntity
import com.openweight.app.core.database.entity.WorkoutEntity
import com.openweight.app.core.database.entity.WorkoutExerciseEntity
import com.openweight.app.workout.data.SetValues
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.SerializationException

/** Why a file can't be imported, in words the user can act on. Nothing was changed. */
class ImportException(message: String, cause: Throwable? = null) : Exception(message, cause)

data class ImportSummary(
  val exercisesAdded: Int = 0,
  /** Exercises in the file that matched one already in the app (same id or same name). */
  val exercisesMatched: Int = 0,
  val workoutsAdded: Int = 0,
  val workoutsSkipped: Int = 0,
  val blocksAdded: Int = 0,
  val blocksSkipped: Int = 0,
) {
  val nothingNew: Boolean
    get() = exercisesAdded + workoutsAdded + blocksAdded == 0
}

/**
 * Imports an Open Weight file. Version 1 applies the "skip" rule: a record whose id already exists
 * stays as it is, and an exercise whose name already exists is reused instead of duplicated. The
 * whole import runs in one transaction: it lands completely or not at all.
 */
@Singleton
class ImportService
@Inject
constructor(private val db: OpenWeightDatabase, private val clock: TimeAndIds) {

  fun parse(bytes: ByteArray): OpenWeightFile {
    if (bytes.size > MAX_FILE_BYTES) throw ImportException("The file is over 20 MB.")
    val file =
      try {
        TransferJson.decodeFromString(OpenWeightFile.serializer(), bytes.decodeToString())
      } catch (e: SerializationException) {
        throw ImportException("This isn't an Open Weight file, or it is damaged.", e)
      } catch (e: IllegalArgumentException) {
        throw ImportException("This isn't an Open Weight file, or it is damaged.", e)
      }
    if (file.format != OpenWeightFile.FORMAT) {
      throw ImportException("This isn't an Open Weight file.")
    }
    if (file.formatVersion > OpenWeightFile.CURRENT_VERSION) {
      throw ImportException("This file comes from a newer version of Open Weight. Update the app to import it.")
    }
    return file
  }

  suspend fun import(bytes: ByteArray): ImportSummary = import(parse(bytes))

  suspend fun import(file: OpenWeightFile): ImportSummary =
    db.withTransaction {
      validate(file.data)
      val now = clock.now()
      val exerciseDao = db.exerciseDao()
      val workoutDao = db.workoutDao()
      val blockDao = db.trainingBlockDao()

      // Exercises: same id or same name → reuse the existing one.
      val exerciseIds = mutableMapOf<String, String>()
      var exercisesAdded = 0
      var exercisesMatched = 0
      for (e in file.data.exercises) {
        val existing = exerciseDao.getById(e.id) ?: exerciseDao.findByName(e.name.trim())
        if (existing != null) {
          exerciseIds[e.id] = existing.id
          exercisesMatched++
        } else {
          exerciseDao.insert(
            ExerciseEntity(
              id = e.id,
              name = e.name.trim(),
              muscleGroup = e.muscleGroup,
              equipment = e.equipment,
              notes = e.notes,
              createdAt = instant(e.createdAt, now),
              updatedAt = instant(e.updatedAt, now),
            )
          )
          exerciseIds[e.id] = e.id
          exercisesAdded++
        }
      }

      // Workouts: an existing id is skipped with everything inside it.
      var workoutsAdded = 0
      var workoutsSkipped = 0
      for (w in file.data.workouts) {
        val inserted =
          workoutDao.insertWorkoutOrIgnore(
            WorkoutEntity(
              id = w.id,
              name = w.name.trim(),
              type = w.type,
              notes = w.notes,
              createdAt = instant(w.createdAt, now),
              updatedAt = instant(w.updatedAt, now),
            )
          ) != -1L
        if (!inserted) {
          workoutsSkipped++
          continue
        }
        workoutsAdded++
        w.exercises.forEachIndexed { position, item ->
          val entryId = clock.newId()
          workoutDao.insertEntry(
            WorkoutExerciseEntity(
              id = entryId,
              workoutId = w.id,
              exerciseId = exerciseIds[item.exerciseId] ?: item.exerciseId,
              position = position,
              notes = item.notes,
              createdAt = now,
              updatedAt = now,
            )
          )
          workoutDao.insertSets(
            item.sets.mapIndexed { setPosition, s ->
              SetPlanEntity(
                id = clock.newId(),
                workoutExerciseId = entryId,
                position = setPosition,
                repsMin = s.repsMin,
                repsMax = s.repsMax,
                weightKg = s.weightKg,
                restSeconds = s.restSeconds,
                createdAt = now,
                updatedAt = now,
              )
            }
          )
        }
      }

      // Training blocks, with their workouts and weekly schedule.
      var blocksAdded = 0
      var blocksSkipped = 0
      for (b in file.data.trainingBlocks) {
        val inserted =
          blockDao.insertBlockOrIgnore(
            TrainingBlockEntity(
              id = b.id,
              name = b.name.trim(),
              startDate = LocalDate.parse(b.startDate),
              endDate = LocalDate.parse(b.endDate),
              notes = b.notes,
              createdAt = instant(b.createdAt, now),
              updatedAt = instant(b.updatedAt, now),
            )
          ) != -1L
        if (!inserted) {
          blocksSkipped++
          continue
        }
        blocksAdded++
        blockDao.insertBlockWorkouts(
          b.workoutIds.mapIndexed { position, workoutId ->
            BlockWorkoutEntity(clock.newId(), b.id, workoutId, position, now, now)
          }
        )
        blockDao.insertScheduleDays(
          DayOfWeek.entries.map { day ->
            val workoutId = b.schedule.firstOrNull { it.dayOfWeek == day.name }?.workoutId
            BlockScheduleDayEntity(clock.newId(), b.id, day, workoutId, now, now)
          }
        )
      }

      ImportSummary(
        exercisesAdded,
        exercisesMatched,
        workoutsAdded,
        workoutsSkipped,
        blocksAdded,
        blocksSkipped,
      )
    }

  /** Checks the whole file before anything is written. */
  private suspend fun validate(data: TransferData) {
    val exerciseIds = data.exercises.map { it.id }.toSet()
    val workoutIds = data.workouts.map { it.id }.toSet()

    data.exercises.forEach { e ->
      if (e.name.isBlank()) fail("An exercise has no name.")
    }
    data.workouts.forEach { w ->
      if (w.name.isBlank()) fail("A workout has no name.")
      w.exercises.forEach { item ->
        if (item.exerciseId !in exerciseIds && db.exerciseDao().getById(item.exerciseId) == null) {
          fail("Workout \"${w.name}\" uses an exercise that isn't in the file.")
        }
        if (item.sets.isEmpty()) fail("Workout \"${w.name}\" has an exercise with no sets.")
        item.sets.forEach { s ->
          try {
            SetValues(s.repsMin, s.repsMax, s.weightKg, s.restSeconds)
          } catch (e: IllegalArgumentException) {
            fail("Workout \"${w.name}\" has an invalid set: ${e.message}.")
          }
        }
      }
    }
    data.trainingBlocks.forEach { b ->
      if (b.name.isBlank()) fail("A training block has no name.")
      val start = date(b.startDate, b.name)
      val end = date(b.endDate, b.name)
      if (end < start) fail("Block \"${b.name}\" ends before it starts.")
      if (b.workoutIds.size > MAX_WORKOUTS_PER_BLOCK) {
        fail("Block \"${b.name}\" has more than $MAX_WORKOUTS_PER_BLOCK workouts.")
      }
      if (b.workoutIds.toSet().size != b.workoutIds.size) {
        fail("Block \"${b.name}\" lists the same workout twice.")
      }
      b.workoutIds.forEach { id ->
        if (id !in workoutIds && db.workoutDao().getWorkout(id) == null) {
          fail("Block \"${b.name}\" uses a workout that isn't in the file.")
        }
      }
      val days = b.schedule.map { it.dayOfWeek }
      if (days.toSet().size != days.size) fail("Block \"${b.name}\" lists a day twice.")
      b.schedule.forEach { day ->
        if (DayOfWeek.entries.none { it.name == day.dayOfWeek }) {
          fail("Block \"${b.name}\" has an unknown day \"${day.dayOfWeek}\".")
        }
        if (day.workoutId != null && day.workoutId !in b.workoutIds) {
          fail("Block \"${b.name}\" schedules a workout that isn't part of the block.")
        }
      }
    }
  }

  private fun date(value: String, blockName: String): LocalDate =
    try {
      LocalDate.parse(value)
    } catch (e: RuntimeException) {
      fail("Block \"$blockName\" has an invalid date \"$value\".")
    }

  private fun instant(value: String?, fallback: Instant): Instant =
    value?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: fallback

  private fun fail(message: String): Nothing = throw ImportException(message)

  companion object {
    const val MAX_FILE_BYTES = 20 * 1024 * 1024
  }
}
