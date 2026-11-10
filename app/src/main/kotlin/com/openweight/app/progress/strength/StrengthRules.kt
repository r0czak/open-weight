package com.openweight.app.progress.strength

import java.time.LocalDate
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.min

/** A done set from a finished session, as the strength rules read it. */
data class StrengthSet(val exerciseId: String?, val exerciseName: String, val weightKg: Double?, val reps: Int, val date: LocalDate)

/** What reaching the next tier takes, in the terms the lift is logged in. */
sealed interface NextGoal {
  /** An estimated one-rep max of this many kg on the bar. */
  data class OneRepMax(val kg: Double) : NextGoal

  /** This many reps at body weight. */
  data class Reps(val reps: Int) : NextGoal

  /** One rep with this much weight added to body weight. */
  data class Added(val kg: Double) : NextGoal
}

/** One exercise's best set in the window and the tier it reaches. */
data class LiftScore(
  val exerciseId: String?,
  val name: String,
  val standard: LiftStandard,
  /** The best set's weight (null for body weight alone) and reps. */
  val weightKg: Double?,
  val reps: Int,
  val date: LocalDate,
  /** Estimated one-rep max over body weight, or reps for [Load.REPS] lifts. */
  val value: Double,
  val score: Double,
  val next: NextGoal?,
) {
  val tier: Tier
    get() = StrengthRules.tierOf(score)
}

data class MuscleScore(val muscle: Muscle, val score: Double) {
  val tier: Tier
    get() = StrengthRules.tierOf(score)
}

data class StrengthReport(
  val bodyWeightKg: Double,
  val lifts: List<LiftScore>,
  val muscles: Map<Muscle, MuscleScore>,
  /** Lifts done in the window that the app has no standard for. */
  val unscored: List<String>,
) {
  /** The power level: the average of the muscles' scores, so every trained muscle counts the same. */
  val overall: Double?
    get() = muscles.values.takeIf { it.isNotEmpty() }?.map { it.score }?.average()

  val overallTier: Tier?
    get() = overall?.let(StrengthRules::tierOf)

  val strongest: MuscleScore?
    get() = muscles.values.maxByOrNull { it.score }

  val weakest: MuscleScore?
    get() = muscles.values.minByOrNull { it.score }
}

/**
 * The power level rules. Each lift's best set in the last [WINDOW_DAYS] days becomes an estimated
 * one-rep max (Epley, reps counted up to [MAX_REPS]) over today's body weight, and is placed against
 * the lift's thresholds as a score from 0 to 7: the whole part is the tier (0 = Untrained), the rest
 * is the way to the next one. A muscle's score is the average of the lifts that work it (a lift that
 * mainly works it counts double a lift that helps); the power level is the average of the muscles.
 */
object StrengthRules {
  const val WINDOW_DAYS = 180L
  const val MAX_REPS = 15
  const val MAX_SCORE = 7.0

  fun oneRepMax(kg: Double, reps: Int): Double = if (reps <= 1) kg else kg * (1 + min(reps, MAX_REPS) / 30.0)

  fun tierOf(score: Double): Tier = Tier.entries[floor(score).toInt().coerceIn(0, Tier.entries.size - 1)]

  /** Places [value] against [thresholds] (where Novice to World class start): 0 ≤ score < 7. */
  fun score(value: Double, thresholds: List<Double>): Double {
    val t = thresholds
    if (value < t[0]) return (value / t[0]).coerceAtLeast(0.0)
    for (i in 0 until t.size - 1) {
      if (value < t[i + 1]) return i + 1 + (value - t[i]) / (t[i + 1] - t[i])
    }
    val last = t.size - 1
    return min(MAX_SCORE - 0.001, last + 1 + (value - t[last]) / (t[last] - t[last - 1]))
  }

  /** A set's value for [lift]: one-rep max over body weight, or reps; null when it can't count. */
  fun value(lift: LiftStandard, weightKg: Double?, reps: Int, bodyWeightKg: Double): Double? {
    if (reps <= 0) return null
    val load =
      when (lift.load) {
        Load.REPS -> return reps.toDouble()
        Load.WEIGHT -> weightKg?.takeIf { it > 0 } ?: return null
        Load.BODYWEIGHT -> lift.bodyShare * bodyWeightKg + (weightKg ?: 0.0)
        Load.ASSISTED -> (bodyWeightKg - (weightKg ?: 0.0)).takeIf { it > 0 } ?: return null
      }
    return oneRepMax(load, reps) / bodyWeightKg
  }

  /** What the next tier asks for, or null at the top. */
  fun nextGoal(lift: LiftStandard, score: Double, standard: StrengthStandard, bodyWeightKg: Double): NextGoal? {
    val tierIndex = tierOf(score).ordinal
    if (tierIndex >= Tier.entries.size - 1) return null
    val target = lift.thresholds(standard)[tierIndex]
    return when (lift.load) {
      Load.REPS -> NextGoal.Reps(ceil(target).toInt())
      Load.WEIGHT -> NextGoal.OneRepMax(target * bodyWeightKg)
      Load.BODYWEIGHT,
      Load.ASSISTED -> {
        val body = (if (lift.load == Load.BODYWEIGHT) lift.bodyShare else 1.0) * bodyWeightKg
        val need = target * bodyWeightKg
        if (need <= body) NextGoal.Reps(1)
        else {
          // Reps at body weight while that stays within the counted range, else weight for one rep.
          val reps = ceil(30 * (need / body - 1)).toInt()
          if (reps <= MAX_REPS) NextGoal.Reps(maxOf(2, reps)) else NextGoal.Added(need - body)
        }
      }
    }
  }

  fun report(sets: List<StrengthSet>, bodyWeightKg: Double, standard: StrengthStandard): StrengthReport {
    val unscored = sortedSetOf<String>()
    val best = linkedMapOf<String, Pair<StrengthSet, Double>>()
    val ids = mutableMapOf<String, String>()
    for (set in sets.sortedBy { it.date }) {
      val key = StrengthStandards.normalize(set.exerciseName)
      val lift = StrengthStandards.forName(set.exerciseName)
      if (lift == null) {
        unscored += set.exerciseName
        continue
      }
      set.exerciseId?.let { ids[key] = it } // the latest id, for opening the exercise's progress
      val value = value(lift, set.weightKg, set.reps, bodyWeightKg) ?: continue
      val current = best[key]
      if (current == null || value > current.second) best[key] = set to value
    }
    val lifts =
      best.map { (key, pair) ->
          val (set, value) = pair
          val lift = StrengthStandards.forName(set.exerciseName)!!
          val score = score(value, lift.thresholds(standard))
          LiftScore(ids[key], set.exerciseName, lift, set.weightKg, set.reps, set.date, value, score, nextGoal(lift, score, standard, bodyWeightKg))
        }
        .sortedByDescending { it.score }
    val muscles =
      Muscle.entries
        .mapNotNull { muscle ->
          val worked = lifts.mapNotNull { l -> l.standard.muscles[muscle]?.let { w -> l.score to w } }
          if (worked.isEmpty()) null
          else muscle to MuscleScore(muscle, worked.sumOf { it.first * it.second } / worked.sumOf { it.second })
        }
        .toMap()
    return StrengthReport(bodyWeightKg, lifts, muscles, unscored.toList())
  }
}
