package com.openweight.app.workout

import com.openweight.app.core.model.WeightUnit
import com.openweight.app.workout.data.SetValues
import com.openweight.app.workout.workouts.FormError
import com.openweight.app.workout.workouts.SetForm
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** What the set dialog accepts: typed or pasted text must never reach the database as nonsense. */
class SetFormTest {
  private fun form(unit: WeightUnit = WeightUnit.KG) = SetForm(SetValues.DEFAULT, unit)

  @Test
  fun weight_isReadInTheUsersUnit_withCommaOrDot() {
    assertEquals(62.5, form().apply { weight = "62,5" }.toValues()!!.weightKg!!, 0.0)
    assertEquals(45.359, form(WeightUnit.LB).apply { weight = "100" }.toValues()!!.weightKg!!, 0.001)
    assertNull(form().apply { weight = " " }.toValues()!!.weightKg)
  }

  @Test
  fun weightThatIsNotASaneNumber_isAnError_notACrash() {
    listOf("abc", "-5", "NaN", "Infinity", "1e999", "2001").forEach { text ->
      val f = form().apply { weight = text }
      assertNull(text, f.toValues())
      assertEquals(text, FormError.WEIGHT, f.error)
    }
    // The limit is in kilograms whatever the unit shown.
    assertNull(form(WeightUnit.LB).apply { weight = "4500" }.toValues())
    assertEquals(2000.0, form().apply { weight = "2000" }.toValues()!!.weightKg!!, 0.0)
  }

  @Test
  fun repsAndRest_areChecked() {
    assertEquals(FormError.REPS, form().apply { repsMin = "0" }.also { it.toValues() }.error)
    assertEquals(FormError.REPS_MAX, form().apply { repsMin = "10"; repsMax = "8" }.also { it.toValues() }.error)
    assertEquals(FormError.REST, form().apply { rest = "4000" }.also { it.toValues() }.error)
    assertEquals(SetValues(5, null, null, 60), form().apply { repsMin = "5"; repsMax = "5"; rest = "60" }.toValues())
  }
}
