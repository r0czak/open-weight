package com.openweight.app.workout.data

data class Exercise(
  val id: String,
  val name: String,
  val muscleGroup: String,
  val equipment: String,
  val notes: String,
)

data class ExerciseListItem(
  val id: String,
  val name: String,
  val muscleGroup: String,
  val equipment: String,
  /** Number of workouts that use this exercise. */
  val usageCount: Int,
)

data class WorkoutSummary(
  val id: String,
  val name: String,
  val type: String,
  val exerciseCount: Int,
  val setCount: Int,
  val blockCount: Int,
)

data class WorkoutDetail(
  val id: String,
  val name: String,
  val type: String,
  val notes: String,
  val entries: List<WorkoutEntry>,
  /** Training blocks that use this workout; editing it changes it in all of them. */
  val blockNames: List<String>,
)

/** One exercise placed in a workout. */
data class WorkoutEntry(
  val id: String,
  val exerciseId: String,
  val exerciseName: String,
  val notes: String,
  val sets: List<PlannedSet>,
)

data class PlannedSet(
  val id: String,
  val repsMin: Int,
  val repsMax: Int?,
  val weightKg: Double?,
  val restSeconds: Int,
)

/** What a new or edited set looks like; also used to add several identical sets at once. */
data class SetValues(
  val repsMin: Int,
  val repsMax: Int? = null,
  val weightKg: Double? = null,
  val restSeconds: Int = DEFAULT_REST_SECONDS,
) {
  init {
    require(repsMin in 1..MAX_REPS) { "Reps must be between 1 and $MAX_REPS" }
    require(repsMax == null || repsMax in repsMin..MAX_REPS) { "Top of the rep range must be at least $repsMin" }
    require(weightKg == null || weightKg >= 0) { "Weight can't be negative" }
    require(restSeconds in 0..MAX_REST_SECONDS) { "Rest must be under ${MAX_REST_SECONDS / 60} minutes" }
  }

  companion object {
    const val DEFAULT_REST_SECONDS = 90
    const val MAX_REPS = 999
    const val MAX_REST_SECONDS = 60 * 60
    val DEFAULT = SetValues(repsMin = 8, repsMax = 12)
  }
}

/** Outcome of deleting something other records may depend on. */
sealed interface DeleteResult {
  data object Deleted : DeleteResult

  /** Not deleted: [count] other records (workouts or blocks) still use it. */
  data class InUse(val count: Int) : DeleteResult
}

class DuplicateNameException(val name: String) : IllegalArgumentException("\"$name\" already exists")
