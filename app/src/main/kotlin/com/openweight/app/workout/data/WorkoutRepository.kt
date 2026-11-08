package com.openweight.app.workout.data

import androidx.room.withTransaction
import com.openweight.app.core.TimeAndIds
import com.openweight.app.core.database.OpenWeightDatabase
import com.openweight.app.core.database.dao.WorkoutEntryRow
import com.openweight.app.core.database.entity.ExerciseEntity
import com.openweight.app.core.database.entity.SetPlanEntity
import com.openweight.app.core.database.entity.WorkoutEntity
import com.openweight.app.core.database.entity.WorkoutExerciseEntity
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/** Exercise library and workouts: every rule about editing them lives here, not in the UI. */
@Singleton
class WorkoutRepository
@Inject
constructor(private val db: OpenWeightDatabase, private val clock: TimeAndIds) {
  private val exerciseDao = db.exerciseDao()
  private val workoutDao = db.workoutDao()

  // ---- Exercise library ----

  fun exercises(): Flow<List<ExerciseListItem>> =
    exerciseDao.observeAllWithUsage().map { rows ->
      rows.map { ExerciseListItem(it.id, it.name, it.muscleGroup, it.equipment, it.usageCount) }
    }

  suspend fun getExercise(id: String): Exercise? =
    exerciseDao.getById(id)?.let { Exercise(it.id, it.name, it.muscleGroup, it.equipment, it.notes) }

  /**
   * Creates ([id] null) or updates an exercise and returns its id. Names are unique, ignoring
   * case, so imports can match exercises by name.
   */
  suspend fun saveExercise(
    id: String?,
    name: String,
    muscleGroup: String,
    equipment: String,
    notes: String,
  ): String {
    val cleanName = name.trim()
    require(cleanName.isNotEmpty()) { "Name can't be empty" }
    return db.withTransaction {
      val sameName = exerciseDao.findByName(cleanName)
      if (sameName != null && sameName.id != id) throw DuplicateNameException(cleanName)
      val now = clock.now()
      val existing = id?.let { exerciseDao.getById(it) }
      if (existing == null) {
        val newId = id ?: clock.newId()
        exerciseDao.insert(
          ExerciseEntity(newId, cleanName, muscleGroup.trim(), equipment.trim(), notes.trim(), now, now)
        )
        newId
      } else {
        exerciseDao.update(
          existing.copy(
            name = cleanName,
            muscleGroup = muscleGroup.trim(),
            equipment = equipment.trim(),
            notes = notes.trim(),
            updatedAt = now,
          )
        )
        existing.id
      }
    }
  }

  /** An exercise used in any workout is kept; the user removes it from those workouts first. */
  suspend fun deleteExercise(id: String): DeleteResult =
    db.withTransaction {
      val usage = exerciseDao.countWorkoutsUsing(id)
      if (usage > 0) {
        DeleteResult.InUse(usage)
      } else {
        exerciseDao.delete(id)
        DeleteResult.Deleted
      }
    }

  // ---- Workouts ----

  fun workouts(): Flow<List<WorkoutSummary>> =
    workoutDao.observeSummaries().map { rows ->
      rows.map {
        WorkoutSummary(it.id, it.name, it.type, it.exerciseCount, it.setCount, it.blockCount)
      }
    }

  fun workout(id: String): Flow<WorkoutDetail?> =
    combine(
      workoutDao.observeWorkout(id),
      workoutDao.observeEntries(id),
      workoutDao.observeBlockNamesUsing(id),
    ) { workout, entries, blocks ->
      workout?.let {
        WorkoutDetail(it.id, it.name, it.type, it.notes, entries.map(::toEntry), blocks)
      }
    }

  suspend fun createWorkout(name: String, type: String): String {
    val cleanName = name.trim()
    require(cleanName.isNotEmpty()) { "Name can't be empty" }
    val now = clock.now()
    val id = clock.newId()
    workoutDao.insertWorkout(WorkoutEntity(id, cleanName, type.trim(), "", now, now))
    return id
  }

  suspend fun updateWorkoutInfo(id: String, name: String, type: String, notes: String) {
    val cleanName = name.trim()
    require(cleanName.isNotEmpty()) { "Name can't be empty" }
    db.withTransaction {
      val existing = workoutDao.getWorkout(id) ?: return@withTransaction
      workoutDao.updateWorkout(
        existing.copy(name = cleanName, type = type.trim(), notes = notes, updatedAt = clock.now())
      )
    }
  }

  /** Copies a workout with all its exercises and sets; returns the new workout's id. */
  suspend fun duplicateWorkout(id: String): String =
    db.withTransaction {
      val source = requireNotNull(workoutDao.getWorkout(id)) { "Workout not found" }
      val now = clock.now()
      val newId = clock.newId()
      workoutDao.insertWorkout(
        source.copy(id = newId, name = "${source.name} (copy)", createdAt = now, updatedAt = now)
      )
      workoutDao.getEntries(id).forEach { row ->
        val entryId = clock.newId()
        workoutDao.insertEntry(
          row.entry.copy(id = entryId, workoutId = newId, createdAt = now, updatedAt = now)
        )
        workoutDao.insertSets(
          row.sets.map {
            it.copy(id = clock.newId(), workoutExerciseId = entryId, createdAt = now, updatedAt = now)
          }
        )
      }
      newId
    }

  /** A workout in a training block is kept; the user removes it from the block first. */
  suspend fun deleteWorkout(id: String): DeleteResult =
    db.withTransaction {
      val blocks = workoutDao.countBlocksUsing(id)
      if (blocks > 0) {
        DeleteResult.InUse(blocks)
      } else {
        workoutDao.deleteWorkout(id)
        DeleteResult.Deleted
      }
    }

  // ---- Exercises inside a workout ----

  /** Adds an exercise at the end of a workout with [setCount] identical sets. */
  suspend fun addExercise(
    workoutId: String,
    exerciseId: String,
    setCount: Int,
    values: SetValues,
  ): String {
    require(setCount in 1..MAX_SETS) { "Sets must be between 1 and $MAX_SETS" }
    return db.withTransaction {
      val now = clock.now()
      val entryId = clock.newId()
      workoutDao.insertEntry(
        WorkoutExerciseEntity(
          id = entryId,
          workoutId = workoutId,
          exerciseId = exerciseId,
          position = workoutDao.maxEntryPosition(workoutId) + 1,
          notes = "",
          createdAt = now,
          updatedAt = now,
        )
      )
      workoutDao.insertSets((0 until setCount).map { newSet(entryId, it, values) })
      workoutDao.touchWorkout(workoutId, now)
      entryId
    }
  }

  /** Moves an exercise up ([delta] < 0) or down within its workout. */
  suspend fun moveEntry(entryId: String, delta: Int) {
    db.withTransaction {
      val entry = workoutDao.getEntry(entryId) ?: return@withTransaction
      val ordered = workoutDao.getEntries(entry.workoutId).map { it.entry }.toMutableList()
      val from = ordered.indexOfFirst { it.id == entryId }
      val to = (from + delta).coerceIn(0, ordered.lastIndex)
      if (from == to) return@withTransaction
      ordered.add(to, ordered.removeAt(from))
      val now = clock.now()
      workoutDao.updateEntries(
        ordered.mapIndexedNotNull { index, e ->
          if (e.position == index) null else e.copy(position = index, updatedAt = now)
        }
      )
      workoutDao.touchWorkout(entry.workoutId, now)
    }
  }

  suspend fun removeEntry(entryId: String) {
    db.withTransaction {
      val entry = workoutDao.getEntry(entryId) ?: return@withTransaction
      workoutDao.deleteEntry(entryId)
      renumberEntries(entry.workoutId)
      workoutDao.touchWorkout(entry.workoutId, clock.now())
    }
  }

  suspend fun updateEntryNotes(entryId: String, notes: String) {
    db.withTransaction {
      val entry = workoutDao.getEntry(entryId) ?: return@withTransaction
      val now = clock.now()
      workoutDao.updateEntries(listOf(entry.copy(notes = notes.trim(), updatedAt = now)))
      workoutDao.touchWorkout(entry.workoutId, now)
    }
  }

  // ---- Sets ----

  /** Adds one set to an exercise, copying the last set (or a default when there is none). */
  suspend fun addSet(entryId: String) {
    db.withTransaction {
      val entry = workoutDao.getEntry(entryId) ?: return@withTransaction
      val sets = workoutDao.getSets(entryId)
      require(sets.size < MAX_SETS) { "At most $MAX_SETS sets per exercise" }
      val values =
        sets.lastOrNull()?.let { SetValues(it.repsMin, it.repsMax, it.weightKg, it.restSeconds) }
          ?: SetValues.DEFAULT
      workoutDao.insertSets(listOf(newSet(entryId, sets.size, values)))
      workoutDao.touchWorkout(entry.workoutId, clock.now())
    }
  }

  /** Changes one set, or every set of the same exercise when [applyToAll] is true. */
  suspend fun updateSet(setId: String, values: SetValues, applyToAll: Boolean) {
    db.withTransaction {
      val set = workoutDao.getSet(setId) ?: return@withTransaction
      val targets = if (applyToAll) workoutDao.getSets(set.workoutExerciseId) else listOf(set)
      val now = clock.now()
      workoutDao.updateSets(
        targets.map {
          it.copy(
            repsMin = values.repsMin,
            repsMax = values.repsMax,
            weightKg = values.weightKg,
            restSeconds = values.restSeconds,
            updatedAt = now,
          )
        }
      )
      workoutDao.getEntry(set.workoutExerciseId)?.let { workoutDao.touchWorkout(it.workoutId, now) }
    }
  }

  suspend fun deleteSet(setId: String) {
    db.withTransaction {
      val set = workoutDao.getSet(setId) ?: return@withTransaction
      workoutDao.deleteSet(setId)
      val now = clock.now()
      workoutDao.updateSets(
        workoutDao.getSets(set.workoutExerciseId).mapIndexedNotNull { index, s ->
          if (s.position == index) null else s.copy(position = index, updatedAt = now)
        }
      )
      workoutDao.getEntry(set.workoutExerciseId)?.let { workoutDao.touchWorkout(it.workoutId, now) }
    }
  }

  // ---- Helpers ----

  private suspend fun renumberEntries(workoutId: String) {
    val now = clock.now()
    workoutDao.updateEntries(
      workoutDao.getEntries(workoutId).mapIndexedNotNull { index, row ->
        if (row.entry.position == index) null else row.entry.copy(position = index, updatedAt = now)
      }
    )
  }

  private fun newSet(entryId: String, position: Int, values: SetValues): SetPlanEntity {
    val now = clock.now()
    return SetPlanEntity(
      id = clock.newId(),
      workoutExerciseId = entryId,
      position = position,
      repsMin = values.repsMin,
      repsMax = values.repsMax,
      weightKg = values.weightKg,
      restSeconds = values.restSeconds,
      createdAt = now,
      updatedAt = now,
    )
  }

  private fun toEntry(row: WorkoutEntryRow) =
    WorkoutEntry(
      id = row.entry.id,
      exerciseId = row.exercise.id,
      exerciseName = row.exercise.name,
      notes = row.entry.notes,
      sets =
        row.sets
          .sortedBy { it.position }
          .map { PlannedSet(it.id, it.repsMin, it.repsMax, it.weightKg, it.restSeconds) },
    )

  companion object {
    const val MAX_SETS = 20
  }
}
