package com.openweight.app.workout.session

import com.openweight.app.core.model.WeightUnit
import com.openweight.app.core.model.toKg
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProgressTest {
  private fun set(kg: Double?, reps: Int, done: Boolean = true, min: Int = 8, max: Int? = 10) = LoggedSet(kg, reps, done, min, max)

  @Test
  fun stepUp_onlyWhenEverySetReachedTheTop() {
    assertEquals(62.5, Progress.lastTime(listOf(set(60.0, 10), set(60.0, 10), set(60.0, 11)), WeightUnit.KG)!!.stepUpKg!!, 1e-9)
    assertNull(Progress.lastTime(listOf(set(60.0, 10), set(60.0, 9)), WeightUnit.KG)!!.stepUpKg) // one short
    assertNull(Progress.lastTime(listOf(set(60.0, 10), set(60.0, 10, done = false)), WeightUnit.KG)!!.stepUpKg) // one skipped
    assertNull(Progress.lastTime(listOf(set(null, 12)), WeightUnit.KG)!!.stepUpKg) // bodyweight
    // No range: the single target counts as the top. Pounds step by 5 lb.
    assertEquals(100.0 + WeightUnit.LB.toKg(5.0), Progress.lastTime(listOf(set(100.0, 5, min = 5, max = null)), WeightUnit.LB)!!.stepUpKg!!, 1e-9)
  }

  @Test
  fun lastTime_showsTheDoneSets_andNothingWithoutOne() {
    assertEquals(listOf(set(60.0, 10)), Progress.lastTime(listOf(set(60.0, 10), set(60.0, 0, done = false)), WeightUnit.KG)!!.sets)
    assertNull(Progress.lastTime(listOf(set(60.0, 8, done = false)), WeightUnit.KG))
  }

  @Test
  fun bestSet_isTheHeaviest_thenTheMostReps() {
    val day = LocalDate.of(2026, 11, 10)
    val history =
      listOf(
        ExerciseSession("a", day, Instant.EPOCH, listOf(set(80.0, 5), set(70.0, 10))),
        ExerciseSession("b", day.plusDays(3), Instant.EPOCH, listOf(set(80.0, 6), set(85.0, 2, done = false))),
      )
    assertEquals(set(80.0, 6), Progress.bestSet(history))
    assertEquals(80.0, history[0].topWeightKg!!, 0.0)
  }
}
