package com.openweight.app.workout.session

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
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
import java.time.DayOfWeek
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Home → Start → log sets → rest → finish → the day shows as done; and resume, discard. */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class LiveSessionFlowTest {
  @get:Rule(order = 0) val hiltRule = HiltAndroidRule(this)

  @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()

  @Inject lateinit var workouts: WorkoutRepository
  @Inject lateinit var blocks: TrainingBlockRepository
  @Inject lateinit var sessions: SessionRepository
  @Inject lateinit var clock: TimeAndIds

  private lateinit var today: LocalDate
  private lateinit var pushA: String

  @Before
  fun setUp() {
    hiltRule.inject()
    today = clock.today()
    runBlocking {
      val bench = workouts.saveExercise(null, "Bench press", "Chest", "Barbell", "")
      pushA = workouts.createWorkout("Push A", "Push")
      // No rest after the first two sets, so only the last one starts the countdown.
      val entry = workouts.addExercise(pushA, bench, 3, SetValues(8, 10, 60.0, 0))
      val last = workouts.workout(pushA).first()!!.entries.single { it.id == entry }.sets.last()
      workouts.updateSet(last.id, SetValues(8, 10, 60.0, 120), applyToAll = false)
      val block = blocks.createBlock("Winter", today.minusDays(7), today.plusDays(30))
      blocks.addWorkouts(block, listOf(pushA))
      DayOfWeek.entries.forEach { blocks.setDay(block, it, pushA) }
    }
  }

  private fun waitForTag(tag: String) = compose.waitUntil(5_000) { compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }

  private fun waitForText(tag: String, text: String) =
    compose.waitUntil(5_000) { runCatching { compose.onNodeWithTag(tag, useUnmergedTree = true).assertTextEquals(text) }.isSuccess }

  private fun openTodayAndStart() {
    compose.waitUntil(5_000) { runCatching { compose.onNodeWithTag("week_agenda").performScrollToNode(hasTestTag("day_workout_$today")) }.isSuccess }
    compose.onNodeWithTag("day_workout_$today").performClick()
    waitForTag("start_workout")
    compose.onNodeWithTag("start_workout").performClick()
    waitForTag("session_progress")
  }

  @Test
  fun trainTodaysWorkout_fromHome_toDone() {
    openTodayAndStart()
    compose.onNodeWithTag("session_progress").assertTextEquals("Sets done: 0/3")
    compose.onNodeWithTag("finish_session").assertIsNotEnabled()
    waitForText("session_value_0_1", "60 kg × 8") // the plan's weight, bottom of the rep range

    // Set 1: one tap takes the suggestion. Set 2: correct the numbers first.
    compose.onNodeWithTag("session_check_0_1", useUnmergedTree = true).performClick()
    waitForText("session_progress", "Sets done: 1/3")
    compose.onNodeWithTag("session_set_0_2").performClick()
    compose.onNodeWithTag("log_weight").performTextReplacement("62.5")
    compose.onNodeWithTag("log_reps").performTextReplacement("9")
    compose.onNodeWithTag("log_confirm").performClick()
    waitForText("session_value_0_2", "62.5 kg × 9")
    waitForText("session_progress", "Sets done: 2/3")

    // Leave and come back: Home offers the open session.
    compose.onNodeWithTag("tab_HOME").performClick()
    waitForTag("session_banner")
    compose.onNodeWithText("In progress: Push A").assertIsDisplayed()
    compose.onNodeWithTag("resume_session").performClick()
    waitForText("session_progress", "Sets done: 2/3")

    // Finish with a set left: it asks, then shows the summary.
    compose.onNodeWithTag("finish_session").performClick()
    compose.onNodeWithText("1 set is not done. It is saved as skipped.").assertIsDisplayed()
    compose.onNodeWithTag("confirm_finish").performClick()
    waitForTag("session_summary")
    compose.onNodeWithTag("summary_sets").assertTextEquals("Sets done: 2/3")
    compose.onNodeWithText("Total lifted: 1042.5 kg").assertIsDisplayed()
    compose.onNodeWithTag("summary_done").performClick()

    // Home marks today as done, and nothing is in progress.
    compose.onNodeWithTag("tab_HOME").performClick()
    compose.waitUntil(5_000) { runCatching { compose.onNodeWithTag("week_agenda").performScrollToNode(hasTestTag("day_workout_$today")) }.isSuccess }
    waitForTag("day_done_$today")
    assertEquals(0, compose.onAllNodesWithTag("session_banner").fetchSemanticsNodes().size)
  }

  @Test
  fun lastSet_startsTheRestCountdown_andDiscardDeletesTheSession() {
    openTodayAndStart()
    compose.onNodeWithTag("session_check_0_3", useUnmergedTree = true).performClick()

    // 2 minutes of rest after that set; the bar counts down from timestamps.
    waitForTag("rest_bar")
    compose.waitUntil(5_000) {
      runCatching { compose.onNodeWithTag("rest_text").fetchSemanticsNode().config.toString() }.getOrDefault("").let { "Rest 2:00" in it || "Rest 1:5" in it }
    }
    compose.onNodeWithTag("rest_skip").performClick()
    assertEquals(0, compose.onAllNodesWithTag("rest_bar").fetchSemanticsNodes().size)

    compose.onNodeWithTag("session_menu").performClick()
    compose.onNodeWithTag("discard_session").performClick()
    compose.onNodeWithTag("confirm_discard").performClick()
    waitForTag("start_workout") // back on the overview
    assertNull(runBlocking { sessions.open().first() })
  }
}
