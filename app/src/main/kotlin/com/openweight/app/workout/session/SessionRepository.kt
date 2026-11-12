package com.openweight.app.workout.session

import androidx.room.withTransaction
import com.openweight.app.core.TimeAndIds
import com.openweight.app.core.database.OpenWeightDatabase
import com.openweight.app.core.database.entity.SessionSetEntity
import com.openweight.app.core.database.entity.WorkoutSessionEntity
import com.openweight.app.workout.data.SetValues
import com.openweight.app.workout.data.WorkoutRepository
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

data class SessionSet(
  val id: String,
  val targetRepsMin: Int,
  val targetRepsMax: Int?,
  val restSeconds: Int,
  /** What was done once [done]; before that, the suggestion (last time's, or the plan's). */
  val weightKg: Double?,
  val reps: Int,
  val done: Boolean,
  val completedAt: Instant?,
)

data class SessionExercise(val position: Int, val exerciseId: String?, val name: String, val sets: List<SessionSet>)

data class Session(
  val id: String,
  val workoutId: String?,
  val workoutName: String,
  val plannedDate: LocalDate,
  val startedAt: Instant,
  val finishedAt: Instant?,
  val exercises: List<SessionExercise>,
) {
  private val sets: List<SessionSet>
    get() = exercises.flatMap { it.sets }

  val setCount: Int
    get() = sets.size

  val doneCount: Int
    get() = sets.count { it.done }

  /** Weight × reps over the done sets, in kg. */
  val volumeKg: Double
    get() = sets.filter { it.done }.sumOf { (it.weightKg ?: 0.0) * it.reps }

  /** The first set not done yet, top to bottom. */
  val nextSetId: String?
    get() = sets.firstOrNull { !it.done }?.id

  /** When the rest after the last confirmed set ends; null before the first set or once finished. */
  val restEndsAt: Instant?
    get() =
      if (finishedAt != null) null
      else sets.filter { it.done && it.completedAt != null }.maxByOrNull { it.completedAt!! }?.let { it.completedAt!!.plusSeconds(it.restSeconds.toLong()) }

  fun duration(now: Instant): Duration = Duration.between(startedAt, finishedAt ?: now)
}

/** A workout that was trained, as Home's week view shows it. */
data class DoneWorkout(val sessionId: String, val workoutId: String?, val workoutName: String)

/**
 * Live training. A session is a copy of a workout's plan that is filled in set by set; training
 * never changes the plan. One session is open at a time. Rules live here.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class SessionRepository
@Inject
constructor(private val db: OpenWeightDatabase, private val clock: TimeAndIds) {
  private val dao = db.sessionDao()

  /** The open session, if any. */
  fun open(): Flow<Session?> =
    dao.observeOpen().flatMapLatest { s -> if (s == null) flowOf(null) else dao.observeSets(s.id).map { s.toModel(it) } }

  fun session(id: String): Flow<Session?> =
    combine(dao.observe(id), dao.observeSets(id)) { s, sets -> s?.toModel(sets) }

  /** Finished sessions by the day they count for. */
  fun done(from: LocalDate, to: LocalDate): Flow<Map<LocalDate, List<DoneWorkout>>> =
    dao.observeFinished(from, to).map { list ->
      list.groupBy({ it.plannedDate }, { DoneWorkout(it.id, it.workoutId, it.workoutName) })
    }

  /**
   * Starts training [workoutId] for [date] (the day it was planned on; today when started from the
   * workout itself) and returns the session's id. If a session is already open, returns that one
   * instead: finish or discard it first. Each set starts with what was done last time for that
   * exercise (same set number, else the last set), or the plan's weight and the bottom of its
   * rep range.
   */
  suspend fun start(workoutId: String, date: LocalDate?): String =
    db.withTransaction {
      dao.getOpen()?.let { return@withTransaction it.id }
      val workout = requireNotNull(db.workoutDao().getWorkout(workoutId)) { "Workout not found" }
      val entries = db.workoutDao().getEntries(workoutId)
      require(entries.any { it.sets.isNotEmpty() }) { "This workout has no sets yet" }
      val now = clock.now()
      val id = clock.newId()
      dao.insertSession(WorkoutSessionEntity(id, workout.id, workout.name, date ?: clock.today(), now, createdAt = now, updatedAt = now))
      val sets =
        entries.flatMapIndexed { exercisePosition, entry ->
          val last = dao.lastDoneSets(entry.exercise.id)
          entry.sets.sortedBy { it.position }.mapIndexed { setPosition, plan ->
            val before = last.getOrNull(setPosition) ?: last.lastOrNull()
            SessionSetEntity(
              id = clock.newId(),
              sessionId = id,
              exerciseId = entry.exercise.id,
              exerciseName = entry.exercise.name,
              exercisePosition = exercisePosition,
              setPosition = setPosition,
              targetRepsMin = plan.repsMin,
              targetRepsMax = plan.repsMax,
              restSeconds = plan.restSeconds,
              weightKg = before?.weightKg ?: plan.weightKg,
              reps = before?.reps ?: plan.repsMin,
              createdAt = now,
              updatedAt = now,
            )
          }
        }
      dao.insertSets(sets)
      id
    }

  /**
   * Marks a set done with what was lifted; confirming it again corrects it without restarting the
   * rest. A changed weight carries over to the exercise's later sets that are not done yet.
   */
  suspend fun confirmSet(setId: String, weightKg: Double?, reps: Int) {
    require(reps in 0..SetValues.MAX_REPS) { "Reps must be between 0 and ${SetValues.MAX_REPS}" }
    require(weightKg == null || weightKg in 0.0..SetValues.MAX_WEIGHT_KG) { "Weight out of range" }
    db.withTransaction {
      val set = openSet(setId) ?: return@withTransaction
      val now = clock.now()
      dao.updateSet(set.copy(weightKg = weightKg, reps = reps, done = true, completedAt = set.completedAt ?: now, updatedAt = now))
      // The sets still to come in this exercise follow a changed weight, if they shared the old suggestion.
      if (weightKg != set.weightKg) {
        dao.getSets(set.sessionId)
          .filter { it.exercisePosition == set.exercisePosition && it.setPosition > set.setPosition && !it.done }
          .filter { it.weightKg == null || it.weightKg == set.weightKg }
          .forEach { dao.updateSet(it.copy(weightKg = weightKg, updatedAt = now)) }
      }
    }
  }

  /** Back to "not done"; the numbers stay as the suggestion. */
  suspend fun undoSet(setId: String) {
    db.withTransaction {
      val set = openSet(setId) ?: return@withTransaction
      dao.updateSet(set.copy(done = false, completedAt = null, updatedAt = clock.now()))
    }
  }

  /**
   * One more set for an exercise, copying its last set, up to [MAX_SETS_PER_EXERCISE]. The workout's
   * plan is not changed.
   */
  suspend fun addSet(sessionId: String, exercisePosition: Int) {
    db.withTransaction {
      val session = dao.get(sessionId)?.takeIf { it.finishedAt == null } ?: return@withTransaction
      val sets = dao.getSets(session.id).filter { it.exercisePosition == exercisePosition }
      val last = sets.lastOrNull() ?: return@withTransaction
      // At the limit nothing happens (the screen hides the button there), rather than a crash.
      if (sets.size >= MAX_SETS_PER_EXERCISE) return@withTransaction
      val now = clock.now()
      dao.insertSets(listOf(last.copy(id = clock.newId(), setPosition = last.setPosition + 1, done = false, completedAt = null, createdAt = now, updatedAt = now)))
    }
  }

  /** Removes a set from the session (not from the plan). An exercise keeps at least one: then nothing happens. */
  suspend fun removeSet(setId: String) {
    db.withTransaction {
      val set = openSet(setId) ?: return@withTransaction
      val siblings = dao.getSets(set.sessionId).count { it.exercisePosition == set.exercisePosition }
      // An exercise keeps at least one set (leave it undone to skip it); the screen offers no remove there.
      if (siblings > 1) dao.deleteSet(setId)
    }
  }

  /** Closes the session. Sets left undone stay in it as skipped. Needs at least one done set. */
  suspend fun finish(sessionId: String) {
    db.withTransaction {
      val session = dao.get(sessionId)?.takeIf { it.finishedAt == null } ?: return@withTransaction
      require(dao.getSets(sessionId).any { it.done }) { "Nothing is done yet; discard the session instead" }
      val now = clock.now()
      dao.updateSession(session.copy(finishedAt = now, updatedAt = now))
    }
  }

  /** Deletes an open session and everything logged in it. */
  suspend fun discard(sessionId: String) {
    db.withTransaction { dao.get(sessionId)?.takeIf { it.finishedAt == null }?.let { dao.deleteSession(it.id) } }
  }

  /** A set of a session that is still open; finished sessions are not edited here. */
  private suspend fun openSet(setId: String): SessionSetEntity? {
    val set = dao.getSet(setId) ?: return null
    return set.takeIf { dao.get(set.sessionId)?.finishedAt == null }
  }

  companion object {
    /** The same limit as a workout's plan. */
    const val MAX_SETS_PER_EXERCISE = WorkoutRepository.MAX_SETS
  }
}

private fun WorkoutSessionEntity.toModel(sets: List<SessionSetEntity>) =
  Session(
    id = id,
    workoutId = workoutId,
    workoutName = workoutName,
    plannedDate = plannedDate,
    startedAt = startedAt,
    finishedAt = finishedAt,
    exercises =
      sets
        .groupBy { it.exercisePosition }
        .toSortedMap()
        .map { (position, list) ->
          SessionExercise(
            position,
            list.first().exerciseId,
            list.first().exerciseName,
            list.sortedBy { it.setPosition }.map { SessionSet(it.id, it.targetRepsMin, it.targetRepsMax, it.restSeconds, it.weightKg, it.reps, it.done, it.completedAt) },
          )
        },
  )
