package com.openweight.app.workout.session

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.openweight.app.MainActivity
import com.openweight.app.workout.data.SetValues
import com.openweight.app.workout.data.WorkoutRepository
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
import org.robolectric.annotation.Config

/** History → session → exercise progress; and "last time" with the step-up hint while training. */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class HistoryFlowTest {
  @get:Rule(order = 0) val hiltRule = HiltAndroidRule(this)

  @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()

  @Inject lateinit var workouts: WorkoutRepository
  @Inject lateinit var sessions: SessionRepository

  private lateinit var pushA: String

  @Before
  fun setUp() {
    hiltRule.inject()
    runBlocking {
      val bench = workouts.saveExercise(null, "Bench press", "Chest", "Barbell", "")
      pushA = workouts.createWorkout("Push A", "Push")
      workouts.addExercise(pushA, bench, 2, SetValues(8, 10, 60.0, 0))
      // Two finished sessions: 60 kg, then 65 kg with every set at the top of the range.
      for ((day, kg) in listOf(LocalDate.of(2026, 11, 2) to 60.0, LocalDate.of(2026, 11, 5) to 65.0)) {
        val id = sessions.start(pushA, day)
        sessions.open().first()!!.exercises[0].sets.forEach { sessions.confirmSet(it.id, kg, 10) }
        sessions.finish(id)
      }
    }
  }

  private fun waitForTag(tag: String) = compose.waitUntil(5_000) { compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }

  @Test
  fun history_session_andExerciseProgress() {
    compose.onNodeWithTag("tab_WORKOUT").performClick()
    waitForTag("open_history")
    compose.onNodeWithTag("open_history").performClick()
    waitForTag("history_list")
    compose.onNodeWithText("Thu 5 Nov", substring = true).assertIsDisplayed()

    val newest = runBlocking { sessions.history().first().first().id }
    compose.onNodeWithTag("history_$newest").performClick()
    waitForTag("session_detail")
    compose.onNodeWithText("Set 1: 65 kg × 10").assertIsDisplayed()

    compose.onNodeWithTag("detail_exercise_0").performClick()
    waitForTag("exercise_chart")
    compose.onNodeWithTag("exercise_best", useUnmergedTree = true).assertTextEquals("Best set: 65 kg × 10")

    // Deleting the session from its page removes it from the history.
    compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
    waitForTag("session_detail_menu")
    compose.onNodeWithTag("session_detail_menu").performClick()
    compose.onNodeWithTag("delete_session").performClick()
    compose.onNodeWithTag("confirm_delete_session").performClick()
    compose.waitUntil(5_000) { runBlocking { sessions.history().first().size == 1 } }
  }

  @Test
  fun training_showsLastTime_andTheStepUp_whichFillsTheRemainingSets() {
    val id = runBlocking { sessions.start(pushA, null) }
    waitForTag("resume_session")
    compose.onNodeWithTag("resume_session").performClick()
    waitForTag("last_time_0")
    compose.onNodeWithTag("last_time_0", useUnmergedTree = true).assertTextEquals("Last time: 65 kg × 10, 65 kg × 10")
    compose.onNodeWithTag("step_up_0", useUnmergedTree = true).assertTextEquals("Every set reached the top of the range: try 67.5 kg")

    compose.onNodeWithTag("use_step_0").performClick()
    compose.waitUntil(5_000) { runBlocking { sessions.session(id).first()!!.exercises[0].sets.all { it.weightKg == 67.5 } } }
    compose.waitUntil(5_000) { compose.onAllNodesWithTag("step_up_0", useUnmergedTree = true).fetchSemanticsNodes().isEmpty() }
    assertEquals(0, compose.onAllNodesWithTag("use_step_0").fetchSemanticsNodes().size)
  }
}
