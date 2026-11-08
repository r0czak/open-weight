package com.openweight.app.core.database.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.Update
import com.openweight.app.core.database.entity.ExerciseEntity
import com.openweight.app.core.database.entity.SetPlanEntity
import com.openweight.app.core.database.entity.WorkoutEntity
import com.openweight.app.core.database.entity.WorkoutExerciseEntity
import java.time.Instant
import kotlinx.coroutines.flow.Flow

/** A workout row for the list: its size and how many training blocks use it. */
data class WorkoutSummaryRow(
  val id: String,
  val name: String,
  val type: String,
  val exerciseCount: Int,
  val setCount: Int,
  val blockCount: Int,
)

/** An exercise placed in a workout, with the library exercise and its planned sets. */
data class WorkoutEntryRow(
  @Embedded val entry: WorkoutExerciseEntity,
  @Relation(parentColumn = "exerciseId", entityColumn = "id") val exercise: ExerciseEntity,
  @Relation(parentColumn = "id", entityColumn = "workoutExerciseId") val sets: List<SetPlanEntity>,
)

@Dao
interface WorkoutDao {
  // Workouts

  @Query(
    """
    SELECT w.id, w.name, w.type,
      (SELECT COUNT(*) FROM workout_exercise we WHERE we.workoutId = w.id) AS exerciseCount,
      (SELECT COUNT(*) FROM set_plan s JOIN workout_exercise we ON s.workoutExerciseId = we.id
        WHERE we.workoutId = w.id) AS setCount,
      (SELECT COUNT(*) FROM block_workout bw WHERE bw.workoutId = w.id) AS blockCount
    FROM workout w
    -- Plan order: by the first block that uses the workout, then its place in that block's week;
    -- workouts in no block come last, by name.
    ORDER BY
      (SELECT MIN(b.startDate) FROM block_workout bw JOIN training_block b ON b.id = bw.blockId
        WHERE bw.workoutId = w.id) IS NULL,
      (SELECT MIN(b.startDate) FROM block_workout bw JOIN training_block b ON b.id = bw.blockId
        WHERE bw.workoutId = w.id),
      (SELECT bw.position FROM block_workout bw JOIN training_block b ON b.id = bw.blockId
        WHERE bw.workoutId = w.id ORDER BY b.startDate LIMIT 1),
      w.name COLLATE NOCASE
    """
  )
  fun observeSummaries(): Flow<List<WorkoutSummaryRow>>

  @Query("SELECT * FROM workout WHERE id = :id") fun observeWorkout(id: String): Flow<WorkoutEntity?>

  @Query("SELECT * FROM workout WHERE id = :id") suspend fun getWorkout(id: String): WorkoutEntity?

  @Query(
    """
    SELECT b.name FROM training_block b JOIN block_workout bw ON bw.blockId = b.id
    WHERE bw.workoutId = :workoutId ORDER BY b.startDate
    """
  )
  fun observeBlockNamesUsing(workoutId: String): Flow<List<String>>

  @Query("SELECT COUNT(*) FROM block_workout WHERE workoutId = :workoutId")
  suspend fun countBlocksUsing(workoutId: String): Int

  @Insert suspend fun insertWorkout(workout: WorkoutEntity)

  @Insert(onConflict = OnConflictStrategy.IGNORE)
  suspend fun insertWorkoutOrIgnore(workout: WorkoutEntity): Long

  @Update suspend fun updateWorkout(workout: WorkoutEntity)

  @Query("UPDATE workout SET updatedAt = :now WHERE id = :id")
  suspend fun touchWorkout(id: String, now: Instant)

  @Query("DELETE FROM workout WHERE id = :id") suspend fun deleteWorkout(id: String)

  // Exercises in a workout

  @Transaction
  @Query("SELECT * FROM workout_exercise WHERE workoutId = :workoutId ORDER BY position")
  fun observeEntries(workoutId: String): Flow<List<WorkoutEntryRow>>

  @Transaction
  @Query("SELECT * FROM workout_exercise WHERE workoutId = :workoutId ORDER BY position")
  suspend fun getEntries(workoutId: String): List<WorkoutEntryRow>

  @Query("SELECT * FROM workout_exercise WHERE id = :id")
  suspend fun getEntry(id: String): WorkoutExerciseEntity?

  @Query("SELECT COALESCE(MAX(position), -1) FROM workout_exercise WHERE workoutId = :workoutId")
  suspend fun maxEntryPosition(workoutId: String): Int

  @Insert suspend fun insertEntry(entry: WorkoutExerciseEntity)

  @Update suspend fun updateEntries(entries: List<WorkoutExerciseEntity>)

  @Query("DELETE FROM workout_exercise WHERE id = :id") suspend fun deleteEntry(id: String)

  // Sets

  @Query("SELECT * FROM set_plan WHERE workoutExerciseId = :entryId ORDER BY position")
  suspend fun getSets(entryId: String): List<SetPlanEntity>

  @Query("SELECT * FROM set_plan WHERE id = :id") suspend fun getSet(id: String): SetPlanEntity?

  @Insert suspend fun insertSets(sets: List<SetPlanEntity>)

  @Update suspend fun updateSets(sets: List<SetPlanEntity>)

  @Query("DELETE FROM set_plan WHERE id = :id") suspend fun deleteSet(id: String)
}
