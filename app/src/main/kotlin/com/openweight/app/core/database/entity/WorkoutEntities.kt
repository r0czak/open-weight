package com.openweight.app.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate

// Every record has a UUID text id, so exported files merge into another phone without clashes,
// plus createdAt/updatedAt so an import can keep "the newer one".

@Entity(tableName = "exercise", indices = [Index("name")])
data class ExerciseEntity(
  @PrimaryKey val id: String,
  val name: String,
  val muscleGroup: String = "",
  val equipment: String = "",
  val notes: String = "",
  val createdAt: Instant,
  val updatedAt: Instant,
)

@Entity(tableName = "workout", indices = [Index("name")])
data class WorkoutEntity(
  @PrimaryKey val id: String,
  val name: String,
  val type: String = "",
  val notes: String = "",
  val createdAt: Instant,
  val updatedAt: Instant,
)

/** An exercise placed in a workout. Deleting the workout removes it; an exercise in use can't be deleted. */
@Entity(
  tableName = "workout_exercise",
  foreignKeys =
    [
      ForeignKey(
        entity = WorkoutEntity::class,
        parentColumns = ["id"],
        childColumns = ["workoutId"],
        onDelete = ForeignKey.CASCADE,
      ),
      ForeignKey(
        entity = ExerciseEntity::class,
        parentColumns = ["id"],
        childColumns = ["exerciseId"],
        onDelete = ForeignKey.RESTRICT,
      ),
    ],
  indices = [Index("workoutId"), Index("exerciseId")],
)
data class WorkoutExerciseEntity(
  @PrimaryKey val id: String,
  val workoutId: String,
  val exerciseId: String,
  val position: Int,
  val notes: String = "",
  val createdAt: Instant,
  val updatedAt: Instant,
)

/**
 * One planned set: a rep target ([repsMin] alone, or a range up to [repsMax], e.g. 8–10), a weight
 * (null = not set yet, or bodyweight) and the rest after the set.
 */
@Entity(
  tableName = "set_plan",
  foreignKeys =
    [
      ForeignKey(
        entity = WorkoutExerciseEntity::class,
        parentColumns = ["id"],
        childColumns = ["workoutExerciseId"],
        onDelete = ForeignKey.CASCADE,
      )
    ],
  indices = [Index("workoutExerciseId")],
)
data class SetPlanEntity(
  @PrimaryKey val id: String,
  val workoutExerciseId: String,
  val position: Int,
  val repsMin: Int,
  val repsMax: Int? = null,
  val weightKg: Double?,
  val restSeconds: Int,
  val createdAt: Instant,
  val updatedAt: Instant,
)

@Entity(tableName = "training_block", indices = [Index("startDate")])
data class TrainingBlockEntity(
  @PrimaryKey val id: String,
  val name: String,
  val startDate: LocalDate,
  val endDate: LocalDate,
  val notes: String = "",
  val createdAt: Instant,
  val updatedAt: Instant,
)

/** A workout that belongs to a block (at most [MAX_WORKOUTS_PER_BLOCK] per block). */
@Entity(
  tableName = "block_workout",
  foreignKeys =
    [
      ForeignKey(
        entity = TrainingBlockEntity::class,
        parentColumns = ["id"],
        childColumns = ["blockId"],
        onDelete = ForeignKey.CASCADE,
      ),
      ForeignKey(
        entity = WorkoutEntity::class,
        parentColumns = ["id"],
        childColumns = ["workoutId"],
        onDelete = ForeignKey.RESTRICT,
      ),
    ],
  indices = [Index(value = ["blockId", "workoutId"], unique = true), Index("workoutId")],
)
data class BlockWorkoutEntity(
  @PrimaryKey val id: String,
  val blockId: String,
  val workoutId: String,
  val position: Int,
  val createdAt: Instant,
  val updatedAt: Instant,
)

/** What a block plans on one day of its repeating week. A null workout means rest. */
@Entity(
  tableName = "block_schedule_day",
  foreignKeys =
    [
      ForeignKey(
        entity = TrainingBlockEntity::class,
        parentColumns = ["id"],
        childColumns = ["blockId"],
        onDelete = ForeignKey.CASCADE,
      ),
      ForeignKey(
        entity = WorkoutEntity::class,
        parentColumns = ["id"],
        childColumns = ["workoutId"],
        onDelete = ForeignKey.SET_NULL,
      ),
    ],
  indices = [Index(value = ["blockId", "dayOfWeek"], unique = true), Index("workoutId")],
)
data class BlockScheduleDayEntity(
  @PrimaryKey val id: String,
  val blockId: String,
  @ColumnInfo(name = "dayOfWeek") val dayOfWeek: DayOfWeek,
  val workoutId: String?,
  val createdAt: Instant,
  val updatedAt: Instant,
)

const val MAX_WORKOUTS_PER_BLOCK = 10
