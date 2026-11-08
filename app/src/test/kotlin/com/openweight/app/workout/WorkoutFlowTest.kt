package com.openweight.app.workout

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import com.openweight.app.MainActivity
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The M1 happy path through the real app: library → workout → exercise with sets → edit a set. */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class WorkoutFlowTest {
  @get:Rule(order = 0) val hiltRule = HiltAndroidRule(this)

  @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()

  @Before fun setUp() = hiltRule.inject()

  private fun waitForText(text: String) =
    compose.waitUntil(5_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }

  private fun waitForTag(tag: String) =
    compose.waitUntil(5_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }

  /** Screens slide in and out; wait until the one we left is really gone. */
  private fun waitUntilGone(tag: String) =
    compose.waitUntil(5_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isEmpty() }

  @Test
  fun createExercise_thenBuildWorkout_thenEditASet() {
    // Exercise library: add one.
    compose.onNodeWithTag("open_exercises").performClick()
    waitForText("No exercises yet.")
    compose.onNodeWithTag("add_exercise").performClick()
    compose.onNodeWithTag("exercise_name").performTextReplacement("Bench press")
    compose.onNodeWithTag("save_exercise").performClick()
    waitUntilGone("exercise_name")
    compose.onNodeWithText("Bench press").assertIsDisplayed()
    compose.onNodeWithContentDescription("Back").performClick()
    waitForTag("open_workouts")

    // Workouts: create one, which opens the editor.
    compose.onNodeWithTag("open_workouts").performClick()
    waitForTag("add_workout")
    compose.onNodeWithTag("add_workout").performClick()
    compose.onNodeWithTag("workout_name").performTextReplacement("Push A")
    compose.onNodeWithTag("workout_type").performTextReplacement("Push")
    compose.onNodeWithTag("confirm_workout").performClick()
    waitForText("No exercises in this workout yet.")

    // Add the exercise with 4 × 5–7 at 80 kg.
    compose.onNodeWithTag("add_exercise_to_workout").performClick()
    waitUntilGone("add_exercise_to_workout")
    waitForText("Bench press")
    compose.onNodeWithText("Bench press").performClick()
    compose.onNodeWithTag("set_count").performTextReplacement("4")
    compose.onNodeWithTag("reps_min").performTextReplacement("5")
    compose.onNodeWithTag("reps_max").performTextReplacement("7")
    compose.onNodeWithTag("weight").performTextReplacement("80")
    compose.onNodeWithTag("add_sets").performClick()
    waitForTag("add_exercise_to_workout")

    waitForText("1. Bench press")
    compose.waitUntil(5_000) { compose.onAllNodesWithText("5–7").fetchSemanticsNodes().size == 4 }
    compose.onAllNodesWithText("80 kg").fetchSemanticsNodes().size.let { assert(it == 4) { "expected 4 sets at 80 kg, got $it" } }

    // Edit the first set to 82.5 kg.
    compose.onAllNodesWithText("80 kg")[0].performClick()
    compose.onNodeWithTag("weight").performTextReplacement("82.5")
    compose.onNodeWithTag("save_set").performClick()
    waitForText("82.5 kg")
    compose.waitUntil(5_000) { compose.onAllNodesWithText("80 kg").fetchSemanticsNodes().size == 3 }
  }
}
