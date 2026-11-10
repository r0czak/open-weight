package com.openweight.app.a11y

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.openweight.app.MainActivity
import com.openweight.app.core.TimeAndIds
import com.openweight.app.core.model.MealSlot
import com.openweight.app.core.model.QuantityUnit
import com.openweight.app.diet.data.DietRepository
import com.openweight.app.diet.data.IngredientValues
import com.openweight.app.diet.data.MealPlanRepository
import com.openweight.app.diet.data.RecipeInfo
import com.openweight.app.diet.data.ShoppingRepository
import com.openweight.app.transfer.ImportService
import com.openweight.app.transfer.ImportSession
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import java.io.File
import java.time.DayOfWeek
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Walks through every screen with real data (the Winter Arc plan, recipes, a diet plan, meals, a
 * shopping list) and checks each one with [accessibilityProblems].
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, qualifiers = "w411dp-h891dp")
class AccessibilityTest {
  @get:Rule(order = 0) val hiltRule = HiltAndroidRule(this)

  @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()

  @Inject lateinit var importer: ImportService
  @Inject lateinit var session: ImportSession
  @Inject lateinit var diet: DietRepository
  @Inject lateinit var meals: MealPlanRepository
  @Inject lateinit var shopping: ShoppingRepository
  @Inject lateinit var clock: TimeAndIds
  @Inject lateinit var household: com.openweight.app.diet.household.HouseholdRepository
  @Inject lateinit var settings: com.openweight.app.settings.SettingsRepository
  @Inject lateinit var bodyWeights: com.openweight.app.progress.BodyWeightRepository
  @Inject lateinit var workoutRepo: com.openweight.app.workout.data.WorkoutRepository
  @Inject lateinit var sessionRepo: com.openweight.app.workout.session.SessionRepository

  private val winterArc = File("../plans/winter-arc.openweight.json").readBytes()
  private val problems = mutableListOf<String>()

  @Before
  fun setUp() {
    hiltRule.inject()
    runBlocking {
      importer.import(winterArc)
      val oats = diet.saveIngredient(null, IngredientValues("Oats", "Grains", QuantityUnit.G, 379.0, 13.0, 68.0, 6.5))
      val porridge = diet.createRecipe(RecipeInfo("Porridge", 1, 5, "Cook it.", "", ""))
      diet.addItem(porridge, oats, 80.0, QuantityUnit.G)
      val plan = meals.createPlan("Lean week")
      meals.addPlanMeal(plan, DayOfWeek.MONDAY, MealSlot.BREAKFAST, porridge, 1.0)
      meals.addMeal(clock.today(), MealSlot.BREAKFAST, porridge, 1.0)
      shopping.generate(clock.today(), clock.today().plusDays(6))
      shopping.addManual("Coffee", null, null, "Drinks")
      // Progress and household, so their screens are walked with data.
      household.save(null, "Anna", 0.75, eatsByDefault = true)
      settings.setTargets(2900, 190)
      bodyWeights.log(clock.today(), 80.5)
      val pushA = workoutRepo.workouts().first().first { it.name == "Push A · Intro" }.id
      val s = sessionRepo.start(pushA, clock.today())
      sessionRepo.confirmSet(sessionRepo.open().first()!!.exercises[0].sets[0].id, 100.0, 10)
      sessionRepo.finish(s)
    }
  }

  /** Checks the screen once [ready] (a text or a test tag on it) shows, so its data has loaded. */
  private fun check(screen: String, ready: String? = null) {
    if (ready != null) {
      compose.waitUntil(5_000) {
        compose.onAllNodesWithText(ready, substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() ||
          compose.onAllNodesWithTag(ready, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
      }
    }
    compose.waitForIdle()
    compose.mainClock.advanceTimeBy(1_000)
    compose.waitForIdle()
    problems += compose.accessibilityProblems().map { "$screen: $it" }
  }

  private fun tap(tag: String) {
    compose.waitUntil(5_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
    compose.onNodeWithTag(tag).performScrollToIfNeeded().performClick()
  }

  private fun tapText(text: String) {
    compose.waitUntil(5_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    compose.onAllNodesWithText(text)[0].performClick()
  }

  private fun back() {
    compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
    compose.waitForIdle()
  }

  private fun androidx.compose.ui.test.SemanticsNodeInteraction.performScrollToIfNeeded() = runCatching { performScrollTo() }.getOrDefault(this)

  @Test
  fun everyScreen_labelsEverythingYouCanTap() {
    check("Home", ready = "Porridge")

    tap("tab_WORKOUT"); check("Workout", ready = "open_blocks")
    tap("open_blocks"); check("Blocks", ready = "Winter Arc 1 · Intro")
    tapText("Winter Arc 1 · Intro"); check("Block", ready = "day_TUESDAY"); back(); back()
    tap("open_workouts"); check("Workouts", ready = "Push A · Intro")
    tapText("Push A · Intro"); check("Workout editor", ready = "Leg press")
    tap("train_workout"); check("Workout overview", ready = "start_workout")
    tap("start_workout"); check("Live session", ready = "session_progress")
    tap("session_check_0_1"); check("Live session, resting", ready = "rest_bar")
    tap("finish_session"); tap("confirm_finish"); check("Session summary", ready = "session_summary")
    back(); back(); back(); back()
    tap("open_history"); check("History", ready = "history_list")
    tapText("Push A · Intro"); check("Finished session", ready = "session_detail"); back()
    tap("tab_WORKOUT"); tap("tab_WORKOUT")
    tap("open_weight"); check("Body weight", ready = "weight_input")
    tap("tab_WORKOUT"); tap("tab_WORKOUT") // back to the tab's first screen
    tap("open_power_level"); check("Power level", ready = "power_body")
    tap("tab_WORKOUT"); tap("tab_WORKOUT")

    tap("open_exercises"); check("Exercises", ready = "Ab wheel")
    tapText("Ab wheel"); check("Exercise", ready = "exercise_name")
    tap("exercise_progress"); check("Exercise progress", ready = "Ab wheel"); back(); back(); back()

    tap("tab_DIET"); check("Recipes", ready = "Porridge")
    tapText("Porridge"); check("Recipe", ready = "macros_card")
    tap("edit_recipe"); check("Recipe editor", ready = "Oats"); back(); back()
    tap("open_ingredients"); check("Ingredients", ready = "Oats"); back()
    tap("panel_CALENDAR"); check("Calendar", ready = "day_totals")
    tap("open_plans"); check("Diet plans", ready = "Lean week")
    tapText("Lean week"); check("Diet plan", ready = "Porridge"); back(); back()
    tap("panel_SHOPPING"); check("Shopping list", ready = "shop_Coffee")

    // Settings with targets and a household member (its dialogs never let Robolectric settle).
    tap("tab_SETTINGS"); check("Settings", ready = "member_Anna")
    tap("export"); check("Export", ready = "export_list")
    tap("export_option_ITEMS"); check("Export items", ready = "Winter Arc 1 · Intro"); back()
    runBlocking { session.openBytes(winterArc) }
    check("Import preview", ready = "import_choice_SKIP")

    assertEquals("", problems.joinToString("\n"))
  }
}
