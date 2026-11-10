package com.openweight.app.progress

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.openweight.app.MainActivity
import com.openweight.app.core.TimeAndIds
import com.openweight.app.progress.strength.StrengthStandard
import com.openweight.app.settings.SettingsRepository
import com.openweight.app.workout.data.SetValues
import com.openweight.app.workout.data.WorkoutRepository
import com.openweight.app.workout.session.SessionRepository
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Workout tab → Power level: asks for a weigh-in, then shows tiers for the lifts and muscles trained. */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, qualifiers = "w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PowerLevelFlowTest {
  @get:Rule(order = 0) val hiltRule = HiltAndroidRule(this)

  @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()

  @Inject lateinit var workouts: WorkoutRepository
  @Inject lateinit var sessions: SessionRepository
  @Inject lateinit var bodyWeights: BodyWeightRepository
  @Inject lateinit var settings: SettingsRepository
  @Inject lateinit var clock: TimeAndIds

  /** Name, weight (null = bodyweight), reps. */
  private val lifts =
    listOf(
      Triple("Barbell back squat", 120.0, 5),
      Triple("Barbell bench press", 90.0, 5),
      Triple("Conventional deadlift", 160.0, 5),
      Triple("Overhead press", 55.0, 5),
      Triple("Barbell row", 80.0, 8),
      Triple("Pull-up", null, 10),
      Triple("Barbell curl", 40.0, 8),
      Triple("Hanging leg raise", null, 12),
      Triple("Hip thrust", 140.0, 8),
      Triple("Standing calf raise", 100.0, 12),
      Triple("Romanian deadlift", 110.0, 8),
      Triple("Cable triceps pushdown", 40.0, 12),
      Triple("Dumbbell lateral raise", 12.0, 15),
      Triple("Plank", null, 60),
    )

  @Before
  fun setUp() {
    hiltRule.inject()
    runBlocking {
      val w = workouts.createWorkout("Full body", "")
      for ((name, kg, reps) in lifts) workouts.addExercise(w, workouts.saveExercise(null, name, "", "", ""), 1, SetValues(reps, null, kg, 90))
      val session = sessions.start(w, clock.today().minusDays(1))
      for (exercise in sessions.open().first()!!.exercises) {
        val (_, kg, reps) = lifts.first { it.first == exercise.name }
        sessions.confirmSet(exercise.sets[0].id, kg, reps)
      }
      sessions.finish(session)
    }
  }

  private fun waitForTag(tag: String) = compose.waitUntil(5_000) { compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }

  private fun openPowerLevel() {
    compose.onNodeWithTag("tab_WORKOUT").performClick()
    waitForTag("open_power_level")
    compose.onNodeWithTag("open_power_level").performClick()
  }

  @Test
  fun withoutAWeighIn_itAsksForOne() {
    openPowerLevel()
    waitForTag("power_needs_weight")
    compose.onNodeWithTag("power_log_weight").performClick()
    waitForTag("weight_input")
  }

  @Test
  fun withAWeighIn_showsTheOverallTier_bodyMap_musclesAndLifts() {
    runBlocking { bodyWeights.log(clock.today(), 80.0) }
    openPowerLevel()
    waitForTag("power_overall")
    waitForTag("power_body")
    screenshot("power-level-top")
    compose.onNodeWithTag("power_level").performScrollToNode(hasTestTag("muscle_QUADS"))
    compose.onNodeWithTag("power_level").performScrollToNode(hasTestTag("lift_Barbell back squat"))
    screenshot("power-level-lifts")
    compose.onNodeWithTag("power_level").performScrollToNode(hasTestTag("power_unscored"))

    // Women's standards: the same lifts reach higher tiers.
    runBlocking { settings.setStrengthStandard(StrengthStandard.WOMEN) }
    compose.onNodeWithTag("power_level").performScrollToNode(hasTestTag("power_overall"))
    screenshot("power-level-women")
  }

  /** Saves the screen for a look (build/screenshots); a no-op where images can't be captured. */
  private fun screenshot(name: String) {
    compose.waitForIdle()
    val bitmap = runCatching { compose.onRoot().captureToImage().asAndroidBitmap() }.getOrNull() ?: return
    val dir = File("build/screenshots").apply { mkdirs() }
    File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
  }
}
