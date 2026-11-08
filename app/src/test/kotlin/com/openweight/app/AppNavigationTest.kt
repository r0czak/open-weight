package com.openweight.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Drives the real app (Hilt, database, DataStore) on Robolectric: no emulator needed. */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class AppNavigationTest {
  @get:Rule(order = 0) val hiltRule = HiltAndroidRule(this)

  @get:Rule(order = 1) val composeRule = createAndroidComposeRule<MainActivity>()

  @Before
  fun setUp() {
    hiltRule.inject()
  }

  @Test
  fun startsOnWorkoutTab_withToday() {
    composeRule.onNodeWithTag("tab_WORKOUT").assertIsSelected()
    composeRule.onNodeWithText("Today").assertIsDisplayed()
    composeRule.onNodeWithText("Training blocks").assertIsDisplayed()
  }

  @Test
  fun dietTab_showsItsThreePanels() {
    composeRule.onNodeWithTag("tab_DIET").performClick()

    composeRule.onNodeWithText("Meals & recipes").assertIsDisplayed()
    composeRule.onNodeWithText("Calendar").assertIsDisplayed()
    composeRule.onNodeWithText("Shopping list").performClick()
    composeRule
      .onNodeWithText("Your shopping list will be built from the meals on your calendar.")
      .assertIsDisplayed()
  }

  @Test
  fun settingsTab_changesThemeAndUnit() {
    composeRule.onNodeWithTag("tab_SETTINGS").performClick()

    composeRule.onNodeWithText("Dark").performClick()
    composeRule.onNodeWithText("Pounds (lb)").performClick()

    // Settings are saved asynchronously; the buttons reflect them once stored.
    composeRule.waitUntil(5_000) {
      runCatching {
          composeRule.onNodeWithText("Dark").assertIsSelected()
          composeRule.onNodeWithText("Pounds (lb)").assertIsSelected()
        }
        .isSuccess
    }
  }

  @Test
  fun switchingTabs_keepsEachTabState() {
    composeRule.onNodeWithTag("tab_DIET").performClick()
    composeRule.onNodeWithText("Calendar").performClick()
    composeRule.onNodeWithTag("tab_WORKOUT").performClick()
    composeRule.onNodeWithTag("tab_DIET").performClick()

    composeRule.onNodeWithText("Calendar").assertIsSelected()
  }
}
