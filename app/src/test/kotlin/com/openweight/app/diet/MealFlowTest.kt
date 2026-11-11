package com.openweight.app.diet

import androidx.compose.ui.test.assertIsDisplayed
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
import com.openweight.app.testing.scrollToText
import com.openweight.app.MainActivity
import com.openweight.app.core.TimeAndIds
import com.openweight.app.core.model.MealSlot
import com.openweight.app.core.model.QuantityUnit
import com.openweight.app.diet.data.DietRepository
import com.openweight.app.diet.data.IngredientValues
import com.openweight.app.diet.data.MealPlanRepository
import com.openweight.app.diet.data.RecipeInfo
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Calendar → meal → day totals → Home → recipe; and a diet plan applied to the calendar. */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class MealFlowTest {
  @get:Rule(order = 0) val hiltRule = HiltAndroidRule(this)

  @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()

  @Inject lateinit var diet: DietRepository
  @Inject lateinit var meals: MealPlanRepository
  @Inject lateinit var clock: TimeAndIds

  private lateinit var today: LocalDate
  private lateinit var oats: String

  @Before
  fun setUp() {
    hiltRule.inject()
    today = clock.today()
    runBlocking {
      val o = diet.saveIngredient(null, IngredientValues("Oats", "", QuantityUnit.G, 379.0, 13.2, 67.7, 6.5))
      oats = diet.createRecipe(RecipeInfo("Overnight oats", 1, 5, "", "", ""))
      diet.addItem(oats, o, 80.0, QuantityUnit.G)
    }
  }

  private fun waitForTag(tag: String) =
    compose.waitUntil(5_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }

  private fun waitUntilDisplayed(text: String) =
    compose.waitUntil(5_000) { runCatching { compose.scrollToText(text, substring = true); runCatching { compose.onNodeWithText(text, substring = true).performScrollTo() }; compose.onNodeWithText(text, substring = true).assertIsDisplayed() }.isSuccess }

  private fun scrollTo(list: String, tag: String) =
    compose.waitUntil(5_000) { runCatching { compose.onNodeWithTag(list).performScrollToNode(hasTestTag(tag)) }.isSuccess }

  @Test
  fun addMealToToday_showsTotals_onCalendarAndHome_andOpensTheRecipe() {
    compose.onNodeWithTag("tab_DIET").performClick()
    waitForTag("panel_CALENDAR")
    compose.onNodeWithTag("panel_CALENDAR").performClick()

    scrollTo("calendar", "add_BREAKFAST")
    compose.onNodeWithTag("add_BREAKFAST").performClick()
    waitForTag("pick_recipe_Overnight oats")
    compose.onNodeWithTag("pick_recipe_Overnight oats").performClick()
    compose.onNodeWithTag("confirm_servings").performClick()

    // Back on the calendar: the meal and the day's totals.
    scrollTo("calendar", "meal_Overnight oats")
    waitUntilDisplayed("303 kcal · protein")

    // Home shows the same meal on today's card; tapping it opens the recipe.
    compose.onNodeWithTag("tab_HOME").performClick()
    // Home scrolls itself to today when it opens; retry until that settles.
    scrollTo("week_agenda", "day_meal_${today}_Overnight oats")
    // The day card is tappable, so its texts merge into it: read the total from the unmerged tree.
    compose.onNodeWithTag("day_kcal_$today", useUnmergedTree = true).assertTextEquals("303 kcal")
    compose.onNodeWithTag("day_meal_${today}_Overnight oats").performClick()
    waitForTag("macros_card")
  }

  @Test
  fun dietPlan_appliesToTheCalendar() {
    val plan = runBlocking {
      val id = meals.createPlan("Lean bulk")
      meals.addPlanMeal(id, java.time.DayOfWeek.MONDAY, MealSlot.BREAKFAST, oats, 2.0)
      id
    }

    compose.onNodeWithTag("tab_DIET").performClick()
    waitForTag("panel_CALENDAR")
    compose.onNodeWithTag("panel_CALENDAR").performClick()
    waitForTag("open_plans")
    compose.onNodeWithTag("open_plans").performScrollTo().performClick()
    waitUntilDisplayed("Lean bulk")
    compose.onNodeWithText("Lean bulk").performClick()

    // The default range is 4 weeks from next Monday: four Mondays get the plan's breakfast.
    waitForTag("apply_plan")
    compose.onNodeWithTag("apply_plan").performClick()
    compose.onNodeWithTag("confirm_apply").performClick()
    waitUntilDisplayed("Filled 4 days.")
    check(plan.isNotEmpty())
  }
}
