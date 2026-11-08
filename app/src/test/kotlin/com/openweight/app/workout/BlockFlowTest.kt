package com.openweight.app.workout

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import com.openweight.app.MainActivity
import com.openweight.app.core.TimeAndIds
import com.openweight.app.workout.blocks.TrainingBlockRepository
import com.openweight.app.workout.data.SetValues
import com.openweight.app.workout.data.WorkoutRepository
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import javax.inject.Inject
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class BlockFlowTest {
  @get:Rule(order = 0) val hiltRule = HiltAndroidRule(this)

  @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()

  @Inject lateinit var workouts: WorkoutRepository
  @Inject lateinit var blocks: TrainingBlockRepository
  @Inject lateinit var clock: TimeAndIds

  @Before fun setUp() = hiltRule.inject()

  private fun waitForText(text: String) =
    compose.waitUntil(5_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }

  private fun waitForTag(tag: String) =
    compose.waitUntil(5_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }

  private fun waitUntilDayShows(day: String, text: String) =
    compose.waitUntil(5_000) {
      compose.onAllNodes(hasTestTag("day_$day") and hasText(text)).fetchSemanticsNodes().isNotEmpty()
    }

  @Test
  fun todayCard_showsTheWorkoutPlannedForToday() {
    val today = clock.today()
    runBlocking {
      val bench = workouts.saveExercise(null, "Bench press", "Chest", "Barbell", "")
      val push = workouts.createWorkout("Push A", "Push")
      workouts.addExercise(push, bench, 4, SetValues(5, 7))
      val block = blocks.createBlock("Winter Arc", today.minusWeeks(1), today.plusWeeks(3))
      blocks.addWorkouts(block, listOf(push))
      blocks.setDay(block, today.dayOfWeek, push)
    }

    waitForTag("today_workout")
    compose.onNodeWithText("Push A").assertIsDisplayed()
    compose.onNodeWithText("Winter Arc · week 2 of 5").assertIsDisplayed()
    compose.onNodeWithText("4 × 5–7").assertIsDisplayed()

    compose.onNodeWithTag("open_today_workout").performClick()
    waitForText("1. Bench press")
  }

  @Test
  fun createBlock_addWorkouts_andScheduleThem() {
    runBlocking {
      workouts.createWorkout("Push A", "Push")
      workouts.createWorkout("Pull A", "Pull")
    }

    compose.onNodeWithTag("open_blocks").performClick()
    waitForTag("add_block")
    compose.onNodeWithTag("add_block").performClick()
    compose.onNodeWithTag("block_name").performTextReplacement("Intro")
    compose.onNodeWithTag("length_M4").performClick()
    compose.onNodeWithTag("confirm_block").performClick()

    // The new block opens in its editor: all rest, no workouts yet.
    waitForTag("add_block_workouts")
    compose.onNodeWithText("Workouts (0 of 10)").assertIsDisplayed()
    compose.onNodeWithTag("add_block_workouts").performClick()
    compose.onNodeWithTag("pick_Push A").performClick()
    compose.onNodeWithTag("pick_Pull A").performClick()
    compose.onNodeWithTag("confirm_add_workouts").performClick()
    waitForText("Workouts (2 of 10)")

    // Tuesday → Push A.
    compose.onNodeWithTag("block_content").performScrollToNode(hasTestTag("day_TUESDAY"))
    compose.onNodeWithTag("day_TUESDAY").performClick()
    waitForTag("choose_Push A")
    compose.onNodeWithTag("choose_Push A").performClick()
    waitUntilDayShows("TUESDAY", "Push A")

    // Removing Push A from the block asks first, then makes Tuesday a rest day.
    compose.onNodeWithTag("block_content").performScrollToNode(hasTestTag("remove_Push A"))
    compose.onNodeWithTag("remove_Push A").performClick()
    waitForTag("confirm_remove")
    compose.onNodeWithTag("confirm_remove").performClick()
    waitForText("Workouts (1 of 10)")
    compose.onNodeWithTag("block_content").performScrollToNode(hasTestTag("day_TUESDAY"))
    waitUntilDayShows("TUESDAY", "Rest")
  }
}
