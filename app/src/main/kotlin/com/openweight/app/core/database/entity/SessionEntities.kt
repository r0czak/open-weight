package com.openweight.app.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.time.LocalDate

/**
 * One time a workout was trained: a copy of its plan, filled in set by set. The workout's name is
 * kept as text, so history stays readable after the workout is renamed or deleted.
 * [plannedDate] is the day the session counts for (the day it was opened from, or the day it
 * started); [finishedAt] is null while the session is open.
 */
@Entity(
  tableName = "workout_session",
  foreignKeys =
    [
      ForeignKey(
        entity = WorkoutEntity::class,
        parentColumns = ["id"],
        childColumns = ["workoutId"],
        onDelete = ForeignKey.SET_NULL,
      )
    ],
  indices = [Index("workoutId"), Index("plannedDate"), Index("finishedAt")],
)
data class WorkoutSessionEntity(
  @PrimaryKey val id: String,
  val workoutId: String?,
  val workoutName: String,
  val plannedDate: LocalDate,
  val startedAt: Instant,
  val finishedAt: Instant? = null,
  val notes: String = "",
  val createdAt: Instant,
  val updatedAt: Instant,
)

/**
 * One set of a session: its target (copied from the plan) and what was actually done.
 * [weightKg] and [reps] start as a suggestion (last time's, or the plan's) and count once [done].
 */
@Entity(
  tableName = "session_set",
  foreignKeys =
    [
      ForeignKey(
        entity = WorkoutSessionEntity::class,
        parentColumns = ["id"],
        childColumns = ["sessionId"],
        onDelete = ForeignKey.CASCADE,
      ),
      ForeignKey(
        entity = ExerciseEntity::class,
        parentColumns = ["id"],
        childColumns = ["exerciseId"],
        onDelete = ForeignKey.SET_NULL,
      ),
    ],
  indices = [Index("sessionId"), Index("exerciseId")],
)
data class SessionSetEntity(
  @PrimaryKey val id: String,
  val sessionId: String,
  val exerciseId: String?,
  val exerciseName: String,
  /** The exercise's place in the session, then the set's place in the exercise. */
  val exercisePosition: Int,
  val setPosition: Int,
  val targetRepsMin: Int,
  val targetRepsMax: Int? = null,
  val restSeconds: Int,
  val weightKg: Double? = null,
  val reps: Int,
  val done: Boolean = false,
  val completedAt: Instant? = null,
  val createdAt: Instant,
  val updatedAt: Instant,
)
