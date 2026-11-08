package com.openweight.app.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.openweight.app.core.database.entity.BlockScheduleDayEntity
import com.openweight.app.core.database.entity.BlockWorkoutEntity
import com.openweight.app.core.database.entity.TrainingBlockEntity
import java.time.DayOfWeek
import java.time.Instant
import kotlinx.coroutines.flow.Flow

data class BlockSummaryRow(
  val id: String,
  val name: String,
  val startDate: java.time.LocalDate,
  val endDate: java.time.LocalDate,
  val workoutCount: Int,
)

/** A workout that belongs to a block, in the block's order. */
data class BlockWorkoutRow(
  val workoutId: String,
  val name: String,
  val type: String,
  val position: Int,
)

data class ScheduleDayRow(val blockId: String, val dayOfWeek: DayOfWeek, val workoutId: String?)

@Dao
interface TrainingBlockDao {
  @Query("SELECT COUNT(*) FROM training_block") fun observeCount(): Flow<Int>

  @Query(
    """
    SELECT b.id, b.name, b.startDate, b.endDate,
      (SELECT COUNT(*) FROM block_workout bw WHERE bw.blockId = b.id) AS workoutCount
    FROM training_block b
    ORDER BY b.startDate, b.name COLLATE NOCASE
    """
  )
  fun observeSummaries(): Flow<List<BlockSummaryRow>>

  @Query("SELECT * FROM training_block ORDER BY startDate")
  fun observeAllBlocks(): Flow<List<TrainingBlockEntity>>

  @Query("SELECT * FROM training_block WHERE id = :id")
  fun observeBlock(id: String): Flow<TrainingBlockEntity?>

  @Query("SELECT * FROM training_block WHERE id = :id")
  suspend fun getBlock(id: String): TrainingBlockEntity?

  @Query(
    """
    SELECT bw.workoutId, w.name, w.type, bw.position FROM block_workout bw
    JOIN workout w ON w.id = bw.workoutId
    WHERE bw.blockId = :blockId ORDER BY bw.position
    """
  )
  fun observeBlockWorkouts(blockId: String): Flow<List<BlockWorkoutRow>>

  @Query("SELECT * FROM block_workout WHERE blockId = :blockId ORDER BY position")
  suspend fun getBlockWorkouts(blockId: String): List<BlockWorkoutEntity>

  @Query("SELECT blockId, dayOfWeek, workoutId FROM block_schedule_day")
  fun observeAllScheduleDays(): Flow<List<ScheduleDayRow>>

  @Query("SELECT blockId, dayOfWeek, workoutId FROM block_schedule_day WHERE blockId = :blockId")
  fun observeScheduleDays(blockId: String): Flow<List<ScheduleDayRow>>

  @Insert(onConflict = OnConflictStrategy.IGNORE)
  suspend fun insertBlockOrIgnore(block: TrainingBlockEntity): Long

  @Insert suspend fun insertBlock(block: TrainingBlockEntity)

  @Update suspend fun updateBlock(block: TrainingBlockEntity)

  @Query("DELETE FROM training_block WHERE id = :id") suspend fun deleteBlock(id: String)

  @Insert suspend fun insertBlockWorkouts(rows: List<BlockWorkoutEntity>)

  @Update suspend fun updateBlockWorkouts(rows: List<BlockWorkoutEntity>)

  @Query("DELETE FROM block_workout WHERE blockId = :blockId AND workoutId = :workoutId")
  suspend fun deleteBlockWorkout(blockId: String, workoutId: String)

  @Insert suspend fun insertScheduleDays(rows: List<BlockScheduleDayEntity>)

  @Query(
    """
    UPDATE block_schedule_day SET workoutId = :workoutId, updatedAt = :now
    WHERE blockId = :blockId AND dayOfWeek = :day
    """
  )
  suspend fun updateScheduleDay(blockId: String, day: DayOfWeek, workoutId: String?, now: Instant): Int

  @Query(
    """
    UPDATE block_schedule_day SET workoutId = NULL, updatedAt = :now
    WHERE blockId = :blockId AND workoutId = :workoutId
    """
  )
  suspend fun clearScheduledWorkout(blockId: String, workoutId: String, now: Instant): Int

  @Query("UPDATE training_block SET updatedAt = :now WHERE id = :id")
  suspend fun touchBlock(id: String, now: Instant)
}
