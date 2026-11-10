package com.openweight.app.diet.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.openweight.app.core.database.OpenWeightDatabase
import com.openweight.app.core.model.MealSlot
import com.openweight.app.core.model.QuantityUnit
import com.openweight.app.testing.FakeTimeAndIds
import com.openweight.app.testing.inMemoryDatabase
import com.openweight.app.workout.data.DeleteResult
import java.time.DayOfWeek
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MealPlanRepositoryTest {
  private lateinit var db: OpenWeightDatabase
  private lateinit var diet: DietRepository
  private lateinit var meals: MealPlanRepository
  private lateinit var oats: String // 1 serving = 80 g oats = 303.2 kcal
  private lateinit var chicken: String // 2 servings of 400 g chicken = 240 kcal each

  private val mon = LocalDate.of(2026, 11, 9)

  @Before
  fun setUp() {
    db = inMemoryDatabase()
    val clock = FakeTimeAndIds()
    diet = DietRepository(db, clock)
    meals = MealPlanRepository(db, diet, clock)
    runBlocking {
      val o = diet.saveIngredient(null, IngredientValues("Oats", "", QuantityUnit.G, 379.0, 13.2, 67.7, 6.5))
      val c = diet.saveIngredient(null, IngredientValues("Chicken", "", QuantityUnit.G, 120.0, 22.5, 0.0, 2.6))
      oats = diet.createRecipe(RecipeInfo("Oats", 1, null, "", "", ""))
      diet.addItem(oats, o, 80.0, QuantityUnit.G)
      chicken = diet.createRecipe(RecipeInfo("Chicken", 2, null, "", "", ""))
      diet.addItem(chicken, c, 400.0, QuantityUnit.G)
    }
  }

  @After fun tearDown() = db.close()

  private suspend fun day(date: LocalDate) = meals.days(date, date).first().single()

  @Test
  fun dayTotals_scaleByServings_inSlotOrder() = runTest {
    meals.addMeal(mon, MealSlot.DINNER, chicken, 1.5)
    meals.addMeal(mon, MealSlot.BREAKFAST, oats, 1.0)

    val d = day(mon)
    assertEquals(listOf("Oats", "Chicken"), d.meals.map { it.recipeName })
    assertEquals(303.2 + 360.0, d.total.kcal, 0.01)
    assertTrue(d.complete)
    assertEquals(7, meals.days(mon, mon.plusDays(6)).first().size) // empty days are listed too
  }

  @Test
  fun servings_canChange_andAreValidated() = runTest {
    val id = meals.addMeal(mon, MealSlot.LUNCH, chicken, 1.0)
    meals.setServings(id, 2.0)
    assertEquals(480.0, day(mon).total.kcal, 0.01)

    assertThrows(IllegalArgumentException::class.java) { runBlocking { meals.setServings(id, 0.0) } }
    assertThrows(IllegalArgumentException::class.java) { runBlocking { meals.addMeal(mon, MealSlot.LUNCH, oats, 25.0) } }
  }

  @Test
  fun copyDay_appendsOrReplaces() = runTest {
    meals.addMeal(mon, MealSlot.BREAKFAST, oats, 1.0)
    meals.addMeal(mon, MealSlot.DINNER, chicken, 1.0)
    val tue = mon.plusDays(1)
    meals.addMeal(tue, MealSlot.SNACK, oats, 0.5)

    assertEquals(2, meals.copyDay(mon, tue, replace = false))
    assertEquals(3, day(tue).meals.size)

    meals.copyDay(mon, tue, replace = true)
    assertEquals(listOf("Oats", "Chicken"), day(tue).meals.map { it.recipeName })
    assertEquals(2, day(mon).meals.size) // the source is untouched
  }

  @Test
  fun copyWeek_shiftsEveryDayBySevenDays() = runTest {
    meals.addMeal(mon, MealSlot.BREAKFAST, oats, 1.0)
    meals.addMeal(mon.plusDays(6), MealSlot.DINNER, chicken, 1.0)

    assertEquals(2, meals.copyWeek(mon, mon.plusWeeks(1), replace = true))

    assertEquals(listOf("Oats"), day(mon.plusWeeks(1)).meals.map { it.recipeName })
    assertEquals(listOf("Chicken"), day(mon.plusDays(13)).meals.map { it.recipeName })
  }

  @Test
  fun applyPlan_fillsByWeekday_andSkipsOrReplacesBusyDays() = runTest {
    val plan = meals.createPlan("Lean bulk")
    meals.addPlanMeal(plan, DayOfWeek.MONDAY, MealSlot.BREAKFAST, oats, 1.0)
    meals.addPlanMeal(plan, DayOfWeek.MONDAY, MealSlot.DINNER, chicken, 1.0)
    meals.addPlanMeal(plan, DayOfWeek.WEDNESDAY, MealSlot.LUNCH, chicken, 2.0)
    val busyMonday = mon.plusWeeks(1)
    meals.addMeal(busyMonday, MealSlot.SNACK, oats, 1.0)

    val skip = meals.applyPlan(plan, mon, mon.plusDays(13), ApplyMode.SKIP_DAYS_WITH_MEALS)
    assertEquals(ApplyResult(daysFilled = 3, daysSkipped = 1, mealsAdded = 4), skip)
    assertEquals(listOf("Oats"), day(busyMonday).meals.map { it.recipeName }) // kept as it was
    assertTrue(day(mon).meals.all { it.fromPlan })
    assertEquals(listOf("Chicken"), day(mon.plusDays(2)).meals.map { it.recipeName })

    val replace = meals.applyPlan(plan, busyMonday, busyMonday, ApplyMode.REPLACE)
    assertEquals(ApplyResult(1, 0, 2), replace)
    assertEquals(listOf("Oats", "Chicken"), day(busyMonday).meals.map { it.recipeName })
  }

  @Test
  fun planSummary_averagesKcalPerDay() = runTest {
    val plan = meals.createPlan("Lean bulk")
    meals.addPlanMeal(plan, DayOfWeek.MONDAY, MealSlot.BREAKFAST, oats, 1.0)
    meals.addPlanMeal(plan, DayOfWeek.TUESDAY, MealSlot.DINNER, chicken, 2.0)

    val s = meals.plans().first().single()
    assertEquals(2, s.mealCount)
    assertEquals((303.2 + 480.0) / 7, s.averageKcalPerDay, 0.01)
    assertEquals(480.0, meals.plan(plan).first()!!.dayTotal(DayOfWeek.TUESDAY).kcal, 0.01)
  }

  @Test
  fun deletingAPlan_keepsItsCalendarMeals_andRecipesInUseAreProtected() = runTest {
    val plan = meals.createPlan("Lean bulk")
    meals.addPlanMeal(plan, DayOfWeek.MONDAY, MealSlot.BREAKFAST, oats, 1.0)
    meals.applyPlan(plan, mon, mon, ApplyMode.REPLACE)

    assertEquals(DeleteResult.InUse(2), diet.deleteRecipe(oats)) // on the calendar and in the plan
    meals.deletePlan(plan)

    val d = day(mon)
    assertEquals(listOf("Oats"), d.meals.map { it.recipeName })
    assertFalse(d.meals.single().fromPlan)
  }

  @Test
  fun applyPlan_rejectsBackwardsAndHugeRanges() {
    assertThrows(IllegalArgumentException::class.java) {
      runBlocking { meals.applyPlan(meals.createPlan("P"), mon, mon.minusDays(1), ApplyMode.REPLACE) }
    }
    assertThrows(IllegalArgumentException::class.java) {
      runBlocking { meals.applyPlan(meals.createPlan("Q"), mon, mon.plusYears(2), ApplyMode.REPLACE) }
    }
  }

  @Test
  fun eatenMarks_swaps_andCopies() = runTest {
    val breakfast = meals.addMeal(mon, MealSlot.BREAKFAST, oats, 1.0)
    val dinner = meals.addMeal(mon, MealSlot.DINNER, chicken, 1.0)

    meals.setEaten(breakfast, true)
    var day = meals.days(mon, mon).first().single()
    assertEquals(listOf(true, false), day.meals.map { it.eaten })
    assertEquals(303.2, day.eaten.kcal, 0.1)
    assertEquals(543.2, day.total.kcal, 0.1)

    // Swapping takes the other recipe for that day only, not eaten yet; the plan link goes.
    meals.swapRecipe(breakfast, chicken, 2.0)
    day = meals.days(mon, mon).first().single()
    assertEquals(listOf("Chicken" to false, "Chicken" to false), day.meals.map { it.recipeName to it.eaten })
    assertEquals(480.0, day.meals.first().macros.kcal, 0.1)

    // A copied day is planned, not eaten.
    meals.setEaten(dinner, true)
    meals.copyDay(mon, mon.plusDays(1), replace = false)
    assertTrue(meals.days(mon.plusDays(1), mon.plusDays(1)).first().single().meals.none { it.eaten })
    meals.setEaten(dinner, false)
    assertTrue(meals.days(mon, mon).first().single().meals.none { it.eaten })
  }
}

