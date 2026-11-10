package com.openweight.app.progress

import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import com.openweight.app.MainActivity
import com.openweight.app.core.TimeAndIds
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class BodyWeightTest {
  @get:Rule(order = 0) val hiltRule = HiltAndroidRule(this)

  @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()

  @Inject lateinit var weights: BodyWeightRepository
  @Inject lateinit var clock: TimeAndIds

  private lateinit var today: LocalDate

  @Before
  fun setUp() {
    hiltRule.inject()
    today = clock.today()
  }

  @Test
  fun trend_comparesTheLatestWeekWithTwoWeeksBefore() {
    val mon = LocalDate.of(2026, 11, 2)
    fun w(day: LocalDate, kg: Double) = WeighIn(day.toString(), day, kg)
    val entries = listOf(w(mon.minusWeeks(2), 80.0), w(mon.minusWeeks(2).plusDays(2), 81.0), w(mon.minusWeeks(1), 80.0), w(mon, 79.0), w(mon.plusDays(6), 80.0))

    val t = WeightRules.trend(entries)

    assertEquals(listOf(80.5, 80.0, 79.5), t.weeks.map { it.averageKg })
    assertEquals(-1.0, t.changeKg!!, 1e-9)
    // A missing week two weeks back: no change yet.
    assertNull(WeightRules.trend(entries.drop(2)).changeKg)
  }

  @Test
  fun oneWeighInPerDay_inRange_notInTheFuture(): Unit = runBlocking {
    weights.log(today, 80.0)
    weights.log(today, 79.6)
    assertEquals(listOf(79.6), weights.entries().first().map { it.weightKg })
    assertThrows(IllegalArgumentException::class.java) { runBlocking { weights.log(today.plusDays(1), 80.0) } }
    assertThrows(IllegalArgumentException::class.java) { runBlocking { weights.log(today, Double.NaN) } }
    assertThrows(IllegalArgumentException::class.java) { runBlocking { weights.log(today, 5.0) } }
  }

  @Test
  fun logFromHome_andSeeTheTwoWeekChange() {
    runBlocking { weights.log(today.minusWeeks(2), 82.0) }
    compose.waitUntil(5_000) { compose.onAllNodesWithTag("home_weight").fetchSemanticsNodes().isNotEmpty() }
    compose.onNodeWithTag("home_weight").performClick()
    compose.waitUntil(5_000) { compose.onAllNodesWithTag("weight_input").fetchSemanticsNodes().isNotEmpty() }

    compose.onNodeWithTag("weight_input").performTextReplacement("80,5")
    compose.onNodeWithTag("weight_save").performClick()

    compose.waitUntil(5_000) {
      runCatching { compose.onNodeWithTag("weight_change", useUnmergedTree = true).assertTextEquals("Down 1.5 kg over 2 weeks.") }.isSuccess
    }
    assertEquals(listOf(82.0, 80.5), runBlocking { weights.entries().first().map { it.weightKg } })
  }
}
