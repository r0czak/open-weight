package com.openweight.app.progress

import com.openweight.app.progress.strength.Load
import com.openweight.app.progress.strength.Muscle
import com.openweight.app.progress.strength.NextGoal
import com.openweight.app.progress.strength.StrengthRules
import com.openweight.app.progress.strength.StrengthSet
import com.openweight.app.progress.strength.StrengthStandard
import com.openweight.app.progress.strength.StrengthStandards
import com.openweight.app.progress.strength.Tier
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StrengthRulesTest {
  private val day = LocalDate.of(2026, 11, 2)

  private fun set(name: String, kg: Double?, reps: Int, id: String? = null) = StrengthSet(id, name, kg, reps, day)

  @Test
  fun everyStarterAndPlanExercise_withWeightOrReps_hasAStandard_exceptTheOnesWithout() {
    val withoutStandard = listOf("Plank", "Nordic curl", "45° back extension")
    for (name in listOf("Barbell back squat", "Trap-bar deadlift", "EZ-bar skull crusher", "Chest-supported T-bar row", "Push-up", "Low-to-high cable fly")) {
      assertNotNull(name, StrengthStandards.forName(name))
    }
    for (name in withoutStandard) assertNull(name, StrengthStandards.forName(name))
    assertNotNull(StrengthStandards.forName("  bench   PRESS "))
  }

  @Test
  fun oneRepMax_isEpley_withRepsCountedUpTo15() {
    assertEquals(100.0, StrengthRules.oneRepMax(100.0, 1), 1e-9)
    assertEquals(116.666, StrengthRules.oneRepMax(100.0, 5), 1e-3)
    assertEquals(StrengthRules.oneRepMax(100.0, 15), StrengthRules.oneRepMax(100.0, 40), 1e-9)
  }

  @Test
  fun score_wholePartIsTheTier_restIsTheWayToTheNext() {
    val t = listOf(1.0, 2.0, 3.0, 4.0, 5.0, 6.0)
    assertEquals(0.5, StrengthRules.score(0.5, t), 1e-9)
    assertEquals(1.0, StrengthRules.score(1.0, t), 1e-9)
    assertEquals(3.25, StrengthRules.score(3.25, t), 1e-9)
    assertEquals(6.5, StrengthRules.score(6.5, t), 1e-9)
    assertTrue(StrengthRules.score(100.0, t) < 7.0)
    assertEquals(Tier.UNTRAINED, StrengthRules.tierOf(0.0))
    assertEquals(Tier.PROFICIENT, StrengthRules.tierOf(3.99))
    assertEquals(Tier.WORLD_CLASS, StrengthRules.tierOf(6.999))
  }

  @Test
  fun aSquat_ofOneAndAHalfBodyWeights_isIntermediate_forMen_andAdvanced_forWomen() {
    val sets = listOf(set("Barbell back squat", 120.0, 1))
    val men = StrengthRules.report(sets, 80.0, StrengthStandard.MEN)
    assertEquals(Tier.PROFICIENT, men.lifts.single().tier) // 1.5 × BW: past 1.25 (Proficient starts), under 1.75
    val women = StrengthRules.report(sets, 80.0, StrengthStandard.WOMEN)
    assertEquals(Tier.ADVANCED, women.lifts.single().tier) // women's thresholds are 0.8×: Advanced starts at 1.4
  }

  @Test
  fun theBestSetCounts_andMusclesAverageTheLiftsThatWorkThem() {
    val report =
      StrengthRules.report(
        listOf(set("Barbell bench press", 60.0, 5), set("Barbell bench press", 100.0, 1), set("Cable triceps pushdown", 40.0, 10)),
        80.0,
        StrengthStandard.MEN,
      )
    val bench = report.lifts.first { it.name == "Barbell bench press" }
    assertEquals(1.25, bench.value, 1e-9)
    assertEquals(4.0, bench.score, 1e-9) // exactly where Advanced starts
    val pushdown = report.lifts.first { it.name == "Cable triceps pushdown" }
    // Triceps: the pushdown mainly (weight 1), the bench helps (0.5).
    val triceps = report.muscles.getValue(Muscle.TRICEPS).score
    assertEquals((pushdown.score + 0.5 * bench.score) / 1.5, triceps, 1e-9)
    assertEquals(bench.score, report.muscles.getValue(Muscle.CHEST).score, 1e-9)
    assertNull(report.muscles[Muscle.QUADS])
    assertEquals(report.muscles.values.map { it.score }.average(), report.overall!!, 1e-9)
  }

  @Test
  fun bodyweightLifts_countBodyWeight_plusAdded_andAssistanceTakesItAway() {
    val pullUps = StrengthRules.report(listOf(set("Pull-up", null, 10)), 80.0, StrengthStandard.MEN).lifts.single()
    assertEquals(1 + 10 / 30.0, pullUps.value, 1e-9)
    val weighted = StrengthRules.report(listOf(set("Weighted pull-up", 20.0, 1)), 80.0, StrengthStandard.MEN).lifts.single()
    assertEquals(1.25, weighted.value, 1e-9)
    val assisted = StrengthRules.report(listOf(set("Assisted pull-up", 30.0, 1)), 80.0, StrengthStandard.MEN).lifts.single()
    assertEquals(50.0 / 80.0, assisted.value, 1e-9)
    val pushUps = StrengthRules.report(listOf(set("Push-up", null, 1)), 80.0, StrengthStandard.MEN).lifts.single()
    assertEquals(0.64, pushUps.value, 1e-9)
  }

  @Test
  fun setsThatCantCount_areLeftOut_andUnknownLiftsAreListed() {
    val report =
      StrengthRules.report(
        listOf(set("Barbell bench press", null, 5), set("Barbell bench press", 60.0, 0), set("Assisted pull-up", 90.0, 5), set("Plank", null, 60)),
        80.0,
        StrengthStandard.MEN,
      )
    assertTrue(report.lifts.isEmpty())
    assertTrue(report.muscles.isEmpty())
    assertNull(report.overall)
    assertEquals(listOf("Plank"), report.unscored)
  }

  @Test
  fun sameLiftUnderTwoSpellings_isOneLift_linkedToTheLatestId() {
    val report =
      StrengthRules.report(
        listOf(StrengthSet("old", "Pull-up", null, 5, day), StrengthSet("new", "pull up", null, 3, day.plusDays(1))),
        80.0,
        StrengthStandard.MEN,
      )
    assertEquals(1, report.lifts.size)
    assertEquals("new", report.lifts.single().exerciseId)
    assertEquals(5, report.lifts.single().reps)
  }

  @Test
  fun nextGoal_speaksTheLiftsLanguage() {
    val squat = StrengthStandards.forName("Barbell back squat")!!
    // Proficient (1.25–1.75): the next tier, Advanced, starts at a 1.75 × 80 = 140 kg one-rep max.
    assertEquals(NextGoal.OneRepMax(140.0), StrengthRules.nextGoal(squat, 3.5, StrengthStandard.MEN, 80.0))
    val pullUp = StrengthStandards.forName("Pull-up")!!
    // Intermediate → Proficient at 1.2 × BW: 6 reps at body weight.
    assertEquals(NextGoal.Reps(6), StrengthRules.nextGoal(pullUp, 2.5, StrengthStandard.MEN, 80.0))
    // Elite → World class at 1.9 × BW: past 15 reps, so as added weight for one rep.
    val added = StrengthRules.nextGoal(pullUp, 5.5, StrengthStandard.MEN, 80.0) as NextGoal.Added
    assertEquals(72.0, added.kg, 1e-9)
    val legRaise = StrengthStandards.forName("Hanging leg raise")!!
    assertEquals(Load.REPS, legRaise.load)
    assertEquals(NextGoal.Reps(10), StrengthRules.nextGoal(legRaise, 2.2, StrengthStandard.MEN, 80.0))
    assertNull(StrengthRules.nextGoal(squat, 6.5, StrengthStandard.MEN, 80.0))
  }
}
