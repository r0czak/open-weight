package com.openweight.app.transfer

import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import com.openweight.app.MainActivity
import com.openweight.app.core.model.QuantityUnit
import com.openweight.app.diet.data.DietRepository
import com.openweight.app.diet.data.IngredientValues
import com.openweight.app.diet.data.RecipeInfo
import com.openweight.app.workout.data.SetValues
import com.openweight.app.workout.data.WorkoutRepository
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
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

/** Import preview with a conflict choice, Replace everything and its undo, and exporting. */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class TransferFlowTest {
  @get:Rule(order = 0) val hiltRule = HiltAndroidRule(this)

  @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()

  @Inject lateinit var workouts: WorkoutRepository
  @Inject lateinit var diet: DietRepository
  @Inject lateinit var exporter: ExportService
  @Inject lateinit var session: ImportSession

  private lateinit var pushA: String

  @Before
  fun setUp() {
    hiltRule.inject()
    runBlocking {
      val bench = workouts.saveExercise(null, "Bench press", "Chest", "Barbell", "")
      pushA = workouts.createWorkout("Push A", "Push")
      workouts.addExercise(pushA, bench, 3, SetValues(8, 10, 60.0, 90))
      val oats = diet.saveIngredient(null, IngredientValues("Oats", "", QuantityUnit.G))
      val porridge = diet.createRecipe(RecipeInfo("Porridge", 1, 5, "", "", ""))
      diet.addItem(porridge, oats, 80.0, QuantityUnit.G)
    }
  }

  private fun waitForTag(tag: String) = compose.waitUntil(5_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }

  private fun waitUntilDisplayed(text: String) =
    compose.waitUntil(5_000) { runCatching { compose.onNodeWithText(text, substring = true).assertIsDisplayed() }.isSuccess }

  private fun scrollTo(list: String, tag: String) =
    compose.waitUntil(5_000) { runCatching { compose.onNodeWithTag(list).performScrollToNode(hasTestTag(tag)) }.isSuccess }

  private fun open(file: ExportedFile) = runBlocking { session.openBytes(file.bytes) }

  @Test
  fun sharedWorkout_alreadyHere_canBeReplaced() {
    val file = runBlocking { exporter.export(ExportRequest.Items(workoutIds = setOf(pushA))) }
    runBlocking { workouts.updateWorkoutInfo(pushA, "Push A (my edit)", "Push", "") }

    // Opening a file shows its preview in the Settings tab.
    open(file)
    waitForTag("import_preview")
    waitUntilDisplayed("Shared items")
    compose.onNodeWithText("Workout: Push A").assertIsDisplayed()
    compose.onNodeWithText("New: 0 · already in the app: 1", useUnmergedTree = true).assertExists()

    scrollTo("import_preview", "import_choice_REPLACE")
    compose.onNodeWithTag("import_choice_REPLACE").performClick()
    compose.onNodeWithTag("import_go").performClick()

    waitForTag("import_done")
    compose.onNodeWithText("Workouts · replaced: 1", substring = true).assertIsDisplayed()
    compose.onNodeWithTag("import_finish").performClick()
    waitForTag("import_file")
    assertEquals(listOf("Push A"), runBlocking { workouts.workouts().first().map { it.name } })
  }

  @Test
  fun replaceEverything_thenUndo() {
    val backup = runBlocking { exporter.export(ExportRequest.Everything, SettingsDto("KG", "SYSTEM")) }
    runBlocking { workouts.createWorkout("Added after the backup", "") }

    open(backup)
    waitForTag("import_preview")
    scrollTo("import_preview", "import_replace_all")
    compose.onNodeWithTag("import_replace_all").performClick()
    compose.onNodeWithTag("import_go").performClick()
    compose.onNodeWithTag("import_replace_confirm").performClick()
    waitForTag("import_done")
    compose.onNodeWithText("Settings → Undo last restore", substring = true).assertIsDisplayed()
    compose.onNodeWithTag("import_finish").performClick()
    assertEquals(listOf("Push A"), runBlocking { workouts.workouts().first().map { it.name } })

    // Settings offers the copy saved before; putting it back brings the newer workout back.
    waitForTag("undo_restore")
    compose.onNodeWithTag("undo_restore").performScrollTo().performClick()
    compose.onNodeWithTag("undo_restore_continue").performClick()
    waitUntilDisplayed("Your data before the last restore")
    compose.onNodeWithTag("import_go").performClick()
    compose.onNodeWithTag("import_replace_confirm").performClick()
    waitForTag("import_done")
    assertEquals(setOf("Push A", "Added after the backup"), runBlocking { workouts.workouts().first().map { it.name }.toSet() })
  }

  @Test
  fun badFile_saysWhy_andChangesNothing() {
    runBlocking { session.openBytes("""{"format":"open-weight","formatVersion":99}""".toByteArray()) }

    waitUntilDisplayed("newer version of Open Weight")
    compose.onNodeWithText("Nothing was changed.", substring = true).assertIsDisplayed()
  }

  @Test
  fun exportPickedItems_fromSettings() {
    compose.onNodeWithTag("tab_SETTINGS").performClick()
    waitForTag("export")
    compose.onNodeWithTag("export").performScrollTo().performClick()
    waitForTag("export_list")
    scrollTo("export_list", "export_option_ITEMS")
    compose.onNodeWithTag("export_option_ITEMS").performClick()
    scrollTo("export_list", "export_item_Push A")
    compose.onNodeWithTag("export_item_Push A").performClick()
    compose.onNodeWithTag("export_go").performClick()

    waitUntilDisplayed("openweight-workout-push-a.json")
    // Share hands the file to Android's chooser.
    compose.onNodeWithTag("export_share").performClick()
    compose.waitUntil(5_000) { shadowOf(compose.activity).peekNextStartedActivity() != null }
    assertEquals(Intent.ACTION_CHOOSER, shadowOf(compose.activity).nextStartedActivity.action)
  }

  @Test
  fun exportOneRecipe_fromItsScreen() {
    compose.onNodeWithTag("tab_DIET").performClick()
    waitUntilDisplayed("Porridge")
    compose.onAllNodesWithText("Porridge")[0].performClick()
    waitForTag("macros_card")
    compose.onNodeWithContentDescription("More options").performClick()
    compose.onNodeWithTag("export_item").performClick()
    waitUntilDisplayed("openweight-recipe-porridge.json")
  }
}
