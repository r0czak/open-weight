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
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import android.os.Looper
import com.openweight.app.core.model.ThemeMode
import com.openweight.app.core.model.WeightUnit
import org.robolectric.Shadows.shadowOf
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
  }

  /** The lift row's merged text (badge, name, tier line, next goal). */
  private fun liftRow(name: String) = compose.onNodeWithTag("lift_$name")

  private fun scrollToLift(name: String) = compose.onNodeWithTag("power_level").performScrollToNode(hasTestTag("lift_$name"))

  /** The node says [text], itself or in a child (a card doesn't merge its texts; a clickable row does). */
  private fun says(text: String) = hasText(text, substring = true) or hasAnyDescendant(hasText(text, substring = true))

  private fun waitForText(tag: String, text: String) =
    compose.waitUntil(5_000) { runCatching { compose.onNodeWithTag(tag).assert(says(text)) }.isSuccess }

  @Test
  fun choosingWomensStandards_inSettings_raisesTheTiersShown() {
    runBlocking { bodyWeights.log(clock.today(), 80.0) }
    openPowerLevel()
    waitForTag("power_overall")
    // Bench 90 kg × 5 ≈ 105 kg = 1.31 × 80 kg: Advanced for men (1.25–1.6).
    scrollToLift("Barbell bench press")
    liftRow("Barbell bench press").assert(says("Advanced · 90 kg × 5"))
    compose.onNodeWithTag("power_level").performScrollToNode(hasTestTag("power_overall"))
    compose.onNodeWithTag("power_overall").assert(says("men's standards"))

    compose.onNodeWithTag("tab_SETTINGS").performClick()
    waitForTag("rest_alerts")
    compose.onNodeWithText("Women").performScrollTo().performClick()
    compose.waitUntil(5_000) {
      shadowOf(Looper.getMainLooper()).idle()
      runBlocking { settings.settings.first().strengthStandard == StrengthStandard.WOMEN }
    }

    // Back on Workout, the power level follows the setting: women's bench thresholds are 0.6 × the men's,
    // so 1.31 × body weight is past where World class starts (1.2).
    compose.onNodeWithTag("tab_WORKOUT").performClick()
    waitForText("power_overall", "women's standards")
    scrollToLift("Barbell bench press")
    liftRow("Barbell bench press").assert(says("World class · 90 kg × 5"))
    liftRow("Barbell bench press").assert(says("Top tier reached"))
  }

  @Test
  fun inPounds_bestSetsEstimatesAndGoals_showInPounds() {
    runBlocking {
      bodyWeights.log(clock.today(), 80.0)
      settings.setWeightUnit(WeightUnit.LB)
      settings.setThemeMode(ThemeMode.DARK)
    }
    openPowerLevel()
    waitForTag("power_overall")
    compose.onNodeWithTag("power_overall").assert(says("against 176.4 lb body weight"))
    screenshot("power-level-dark")
    scrollToLift("Barbell bench press")
    // 90 kg = 198.4 lb; its estimate, 105 kg, is 231 lb; Elite starts at 1.6 × 80 kg = 128 kg = 282 lb.
    liftRow("Barbell bench press").assert(says("198.4 lb × 5 ≈ 231 lb 1RM (1.31× body weight)"))
    liftRow("Barbell bench press").assert(says("Next: Elite at a 282 lb 1RM"))
    // Body-weight lifts give their goal in reps, whatever the unit: pull-ups 10 reps = 1.33 ×, Advanced at 1.4 × → 12 reps.
    scrollToLift("Pull-up")
    liftRow("Pull-up").assert(says("Bodyweight × 10"))
    liftRow("Pull-up").assert(says("Next: Advanced at 12 reps"))
  }

  /** Saves the screen for a look (build/screenshots); a no-op where images can't be captured. */
  private fun screenshot(name: String) {
    compose.waitForIdle()
    val bitmap = runCatching { compose.onRoot().captureToImage().asAndroidBitmap() }.getOrNull() ?: return
    val dir = File("build/screenshots").apply { mkdirs() }
    File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
  }
}
