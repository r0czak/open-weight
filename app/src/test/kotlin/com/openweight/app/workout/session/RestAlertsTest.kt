package com.openweight.app.workout.session

import android.Manifest
import android.app.AlarmManager
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Looper
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.openweight.app.MainActivity
import com.openweight.app.settings.SettingsRepository
import com.openweight.app.workout.data.SetValues
import com.openweight.app.workout.data.WorkoutRepository
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** The "rest is over" alarm follows the session, and its notification names the next set. */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class RestAlertsTest {
  @get:Rule(order = 0) val hiltRule = HiltAndroidRule(this)

  @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()

  @Inject lateinit var workouts: WorkoutRepository
  @Inject lateinit var sessions: SessionRepository
  @Inject lateinit var settings: SettingsRepository

  private val context: Context
    get() = ApplicationProvider.getApplicationContext()
  private val alarms
    get() = shadowOf(context.getSystemService(AlarmManager::class.java))
  private lateinit var sessionId: String

  @Before
  fun setUp() {
    hiltRule.inject()
    shadowOf(context as Application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
    sessionId = runBlocking {
      val bench = workouts.saveExercise(null, "Bench press", "", "", "")
      val w = workouts.createWorkout("Push A", "")
      workouts.addExercise(w, bench, 2, SetValues(8, 10, 60.0, 90))
      sessions.start(w, null)
    }
    compose.runOnUiThread { compose.activity.onBackPressedDispatcher } // activity up
  }

  private fun openSession() {
    compose.onNodeWithTag("resume_session").performClick()
    compose.waitUntil(5_000) { compose.onAllNodesWithTag("session_progress").fetchSemanticsNodes().isNotEmpty() }
  }

  /**
   * The view model sets the alarm on the main thread after the database reports the change, so
   * let the main thread run between checks (waitUntil alone may not).
   */
  private fun waitForAlarm(present: Boolean) =
    compose.waitUntil(5_000) {
      shadowOf(Looper.getMainLooper()).idle()
      (alarms.peekNextScheduledAlarm() != null) == present
    }

  @Test
  fun confirmingASet_schedulesTheAlert_atTheEndOfItsRest_andSkipCancelsIt() {
    compose.waitUntil(5_000) { compose.onAllNodesWithTag("resume_session").fetchSemanticsNodes().isNotEmpty() }
    openSession()
    compose.onNodeWithTag("session_check_0_1", useUnmergedTree = true).performClick()
    compose.waitUntil(5_000) { runBlocking { sessions.open().first()!!.doneCount == 1 } }
    compose.waitForIdle()

    waitForAlarm(true)
    val done = runBlocking { sessions.open().first()!!.exercises[0].sets[0] }
    assertEquals(done.completedAt!!.plusSeconds(90).toEpochMilli(), alarms.peekNextScheduledAlarm()!!.triggerAtTime)

    compose.onNodeWithTag("rest_skip").performClick()
    waitForAlarm(false)
  }

  @Test
  fun settingOff_noAlert() {
    runBlocking { settings.setRestAlerts(false) }
    compose.waitUntil(5_000) { compose.onAllNodesWithTag("resume_session").fetchSemanticsNodes().isNotEmpty() }
    openSession()
    compose.onNodeWithTag("session_check_0_1", useUnmergedTree = true).performClick()
    compose.waitUntil(5_000) { runBlocking { sessions.open().first()!!.doneCount == 1 } }
    compose.waitForIdle()
    shadowOf(Looper.getMainLooper()).idle()
    assertNull(alarms.peekNextScheduledAlarm())
  }

  @Test
  fun theAlarm_postsANotification_namingTheNextSet() {
    RestAlarmReceiver().onReceive(context, Intent().putExtra(RestAlerts.EXTRA_EXERCISE, "Bench press").putExtra(RestAlerts.EXTRA_SET, 2))

    val posted = shadowOf(context.getSystemService(NotificationManager::class.java)).allNotifications.single()
    assertNotNull(posted)
    assertEquals("Rest is over", shadowOf(posted).contentTitle)
    assertEquals("Next: Bench press, set 2", shadowOf(posted).contentText)
  }
}
