package com.openweight.app.diet

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.openweight.app.R
import com.openweight.app.core.ui.PillTabs
import com.openweight.app.core.ui.ScreenScaffold
import com.openweight.app.core.model.MealSlot
import com.openweight.app.diet.calendar.CalendarPanel
import com.openweight.app.diet.recipes.RecipesPanel
import com.openweight.app.diet.shopping.ShoppingPanel
import java.time.LocalDate
import com.openweight.app.core.ui.theme.OpenWeightTheme

/** The three diet panels from the spec. */
enum class DietPanel(val title: Int) {
  RECIPES(R.string.diet_recipes),
  CALENDAR(R.string.diet_calendar),
  SHOPPING(R.string.diet_shopping),
}

@Composable
fun DietHomeScreen(
  modifier: Modifier = Modifier,
  onOpenRecipe: (String) -> Unit = {},
  onCreateRecipe: () -> Unit = {},
  onOpenIngredients: () -> Unit = {},
  onAddMeal: (LocalDate, MealSlot) -> Unit = { _, _ -> },
  onSwapMeal: (mealId: String, date: LocalDate, slot: MealSlot) -> Unit = { _, _, _ -> },
  onOpenPlans: () -> Unit = {},
) {
  var selected by rememberSaveable { mutableIntStateOf(0) }
  var addingShopItem by rememberSaveable { mutableStateOf(false) }
  val snackbar = remember { SnackbarHostState() }
  ScreenScaffold(
    title = stringResource(R.string.tab_diet),
    modifier = modifier,
    snackbarHostState = snackbar,
    floatingActionButton = {
      when (DietPanel.entries[selected]) {
        DietPanel.RECIPES ->
          FloatingActionButton(onClick = onCreateRecipe, modifier = Modifier.testTag("add_recipe")) {
            Icon(Icons.Filled.Add, stringResource(R.string.recipe_new))
          }
        DietPanel.SHOPPING ->
          FloatingActionButton(onClick = { addingShopItem = true }, modifier = Modifier.testTag("add_shopping_item")) {
            Icon(Icons.Filled.Add, stringResource(R.string.shopping_add_item))
          }
        DietPanel.CALENDAR -> Unit
      }
    },
  ) { padding ->
    Column(Modifier.fillMaxSize().padding(padding)) {
      PillTabs(
        labels = DietPanel.entries.map { stringResource(it.title) },
        selected = selected,
        onSelect = { selected = it },
        tags = DietPanel.entries.map { "panel_${it.name}" },
      )
      when (DietPanel.entries[selected]) {
        DietPanel.RECIPES -> RecipesPanel(onOpenRecipe = onOpenRecipe, onOpenIngredients = onOpenIngredients)
        DietPanel.CALENDAR ->
          CalendarPanel(snackbar = snackbar, onAddMeal = onAddMeal, onOpenRecipe = onOpenRecipe, onOpenPlans = onOpenPlans, onSwapMeal = onSwapMeal)
        DietPanel.SHOPPING -> ShoppingPanel(snackbar = snackbar, adding = addingShopItem, onAddingDone = { addingShopItem = false })
      }
    }
  }
}

@Preview(showBackground = true)
@Composable
private fun DietHomeScreenPreview() {
  OpenWeightTheme { DietHomeScreen() }
}
