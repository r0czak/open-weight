package com.openweight.app.workout

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import com.openweight.app.MainActivity
import com.openweight.app.transfer.ImportService
import com.openweight.app.transfer.KindResult
import com.openweight.app.workout.data.SetValues
import com.openweight.app.workout.data.StarterExercises
import com.openweight.app.workout.data.WorkoutRepository
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import java.io.File
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

/** The common exercises, and the warning before an exercise leaves a workout. */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class StarterExercisesTest {
  @get:Rule(order = 0) val hiltRule = HiltAndroidRule(this)

  @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()

  @Inject lateinit var starter: StarterExercises
  @Inject lateinit var importer: ImportService
  @Inject lateinit var workouts: WorkoutRepository

  @Before fun setUp() = hiltRule.inject()

  private fun waitForTag(tag: String) = compose.waitUntil(5_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }

  private fun waitUntilDisplayed(text: String) =
    compose.waitUntil(5_000) { runCatching { compose.onNodeWithText(text, substring = true).assertIsDisplayed() }.isSuccess }

  @Test
  fun starterList_skipsWhatYouHave_andAddsNothingTwice() = runBlocking {
    importer.import(File("../plans/winter-arc.openweight.json").readBytes())

    assertEquals(KindResult(added = 9, skipped = 31), starter.add())
    assertEquals(KindResult(skipped = 40), starter.add())
    assertEquals(69 + 9, workouts.exercises().first().size)
  }

  @Test
  fun emptyLibrary_offersTheCommonExercises() {
    compose.onNodeWithTag("tab_WORKOUT").performClick()
    waitForTag("open_exercises")
    compose.onNodeWithTag("open_exercises").performClick()
    waitForTag("add_starter")
    compose.onNodeWithTag("add_starter").performClick()

    waitUntilDisplayed("Added 40 exercises.")
    compose.onNodeWithText("Barbell back squat").assertIsDisplayed()
  }

  @Test
  fun removingAnExerciseFromAWorkout_asksFirst() {
    val id = runBlocking {
      val squat = workouts.saveExercise(null, "Squat", "Quads", "Barbell", "")
      workouts.createWorkout("Legs", "").also { workouts.addExercise(it, squat, 3, SetValues(5)) }
    }
    compose.onNodeWithTag("tab_WORKOUT").performClick()
    waitForTag("open_workouts")
    compose.onNodeWithTag("open_workouts").performClick()
    waitUntilDisplayed("Legs")
    compose.onNodeWithText("Legs").performClick()
    waitUntilDisplayed("Squat")
    compose.onNodeWithTag("entry_menu_0").performClick()
    compose.onNodeWithText("Remove from workout").performClick()

    compose.onNodeWithText("Its 3 sets are removed with it.", substring = true).assertIsDisplayed()
    compose.onNodeWithTag("confirm_remove_entry").performClick()
    compose.waitUntil(5_000) { runBlocking { workouts.workout(id).first()!!.entries.isEmpty() } }
  }

  @Test
  fun workoutList_searchesByNameOrType() {
    runBlocking {
      workouts.createWorkout("Push A", "Push")
      workouts.createWorkout("Pull A", "Pull")
      workouts.createWorkout("Legs", "Lower")
    }
    compose.onNodeWithTag("tab_WORKOUT").performClick()
    waitForTag("open_workouts")
    compose.onNodeWithTag("open_workouts").performClick()
    waitForTag("search")

    compose.onNodeWithTag("search").performTextReplacement("lower")
    waitUntilDisplayed("Legs")
    compose.onNodeWithText("Push A").assertDoesNotExist()
    compose.onNodeWithTag("search").performTextReplacement("squats")
    waitUntilDisplayed("Nothing matches")
  }
}
