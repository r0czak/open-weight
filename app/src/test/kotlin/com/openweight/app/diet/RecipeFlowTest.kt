package com.openweight.app.diet

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import com.openweight.app.testing.scrollToText
import com.openweight.app.MainActivity
import com.openweight.app.core.model.QuantityUnit
import com.openweight.app.diet.data.DietRepository
import com.openweight.app.diet.data.IngredientValues
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

/** Diet tab: create a recipe, add an ingredient with an amount, see calories per serving. */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class RecipeFlowTest {
  @get:Rule(order = 0) val hiltRule = HiltAndroidRule(this)

  @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()

  @Inject lateinit var diet: DietRepository

  @Before
  fun setUp() {
    hiltRule.inject()
    runBlocking { diet.saveIngredient(null, IngredientValues("Oats", "Grains & pasta", QuantityUnit.G, 379.0, 13.2, 67.7, 6.5)) }
  }

  private fun waitForTag(tag: String) =
    compose.waitUntil(5_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }

  private fun waitForText(text: String) =
    compose.waitUntil(5_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }

  private fun waitUntilGone(tag: String) =
    compose.waitUntil(5_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isEmpty() }

  /** Screens slide in: wait until the text is really on screen, not just composed. */
  private fun waitUntilDisplayed(text: String) =
    compose.waitUntil(5_000) { runCatching { compose.scrollToText(text); runCatching { compose.onNodeWithText(text).performScrollTo() }; compose.onNodeWithText(text).assertIsDisplayed() }.isSuccess }

  @Test
  fun createRecipe_addIngredient_seeCaloriesPerServing() {
    compose.onNodeWithTag("tab_DIET").performClick()
    waitForTag("add_recipe")
    compose.onNodeWithTag("add_recipe").performClick()

    // A new recipe saves first, then takes ingredients.
    waitForTag("recipe_name")
    compose.onNodeWithTag("recipe_name").performTextReplacement("Overnight oats")
    compose.onNodeWithTag("recipe_servings").performTextReplacement("1")
    compose.onNodeWithTag("save_recipe").performClick()
    // The ingredients section appears once saved; it sits below the fields.
    compose.waitUntil(5_000) {
      runCatching { compose.onNodeWithTag("recipe_form").performScrollToNode(hasTestTag("add_recipe_ingredient")) }.isSuccess
    }

    compose.onNodeWithTag("add_recipe_ingredient").performClick()
    waitForText("Oats")
    compose.onNodeWithText("Oats").performClick()
    compose.onNodeWithTag("quantity").performTextReplacement("80")
    compose.onNodeWithTag("confirm_quantity").performClick()

    compose.waitUntil(5_000) {
      runCatching { compose.onNodeWithTag("recipe_form").performScrollToNode(hasTestTag("item_Oats")) }.isSuccess
    }
    compose.onNodeWithText("80 g · 303 kcal").assertIsDisplayed()
    compose.onNodeWithTag("save_recipe").performClick()

    // Back on the Diet tab: the recipe with its calories per serving.
    waitUntilGone("recipe_form")
    waitUntilDisplayed("303 kcal")
    compose.onNodeWithText("Overnight oats").performClick()
    waitForTag("macros_card")
    compose.onNodeWithText("Per serving").assertIsDisplayed()
  }
}
