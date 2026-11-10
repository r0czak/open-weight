package com.openweight.app.workout.session

import com.openweight.app.core.model.WeightUnit
import com.openweight.app.core.model.toKg
import com.openweight.app.workout.data.SetValues
import java.time.Instant
import java.time.LocalDate

/** A set as it was done: what history, "last time" and the step-up hint read. */
data class LoggedSet(
  val weightKg: Double?,
  val reps: Int,
  val done: Boolean,
  val targetRepsMin: Int = reps,
  val targetRepsMax: Int? = null,
)

/** One exercise in one finished session. */
data class ExerciseSession(val sessionId: String, val date: LocalDate, val finishedAt: Instant, val sets: List<LoggedSet>) {
  /** The heaviest set, null when every set was bodyweight. */
  val topWeightKg: Double?
    get() = sets.mapNotNull { it.weightKg }.maxOrNull()
}

/** What "last time" says about an exercise, and the next step up when it has earned one. */
data class LastTime(val sets: List<LoggedSet>, val stepUpKg: Double?)

/**
 * The progress rules: the best set is the heaviest (most reps breaks a tie); an exercise has
 * earned a step up when, last time, every planned set was done and reached the top of its rep
 * range with a weight on the bar. The step is 2.5 kg, or 5 lb in pounds.
 */
object Progress {
  fun bestSet(history: List<ExerciseSession>): LoggedSet? =
    history.flatMap { it.sets }.filter { it.done }.maxWithOrNull(compareBy<LoggedSet>({ it.weightKg ?: 0.0 }, { it.reps }))

  fun lastTime(sets: List<LoggedSet>, unit: WeightUnit): LastTime? {
    val done = sets.filter { it.done }
    if (done.isEmpty()) return null
    val earned = sets.all { it.done && it.weightKg != null && it.reps >= (it.targetRepsMax ?: it.targetRepsMin) }
    val step = if (unit == WeightUnit.KG) 2.5 else unit.toKg(5.0)
    val top = done.mapNotNull { it.weightKg }.maxOrNull()
    // Never past the app's weight limit: "Use" would be refused.
    val next = top?.plus(step)?.takeIf { it <= SetValues.MAX_WEIGHT_KG }
    return LastTime(done, if (earned) next else null)
  }
}
