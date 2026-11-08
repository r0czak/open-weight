package com.openweight.app.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.openweight.app.core.database.entity.ExerciseEntity
import kotlinx.coroutines.flow.Flow

/** An exercise plus how many workouts use it, for the library list. */
data class ExerciseWithUsage(
  val id: String,
  val name: String,
  val muscleGroup: String,
  val equipment: String,
  val usageCount: Int,
)

@Dao
interface ExerciseDao {
  @Query(
    """
    SELECT e.id, e.name, e.muscleGroup, e.equipment,
      (SELECT COUNT(DISTINCT we.workoutId) FROM workout_exercise we WHERE we.exerciseId = e.id)
        AS usageCount
    FROM exercise e
    ORDER BY e.name COLLATE NOCASE
    """
  )
  fun observeAllWithUsage(): Flow<List<ExerciseWithUsage>>

  @Query("SELECT * FROM exercise WHERE id = :id") suspend fun getById(id: String): ExerciseEntity?

  @Query("SELECT * FROM exercise WHERE name = :name COLLATE NOCASE LIMIT 1")
  suspend fun findByName(name: String): ExerciseEntity?

  @Query("SELECT COUNT(DISTINCT workoutId) FROM workout_exercise WHERE exerciseId = :id")
  suspend fun countWorkoutsUsing(id: String): Int

  @Insert suspend fun insert(exercise: ExerciseEntity)

  /** Returns -1 when a row with the same id already exists (the import's "skip" rule). */
  @Insert(onConflict = OnConflictStrategy.IGNORE)
  suspend fun insertOrIgnore(exercise: ExerciseEntity): Long

  @Update suspend fun update(exercise: ExerciseEntity)

  @Query("DELETE FROM exercise WHERE id = :id") suspend fun delete(id: String)
}
