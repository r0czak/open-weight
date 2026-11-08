package com.openweight.app.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.openweight.app.core.database.entity.BlockScheduleDayEntity
import com.openweight.app.core.database.entity.BlockWorkoutEntity
import com.openweight.app.core.database.entity.TrainingBlockEntity
import kotlinx.coroutines.flow.Flow

/** Training blocks. M1 only needs what import uses; the block editor (M2) extends this. */
@Dao
interface TrainingBlockDao {
  @Query("SELECT COUNT(*) FROM training_block") fun observeCount(): Flow<Int>

  @Insert(onConflict = OnConflictStrategy.IGNORE)
  suspend fun insertBlockOrIgnore(block: TrainingBlockEntity): Long

  @Insert suspend fun insertBlockWorkouts(rows: List<BlockWorkoutEntity>)

  @Insert suspend fun insertScheduleDays(rows: List<BlockScheduleDayEntity>)
}
