package com.openweight.app.progress.strength

/** The muscles the body map shows. Rear delts count as shoulders; lats and rhomboids as upper back. */
enum class Muscle { CHEST, SHOULDERS, TRAPS, BICEPS, TRICEPS, FOREARMS, UPPER_BACK, LOWER_BACK, ABS, GLUTES, QUADS, HAMSTRINGS, CALVES }

/** Which standards a lift is measured against. */
enum class StrengthStandard { MEN, WOMEN }

/** The seven strength tiers, weakest first. */
enum class Tier { UNTRAINED, NOVICE, INTERMEDIATE, PROFICIENT, ADVANCED, ELITE, WORLD_CLASS }

/** What a set's weight means for a lift. */
enum class Load {
  /** The weight is the load (per dumbbell for dumbbell lifts; their factor allows for it). */
  WEIGHT,
  /** A share of body weight, plus the weight when one is added (belt, vest, plate). */
  BODYWEIGHT,
  /** Body weight minus the weight, which is the machine's assistance. */
  ASSISTED,
  /** Reps alone count; the thresholds are rep counts. */
  REPS,
}

/**
 * A lift family: where each tier from Novice up starts, as estimated one-rep max over body weight
 * (or reps, for [Load.REPS]), for men; women's standards are these times [women].
 */
enum class Anchor(val men: List<Double>, val women: Double) {
  SQUAT(listOf(0.75, 1.0, 1.25, 1.75, 2.25, 2.75), 0.8),
  BENCH(listOf(0.5, 0.75, 1.0, 1.25, 1.6, 2.0), 0.6),
  DEADLIFT(listOf(1.0, 1.25, 1.5, 2.0, 2.5, 3.0), 0.8),
  PRESS(listOf(0.35, 0.5, 0.65, 0.8, 1.0, 1.25), 0.65),
  ROW(listOf(0.5, 0.65, 0.85, 1.05, 1.3, 1.6), 0.65),
  /** Body weight plus added weight, over body weight. */
  PULL_UP(listOf(0.9, 1.05, 1.2, 1.4, 1.65, 1.9), 0.85),
  CURL(listOf(0.2, 0.3, 0.4, 0.55, 0.7, 0.85), 0.6),
  /** Reps in one set. */
  CORE_REPS(listOf(1.0, 5.0, 10.0, 15.0, 20.0, 30.0), 0.8),
}

/**
 * How one exercise is scored: its family's thresholds times [factor], what its weight means, and
 * the muscles it works (1 = mainly, 0.5 = helps).
 */
data class LiftStandard(val anchor: Anchor, val factor: Double, val load: Load, val muscles: Map<Muscle, Double>, val bodyShare: Double = 1.0) {
  /** Where tiers Novice to World class start, for [standard]. */
  fun thresholds(standard: StrengthStandard): List<Double> {
    val scale = if (standard == StrengthStandard.WOMEN) anchor.women else 1.0
    return anchor.men.map { it * factor * scale }
  }
}

/** The exercises with a standard, matched by name (case and punctuation don't matter). */
object StrengthStandards {
  private const val MAIN = 1.0
  private const val HELPS = 0.5

  private fun lift(anchor: Anchor, factor: Double, vararg muscles: Pair<Muscle, Double>, load: Load = Load.WEIGHT, bodyShare: Double = 1.0) =
    LiftStandard(anchor, factor, load, muscles.toMap(), bodyShare)

  private val squat = arrayOf(Muscle.QUADS to MAIN, Muscle.GLUTES to MAIN, Muscle.LOWER_BACK to HELPS)
  private val legPress = arrayOf(Muscle.QUADS to MAIN, Muscle.GLUTES to HELPS)
  private val lunge = arrayOf(Muscle.QUADS to MAIN, Muscle.GLUTES to MAIN)
  private val deadlift =
    arrayOf(Muscle.HAMSTRINGS to MAIN, Muscle.GLUTES to MAIN, Muscle.LOWER_BACK to MAIN, Muscle.TRAPS to HELPS, Muscle.FOREARMS to HELPS, Muscle.QUADS to HELPS)
  private val trapBar =
    arrayOf(Muscle.QUADS to MAIN, Muscle.GLUTES to MAIN, Muscle.HAMSTRINGS to HELPS, Muscle.LOWER_BACK to HELPS, Muscle.TRAPS to HELPS, Muscle.FOREARMS to HELPS)
  private val hinge = arrayOf(Muscle.HAMSTRINGS to MAIN, Muscle.GLUTES to MAIN, Muscle.LOWER_BACK to HELPS, Muscle.FOREARMS to HELPS)
  private val press = arrayOf(Muscle.CHEST to MAIN, Muscle.TRICEPS to HELPS, Muscle.SHOULDERS to HELPS)
  private val closeGrip = arrayOf(Muscle.TRICEPS to MAIN, Muscle.CHEST to HELPS, Muscle.SHOULDERS to HELPS)
  private val dip = arrayOf(Muscle.CHEST to MAIN, Muscle.TRICEPS to MAIN, Muscle.SHOULDERS to HELPS)
  private val fly = arrayOf(Muscle.CHEST to MAIN)
  private val overhead = arrayOf(Muscle.SHOULDERS to MAIN, Muscle.TRICEPS to HELPS, Muscle.TRAPS to HELPS)
  private val raise = arrayOf(Muscle.SHOULDERS to MAIN)
  private val rearDelt = arrayOf(Muscle.SHOULDERS to MAIN, Muscle.TRAPS to HELPS, Muscle.UPPER_BACK to HELPS)
  private val row = arrayOf(Muscle.UPPER_BACK to MAIN, Muscle.TRAPS to HELPS, Muscle.BICEPS to HELPS, Muscle.FOREARMS to HELPS, Muscle.SHOULDERS to HELPS)
  private val bentRow = row + (Muscle.LOWER_BACK to HELPS)
  private val pull = arrayOf(Muscle.UPPER_BACK to MAIN, Muscle.BICEPS to HELPS, Muscle.FOREARMS to HELPS)
  private val chin = arrayOf(Muscle.UPPER_BACK to MAIN, Muscle.BICEPS to MAIN, Muscle.FOREARMS to HELPS)
  private val curl = arrayOf(Muscle.BICEPS to MAIN, Muscle.FOREARMS to HELPS)
  private val hammer = arrayOf(Muscle.BICEPS to MAIN, Muscle.FOREARMS to MAIN)
  private val reverseCurl = arrayOf(Muscle.FOREARMS to MAIN, Muscle.BICEPS to HELPS)
  private val triceps = arrayOf(Muscle.TRICEPS to MAIN)
  private val abs = arrayOf(Muscle.ABS to MAIN)

  private val byName: Map<String, LiftStandard> =
    mapOf(
      // Squats and legs
      "barbell back squat" to lift(Anchor.SQUAT, 1.0, *squat),
      "back squat" to lift(Anchor.SQUAT, 1.0, *squat),
      "squat" to lift(Anchor.SQUAT, 1.0, *squat),
      "front squat" to lift(Anchor.SQUAT, 0.85, *squat),
      "hack squat" to lift(Anchor.SQUAT, 1.1, *legPress),
      "pendulum squat" to lift(Anchor.SQUAT, 1.0, *legPress),
      "leg press" to lift(Anchor.SQUAT, 2.0, *legPress),
      "bulgarian split squat" to lift(Anchor.SQUAT, 0.3, *lunge),
      "walking lunge" to lift(Anchor.SQUAT, 0.3, *lunge),
      "leg extension" to lift(Anchor.SQUAT, 0.6, Muscle.QUADS to MAIN),
      "standing calf raise" to lift(Anchor.SQUAT, 1.0, Muscle.CALVES to MAIN),
      "seated calf raise" to lift(Anchor.SQUAT, 0.6, Muscle.CALVES to MAIN),
      // Hinges
      "conventional deadlift" to lift(Anchor.DEADLIFT, 1.0, *deadlift),
      "deadlift" to lift(Anchor.DEADLIFT, 1.0, *deadlift),
      "sumo deadlift" to lift(Anchor.DEADLIFT, 1.0, *deadlift),
      "trap bar deadlift" to lift(Anchor.DEADLIFT, 1.05, *trapBar),
      "romanian deadlift" to lift(Anchor.DEADLIFT, 0.75, *hinge),
      "stiff leg deadlift" to lift(Anchor.DEADLIFT, 0.7, *hinge),
      "dumbbell romanian deadlift" to lift(Anchor.DEADLIFT, 0.35, *hinge),
      "hip thrust" to lift(Anchor.DEADLIFT, 1.0, Muscle.GLUTES to MAIN, Muscle.HAMSTRINGS to HELPS),
      "lying leg curl" to lift(Anchor.DEADLIFT, 0.3, Muscle.HAMSTRINGS to MAIN),
      "seated leg curl" to lift(Anchor.DEADLIFT, 0.35, Muscle.HAMSTRINGS to MAIN),
      // Chest
      "barbell bench press" to lift(Anchor.BENCH, 1.0, *press),
      "bench press" to lift(Anchor.BENCH, 1.0, *press),
      "close grip bench press" to lift(Anchor.BENCH, 0.85, *closeGrip),
      "incline barbell press" to lift(Anchor.BENCH, 0.8, *press),
      "incline smith press" to lift(Anchor.BENCH, 0.85, *press),
      "incline dumbbell press" to lift(Anchor.BENCH, 0.35, *press),
      "dumbbell bench press" to lift(Anchor.BENCH, 0.4, *press),
      "machine chest press" to lift(Anchor.BENCH, 0.9, *press),
      "push up" to lift(Anchor.BENCH, 1.0, *press, load = Load.BODYWEIGHT, bodyShare = 0.64),
      "dip" to lift(Anchor.PULL_UP, 1.1, *dip, load = Load.BODYWEIGHT),
      "dips" to lift(Anchor.PULL_UP, 1.1, *dip, load = Load.BODYWEIGHT),
      "weighted dips" to lift(Anchor.PULL_UP, 1.1, *dip, load = Load.BODYWEIGHT),
      "cable fly" to lift(Anchor.BENCH, 0.3, *fly),
      "low to high cable fly" to lift(Anchor.BENCH, 0.25, *fly),
      "pec deck" to lift(Anchor.BENCH, 0.45, *fly),
      // Shoulders
      "overhead press" to lift(Anchor.PRESS, 1.0, *overhead),
      "standing overhead press" to lift(Anchor.PRESS, 1.0, *overhead),
      "seated barbell press" to lift(Anchor.PRESS, 1.05, *overhead),
      "seated dumbbell shoulder press" to lift(Anchor.PRESS, 0.38, *overhead),
      "arnold press" to lift(Anchor.PRESS, 0.33, *overhead),
      "machine shoulder press" to lift(Anchor.PRESS, 1.0, *overhead),
      "dumbbell lateral raise" to lift(Anchor.PRESS, 0.25, *raise),
      "lean away dumbbell lateral raise" to lift(Anchor.PRESS, 0.2, *raise),
      "cable lateral raise" to lift(Anchor.PRESS, 0.2, *raise),
      "face pull" to lift(Anchor.ROW, 0.4, *rearDelt),
      "reverse pec deck" to lift(Anchor.ROW, 0.35, *rearDelt),
      "rear delt cable fly" to lift(Anchor.ROW, 0.15, *rearDelt),
      // Back
      "barbell row" to lift(Anchor.ROW, 1.0, *bentRow),
      "pendlay row" to lift(Anchor.ROW, 0.95, *bentRow),
      "seal row" to lift(Anchor.ROW, 0.8, *row),
      "chest supported t bar row" to lift(Anchor.ROW, 0.9, *row),
      "chest supported machine row" to lift(Anchor.ROW, 1.0, *row),
      "meadows row" to lift(Anchor.ROW, 0.45, *row),
      "one arm dumbbell row" to lift(Anchor.ROW, 0.45, *row),
      "seated cable row" to lift(Anchor.ROW, 1.0, *row),
      "lat pulldown" to lift(Anchor.PULL_UP, 0.85, *pull),
      "wide grip lat pulldown" to lift(Anchor.PULL_UP, 0.8, *pull),
      "single arm lat pulldown" to lift(Anchor.PULL_UP, 0.45, *pull),
      "kneeling single arm pulldown" to lift(Anchor.PULL_UP, 0.45, *pull),
      "pull up" to lift(Anchor.PULL_UP, 1.0, *pull, load = Load.BODYWEIGHT),
      "weighted pull up" to lift(Anchor.PULL_UP, 1.0, *pull, load = Load.BODYWEIGHT),
      "chin up" to lift(Anchor.PULL_UP, 1.05, *chin, load = Load.BODYWEIGHT),
      "weighted chin up" to lift(Anchor.PULL_UP, 1.05, *chin, load = Load.BODYWEIGHT),
      "assisted pull up" to lift(Anchor.PULL_UP, 1.0, *pull, load = Load.ASSISTED),
      // Arms
      "barbell curl" to lift(Anchor.CURL, 1.0, *curl),
      "ez bar curl" to lift(Anchor.CURL, 1.0, *curl),
      "preacher curl" to lift(Anchor.CURL, 0.85, *curl),
      "dumbbell curl" to lift(Anchor.CURL, 0.4, *curl),
      "incline dumbbell curl" to lift(Anchor.CURL, 0.35, *curl),
      "bayesian cable curl" to lift(Anchor.CURL, 0.35, *curl),
      "hammer curl" to lift(Anchor.CURL, 0.45, *hammer),
      "reverse curl" to lift(Anchor.CURL, 0.75, *reverseCurl),
      "cable triceps pushdown" to lift(Anchor.BENCH, 0.45, *triceps),
      "ez bar skull crusher" to lift(Anchor.BENCH, 0.4, *triceps),
      "dumbbell overhead triceps extension" to lift(Anchor.BENCH, 0.3, *triceps),
      "overhead cable triceps extension" to lift(Anchor.BENCH, 0.35, *triceps),
      // Core
      "hanging leg raise" to lift(Anchor.CORE_REPS, 1.0, *abs, load = Load.REPS),
      "hanging knee raise" to lift(Anchor.CORE_REPS, 1.5, *abs, load = Load.REPS),
      "ab wheel" to lift(Anchor.CORE_REPS, 1.0, *abs, load = Load.REPS),
      "cable crunch" to lift(Anchor.CURL, 1.6, *abs),
    )

  /** The standard for an exercise name, or null when the app has none for it. */
  fun forName(name: String): LiftStandard? = byName[normalize(name)]

  /** "Push-up", "push up" and "PUSH UP " are the same; so are "45°" signs and hyphens. */
  fun normalize(name: String): String =
    name.lowercase().replace(Regex("[^a-z0-9]+"), " ").trim().replace(Regex("\\s+"), " ")
}
