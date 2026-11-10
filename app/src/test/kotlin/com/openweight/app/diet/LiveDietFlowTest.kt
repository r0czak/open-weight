package com.openweight.app.diet

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
import android.os.Looper
import com.openweight.app.MainActivity
import com.openweight.app.core.TimeAndIds
import com.openweight.app.core.model.MealSlot
import com.openweight.app.core.model.QuantityUnit
import com.openweight.app.diet.data.DietRepository
import com.openweight.app.diet.data.IngredientValues
import com.openweight.app.diet.data.MealPlanRepository
import com.openweight.app.diet.data.RecipeInfo
import com.openweight.app.settings.SettingsRepository
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Targets in Settings → tick a meal on Home → eaten and left; swap a meal on the calendar. */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class LiveDietFlowTest {
  @get:Rule(order = 0) val hiltRule = HiltAndroidRule(this)

  @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()

  @Inject lateinit var diet: DietRepository
  @Inject lateinit var meals: MealPlanRepository
  @Inject lateinit var settings: SettingsRepository
  @Inject lateinit var clock: TimeAndIds

  private lateinit var today: LocalDate

  @Before
  fun setUp() {
    hiltRule.inject()
    today = clock.today()
    runBlocking {
      val oats = diet.saveIngredient(null, IngredientValues("Oats", "", QuantityUnit.G, 400.0, 10.0, 70.0, 7.0))
      val porridge = diet.createRecipe(RecipeInfo("Porridge", 1, null, "", "", "")).also { diet.addItem(it, oats, 100.0, QuantityUnit.G) }
      diet.createRecipe(RecipeInfo("Omelette", 1, null, "", "", "")).also { diet.addItem(it, oats, 50.0, QuantityUnit.G) }
      meals.addMeal(today, MealSlot.BREAKFAST, porridge, 1.0)
      meals.addMeal(today, MealSlot.DINNER, porridge, 2.0)
    }
  }

  private fun waitForTag(tag: String) = compose.waitUntil(5_000) { compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }

  @Test
  fun targets_eatenOnHome_andWhatIsLeft() {
    compose.onNodeWithTag("tab_SETTINGS").performClick()
    waitForTag("targets")
    compose.onNodeWithTag("targets").performScrollTo().performClick()
    compose.onNodeWithTag("target_kcal").performTextReplacement("2000")
    compose.onNodeWithTag("target_protein").performTextReplacement("50")
    compose.onNodeWithTag("targets_save").performClick()
    // The save runs on the main thread: let it run between checks.
    compose.waitUntil(5_000) {
      shadowOf(Looper.getMainLooper()).idle()
      runBlocking { settings.settings.first().kcalTarget == 2000 }
    }

    compose.onNodeWithTag("tab_HOME").performClick()
    compose.waitUntil(5_000) { runCatching { compose.onNodeWithTag("week_agenda").performScrollToNode(hasTestTag("day_card_$today")) }.isSuccess }
    compose.onAllNodesWithTag("day_eaten_${today}_Porridge")[0].performClick()

    compose.waitUntil(5_000) {
      runCatching {
        compose.onNodeWithTag("day_progress_$today", useUnmergedTree = true)
          .assertTextEquals("Eaten 400 kcal of 1,200 kcal · protein 10 g of 30 g · 1,600 kcal left to target · 40 g protein to go")
      }.isSuccess
    }
  }

  @Test
  fun swapAMeal_onTheCalendar() {
    compose.onNodeWithTag("tab_DIET").performClick()
    waitForTag("panel_CALENDAR")
    compose.onNodeWithTag("panel_CALENDAR").performClick()
    compose.waitUntil(5_000) { runCatching { compose.onNodeWithTag("calendar").performScrollToNode(hasTestTag("meal_Porridge")) }.isSuccess }
    compose.onAllNodesWithTag("meal_Porridge")[0].performClick()
    compose.onNodeWithTag("swap_meal").performClick()
    compose.waitUntil(5_000) { compose.onAllNodesWithText("Omelette").fetchSemanticsNodes().isNotEmpty() }
    compose.onNodeWithText("Omelette").performClick()
    compose.onNodeWithTag("confirm_servings").performClick()

    compose.waitUntil(5_000) {
      runBlocking { meals.days(today, today).first().single().meals.map { it.recipeName }.sorted() == listOf("Omelette", "Porridge") }
    }
    assertEquals(2, runBlocking { meals.days(today, today).first().single().meals.size })
  }
}
