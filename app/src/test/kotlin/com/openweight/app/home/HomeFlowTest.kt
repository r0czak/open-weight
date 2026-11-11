package com.openweight.app.home

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.openweight.app.MainActivity
import com.openweight.app.core.TimeAndIds
import com.openweight.app.workout.blocks.TrainingBlockRepository
import com.openweight.app.workout.data.SetValues
import com.openweight.app.workout.data.WorkoutRepository
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

/** Home week view against the real app: today's workout, opening it, moving between weeks. */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class HomeFlowTest {
  @get:Rule(order = 0) val hiltRule = HiltAndroidRule(this)

  @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()

  @Inject lateinit var workouts: WorkoutRepository
  @Inject lateinit var blocks: TrainingBlockRepository
  @Inject lateinit var clock: TimeAndIds

  private lateinit var today: LocalDate

  @Before
  fun setUp() {
    hiltRule.inject()
    today = clock.today()
    runBlocking {
      val bench = workouts.saveExercise(null, "Bench press", "Chest", "Barbell", "")
      val push = workouts.createWorkout("Push A", "Push")
      workouts.addExercise(push, bench, 4, SetValues(5, 7, 80.0, 150))
      val block = blocks.createBlock("Winter Arc", today.minusWeeks(1), today.plusWeeks(3))
      blocks.addWorkouts(block, listOf(push))
      blocks.setDay(block, today.dayOfWeek, push)
    }
  }

  private fun waitForTag(tag: String) =
    compose.waitUntil(5_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }

  /** Brings a node of the Home list into view (the hero sits above the week; days below compose lazily). */
  private fun reveal(tag: String) =
    compose.waitUntil(5_000) { runCatching { compose.onNodeWithTag("week_agenda").performScrollToNode(hasTestTag(tag)) }.isSuccess }

  private fun waitForText(text: String) =
    compose.waitUntil(5_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }

  @Test
  fun todaysWorkout_opensItsOverview_thenTheEditor() {
    waitForTag("week_agenda")
    // Today's card sits under the hero and the week strip: scroll to it. Every day card of the
    // block names its week, so any of them will do.
    compose.waitUntil(5_000) {
      runCatching {
          compose.onNodeWithTag("week_agenda").performScrollToNode(hasTestTag("day_workout_$today"))
          compose.onAllNodesWithText("Winter Arc · week 2 of 5")[0].assertIsDisplayed()
        }
        .isSuccess
    }

    compose.onNodeWithTag("day_workout_$today").performClick()
    waitForText("1. Bench press")
    compose.waitUntil(5_000) { compose.onAllNodesWithText("80 kg").fetchSemanticsNodes().size == 4 }
    compose.onNodeWithTag("start_workout").assertIsEnabled()

    compose.onNodeWithTag("edit_workout").performClick()
    waitForTag("add_exercise_to_workout") // the editor
  }

  @Test
  fun weekArrows_moveAWeek_andTodayComesBack() {
    reveal("strip_$today")
    val nextMonday = WeekPlan.mondayOf(today).plusWeeks(1)

    reveal("next_week")
    compose.onNodeWithTag("next_week").performClick()
    reveal("strip_$nextMonday")
    // The same weekday next week is planned too: the block runs 3 more weeks.
    reveal("day_workout_${today.plusWeeks(1)}")

    reveal("this_week")
    compose.onNodeWithTag("this_week").performClick()
    reveal("strip_$today")
  }
}
