package com.openweight.app.diet.household

import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import com.openweight.app.MainActivity
import com.openweight.app.core.TimeAndIds
import com.openweight.app.core.model.MealSlot
import com.openweight.app.core.model.QuantityUnit
import com.openweight.app.diet.data.ApplyMode
import com.openweight.app.diet.data.DietRepository
import com.openweight.app.diet.data.IngredientValues
import com.openweight.app.diet.data.MealPlanRepository
import com.openweight.app.diet.data.RecipeInfo
import com.openweight.app.diet.data.ShoppingRepository
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import java.time.DayOfWeek
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Portions for two: who eats each meal, your numbers stay yours, the shopping list counts everyone. */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class HouseholdTest {
  @get:Rule(order = 0) val hiltRule = HiltAndroidRule(this)

  @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()

  @Inject lateinit var household: HouseholdRepository
  @Inject lateinit var diet: DietRepository
  @Inject lateinit var meals: MealPlanRepository
  @Inject lateinit var shopping: ShoppingRepository
  @Inject lateinit var clock: TimeAndIds

  private lateinit var today: LocalDate
  private lateinit var porridge: String

  @Before
  fun setUp() {
    hiltRule.inject()
    today = clock.today()
    porridge = runBlocking {
      val oats = diet.saveIngredient(null, IngredientValues("Oats", "", QuantityUnit.G, 400.0, 10.0, 70.0, 7.0))
      diet.createRecipe(RecipeInfo("Porridge", 1, null, "", "", "")).also { diet.addItem(it, oats, 80.0, QuantityUnit.G) }
    }
  }

  @Test
  fun rules_forMembers(): Unit = runBlocking {
    household.save(null, "Anna", 0.75, eatsByDefault = true)
    assertThrows(IllegalArgumentException::class.java) { runBlocking { household.save(null, "anna", 1.0, true) } }
    assertThrows(IllegalArgumentException::class.java) { runBlocking { household.save(null, "Bob", 5.0, true) } }
    assertThrows(IllegalArgumentException::class.java) { runBlocking { household.save(null, " ", 1.0, true) } }
    assertEquals(listOf("Anna" to 0.75), household.members().first().map { it.name to it.portion })
  }

  @Test
  fun newAndPlannedMeals_includeTheDefaultEaters_yourNumbersStayYours_shoppingCountsEveryone(): Unit = runBlocking {
    val anna = household.save(null, "Anna", 0.75, eatsByDefault = true)
    household.save(null, "Guest", 1.0, eatsByDefault = false)

    meals.addMeal(today, MealSlot.BREAKFAST, porridge, 1.0)
    val plan = meals.createPlan("Week").also { meals.addPlanMeal(it, today.plusDays(1).dayOfWeek, MealSlot.DINNER, porridge, 1.0) }
    meals.applyPlan(plan, today.plusDays(1), today.plusDays(1), ApplyMode.REPLACE)

    val days = meals.days(today, today.plusDays(1)).first()
    assertEquals(listOf(listOf("Anna"), listOf("Anna")), days.map { d -> d.meals.single().members.map { it.name } })
    assertEquals(320.0, days[0].total.kcal, 0.1) // yours only: 80 g oats
    assertEquals(1.75, days[0].meals.single().householdServings, 0.0)

    // Copies keep who eats; the shopping list buys for everyone (2 meals × 80 g × 1.75).
    meals.copyDay(today, today.plusDays(2), replace = false)
    assertEquals(listOf(anna), meals.days(today.plusDays(2), today.plusDays(2)).first().single().meals.single().members.map { it.id })
    shopping.generate(today, today.plusDays(1))
    assertEquals(280.0, shopping.current().first()!!.items.single().quantity!!, 0.0)

    // Removing someone leaves the meals, now just yours.
    household.delete(anna)
    assertEquals(emptyList<String>(), meals.days(today, today).first().single().meals.single().members.map { it.name })
  }

  @Test
  fun settings_calendar_andCookingForTheHousehold() {
    runBlocking { meals.addMeal(today, MealSlot.BREAKFAST, porridge, 1.0) }

    // Add Anna in Settings.
    compose.onNodeWithTag("tab_SETTINGS").performClick()
    compose.waitUntil(5_000) { compose.onAllNodesWithTag("add_member").fetchSemanticsNodes().isNotEmpty() }
    compose.onNodeWithTag("add_member").performScrollTo().performClick()
    compose.onNodeWithTag("member_name").performTextReplacement("Anna")
    compose.onNodeWithTag("member_portion").performTextReplacement("0.75")
    compose.onNodeWithTag("member_save").performClick()
    compose.waitUntil(5_000) { runBlocking { household.members().first().size == 1 } }

    // The breakfast was added before Anna joined: add her from the calendar.
    compose.onNodeWithTag("tab_DIET").performClick()
    compose.waitUntil(5_000) { compose.onAllNodesWithTag("panel_CALENDAR").fetchSemanticsNodes().isNotEmpty() }
    compose.onNodeWithTag("panel_CALENDAR").performClick()
    compose.waitUntil(5_000) { runCatching { compose.onNodeWithTag("calendar").performScrollToNode(hasTestTag("meal_Porridge")) }.isSuccess }
    compose.onNodeWithTag("meal_Porridge").performClick()
    compose.onNodeWithTag("eater_Anna").performClick()
    compose.onNodeWithTag("confirm_servings").performClick()
    compose.waitUntil(5_000) { runBlocking { meals.days(today, today).first().single().meals.single().members.size == 1 } }
    compose.waitUntil(5_000) { compose.onAllNodesWithText("1 serving · with Anna", substring = true).fetchSemanticsNodes().isNotEmpty() }

    // The recipe page scales its amounts for the household.
    compose.onNodeWithTag("panel_RECIPES").performClick()
    compose.waitUntil(5_000) { compose.onAllNodesWithText("Porridge").fetchSemanticsNodes().isNotEmpty() }
    compose.onAllNodesWithText("Porridge")[0].performClick()
    compose.waitUntil(5_000) { compose.onAllNodesWithTag("cook_for_household").fetchSemanticsNodes().isNotEmpty() }
    compose.onNodeWithTag("recipe_view").performScrollToNode(hasTestTag("qty_Oats"))
    compose.onNodeWithTag("qty_Oats", useUnmergedTree = true).assertTextEquals("80 g")
    compose.onNodeWithTag("recipe_view").performScrollToNode(hasTestTag("cook_for_household"))
    compose.onNodeWithTag("cook_for_household").performClick()
    compose.onNodeWithTag("recipe_view").performScrollToNode(hasTestTag("qty_Oats"))
    compose.onNodeWithTag("qty_Oats", useUnmergedTree = true).assertTextEquals("140 g")
  }
}
