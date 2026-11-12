package com.openweight.app.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.openweight.app.core.database.entity.SessionSetEntity
import com.openweight.app.core.database.entity.WorkoutSessionEntity
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {
  /** The open session; the app allows one at a time. */
  @Query("SELECT * FROM workout_session WHERE finishedAt IS NULL ORDER BY startedAt DESC LIMIT 1")
  fun observeOpen(): Flow<WorkoutSessionEntity?>

  @Query("SELECT * FROM workout_session WHERE finishedAt IS NULL ORDER BY startedAt DESC LIMIT 1")
  suspend fun getOpen(): WorkoutSessionEntity?

  @Query("SELECT * FROM workout_session WHERE id = :id") fun observe(id: String): Flow<WorkoutSessionEntity?>

  @Query("SELECT * FROM workout_session WHERE id = :id") suspend fun get(id: String): WorkoutSessionEntity?

  @Query("SELECT * FROM workout_session WHERE finishedAt IS NOT NULL AND plannedDate BETWEEN :from AND :to")
  fun observeFinished(from: LocalDate, to: LocalDate): Flow<List<WorkoutSessionEntity>>

  @Query("SELECT * FROM session_set WHERE sessionId = :sessionId ORDER BY exercisePosition, setPosition")
  fun observeSets(sessionId: String): Flow<List<SessionSetEntity>>

  @Query("SELECT * FROM session_set WHERE sessionId = :sessionId ORDER BY exercisePosition, setPosition")
  suspend fun getSets(sessionId: String): List<SessionSetEntity>

  @Query("SELECT * FROM session_set WHERE id = :id") suspend fun getSet(id: String): SessionSetEntity?

  /** The done sets of the most recent finished session that trained [exerciseId]. */
  @Query(
    """
    SELECT s.* FROM session_set s WHERE s.done = 1 AND s.exerciseId = :exerciseId AND s.sessionId = (
      SELECT ws.id FROM workout_session ws JOIN session_set x ON x.sessionId = ws.id
      WHERE ws.finishedAt IS NOT NULL AND x.exerciseId = :exerciseId AND x.done = 1
      ORDER BY ws.finishedAt DESC LIMIT 1)
    ORDER BY s.setPosition
    """
  )
  suspend fun lastDoneSets(exerciseId: String): List<SessionSetEntity>

  @Insert suspend fun insertSession(session: WorkoutSessionEntity)

  @Update suspend fun updateSession(session: WorkoutSessionEntity)

  @Query("DELETE FROM workout_session WHERE id = :id") suspend fun deleteSession(id: String)

  @Insert suspend fun insertSets(sets: List<SessionSetEntity>)

  @Update suspend fun updateSet(set: SessionSetEntity)

  @Query("DELETE FROM session_set WHERE id = :id") suspend fun deleteSet(id: String)
}
