package com.openweight.app.diet

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import com.openweight.app.testing.scrollToText
import com.openweight.app.MainActivity
import com.openweight.app.core.TimeAndIds
import com.openweight.app.core.model.MealSlot
import com.openweight.app.core.model.QuantityUnit
import com.openweight.app.diet.data.DietRepository
import com.openweight.app.diet.data.IngredientValues
import com.openweight.app.diet.data.MealPlanRepository
import com.openweight.app.diet.data.RecipeInfo
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

/** Calendar meals → shopping list for the next 7 days → tick, change an amount, add by hand, update. */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class ShoppingFlowTest {
  @get:Rule(order = 0) val hiltRule = HiltAndroidRule(this)

  @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()

  @Inject lateinit var diet: DietRepository
  @Inject lateinit var meals: MealPlanRepository
  @Inject lateinit var clock: TimeAndIds

  private lateinit var oats: String

  @Before
  fun setUp() {
    hiltRule.inject()
    runBlocking {
      val o = diet.saveIngredient(null, IngredientValues("Oats", "Grains & pasta", QuantityUnit.G))
      val m = diet.saveIngredient(null, IngredientValues("Milk", "Dairy & eggs", QuantityUnit.ML))
      oats = diet.createRecipe(RecipeInfo("Overnight oats", 1, 5, "", "", ""))
      diet.addItem(oats, o, 80.0, QuantityUnit.G)
      diet.addItem(oats, m, 200.0, QuantityUnit.ML)
      (0..6L).forEach { meals.addMeal(clock.today().plusDays(it), MealSlot.BREAKFAST, oats, 1.0) }
    }
  }

  private fun waitForTag(tag: String) =
    compose.waitUntil(5_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }

  private fun waitUntilDisplayed(text: String) =
    compose.waitUntil(5_000) { runCatching { compose.scrollToText(text, substring = true); runCatching { compose.onNodeWithText(text, substring = true).performScrollTo() }; compose.onNodeWithText(text, substring = true).assertIsDisplayed() }.isSuccess }

  private fun scrollTo(tag: String) =
    compose.waitUntil(5_000) { runCatching { compose.onNodeWithTag("shopping").performScrollToNode(hasTestTag(tag)) }.isSuccess }

  private fun progress(text: String) {
    scrollTo("shopping_progress")
    // Changes are saved in the background; wait for the list to show them.
    compose.waitUntil(5_000) { runCatching { compose.onNodeWithTag("shopping_progress").assertTextEquals(text) }.isSuccess }
  }

  private fun assertQty(name: String, text: String) =
    compose.waitUntil(5_000) { runCatching { compose.onNodeWithTag("shop_qty_$name", useUnmergedTree = true).assertTextEquals(text) }.isSuccess }

  @Test
  fun makeList_tick_editAmount_addByHand_thenUpdate() {
    compose.onNodeWithTag("tab_DIET").performClick()
    waitForTag("panel_SHOPPING")
    compose.onNodeWithTag("panel_SHOPPING").performClick()

    // No list yet: make one for the next 7 days. 7 × 80 g oats, 7 × 200 ml milk.
    waitForTag("shopping_next_week")
    compose.onNodeWithTag("shopping_next_week").performScrollTo().performClick()
    waitUntilDisplayed("List updated (new: 2,")
    scrollTo("shop_Oats")
    assertQty("Oats", "560 g")
    scrollTo("shop_Milk")
    assertQty("Milk", "1.4 l")
    progress("Ticked: 0/2")

    // Tick oats; buy a whole 2 l of milk.
    scrollTo("shop_Oats")
    // The "List updated" snackbar can cover the row on this small screen: click through semantics.
    compose.onNodeWithTag("shop_Oats").performSemanticsAction(SemanticsActions.OnClick)
    progress("Ticked: 1/2")
    scrollTo("shop_Milk")
    compose.onNodeWithTag("shop_edit_Milk", useUnmergedTree = true).performSemanticsAction(SemanticsActions.OnClick)
    compose.onNodeWithTag("shop_item_amount").performTextReplacement("2")
    compose.onNodeWithTag("shop_item_save").performClick()
    compose.waitForIdle()
    scrollTo("shop_Milk")
    assertQty("Milk", "2 l")
    compose.onNodeWithText("Amount changed by you").assertIsDisplayed()

    // Add coffee by hand, in a new category.
    compose.onNodeWithTag("add_shopping_item").performClick()
    compose.onNodeWithTag("shop_item_name").performTextReplacement("Coffee")
    compose.onNodeWithTag("shop_item_category").performTextReplacement("Drinks")
    compose.onNodeWithTag("shop_item_save").performClick()
    scrollTo("shop_Coffee")
    compose.onNodeWithText("Drinks").assertIsDisplayed()
    compose.onNodeWithText("Added by you").assertIsDisplayed()

    // One more breakfast, then Update: oats go up; the tick, the milk amount and coffee stay.
    runBlocking { meals.addMeal(clock.today(), MealSlot.SNACK, oats, 1.0) }
    scrollTo("shopping_update")
    compose.onNodeWithTag("shopping_update").performClick()
    waitUntilDisplayed("1 amount you changed was kept.")
    progress("Ticked: 1/3")
    scrollTo("shop_Oats")
    assertQty("Oats", "640 g")
    compose.onNodeWithTag("shop_Oats").assertIsOn()
    scrollTo("shop_Milk")
    assertQty("Milk", "2 l")
  }
}
