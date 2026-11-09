package com.openweight.app.diet

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.openweight.app.R
import com.openweight.app.core.ui.EmptyState
import com.openweight.app.core.ui.ScreenScaffold
import com.openweight.app.core.model.MealSlot
import com.openweight.app.diet.calendar.CalendarPanel
import com.openweight.app.diet.recipes.RecipesPanel
import java.time.LocalDate
import com.openweight.app.core.ui.theme.OpenWeightTheme

/** The three diet panels from the spec. */
enum class DietPanel(val title: Int, val emptyMessage: Int) {
  RECIPES(R.string.diet_recipes, R.string.diet_recipes_empty),
  CALENDAR(R.string.diet_calendar, R.string.diet_calendar_empty),
  SHOPPING(R.string.diet_shopping, R.string.diet_shopping_empty),
}

@Composable
fun DietHomeScreen(
  modifier: Modifier = Modifier,
  onOpenRecipe: (String) -> Unit = {},
  onCreateRecipe: () -> Unit = {},
  onOpenIngredients: () -> Unit = {},
  onAddMeal: (LocalDate, MealSlot) -> Unit = { _, _ -> },
  onOpenPlans: () -> Unit = {},
) {
  var selected by rememberSaveable { mutableIntStateOf(0) }
  val snackbar = remember { SnackbarHostState() }
  ScreenScaffold(
    title = stringResource(R.string.tab_diet),
    modifier = modifier,
    snackbarHostState = snackbar,
    floatingActionButton = {
      if (DietPanel.entries[selected] == DietPanel.RECIPES) {
        FloatingActionButton(onClick = onCreateRecipe, modifier = Modifier.testTag("add_recipe")) {
          Icon(Icons.Filled.Add, stringResource(R.string.recipe_new))
        }
      }
    },
  ) { padding ->
    Column(Modifier.fillMaxSize().padding(padding)) {
      PrimaryTabRow(selectedTabIndex = selected) {
        DietPanel.entries.forEachIndexed { index, panel ->
          Tab(
            modifier = Modifier.testTag("panel_${panel.name}"),
            selected = selected == index,
            onClick = { selected = index },
            text = { Text(stringResource(panel.title)) },
          )
        }
      }
      val panel = DietPanel.entries[selected]
      when (panel) {
        DietPanel.RECIPES -> RecipesPanel(onOpenRecipe = onOpenRecipe, onOpenIngredients = onOpenIngredients)
        DietPanel.CALENDAR ->
          CalendarPanel(snackbar = snackbar, onAddMeal = onAddMeal, onOpenRecipe = onOpenRecipe, onOpenPlans = onOpenPlans)
        DietPanel.SHOPPING ->
          EmptyState(
            message = stringResource(panel.emptyMessage),
            hint = stringResource(R.string.coming_soon),
          )
      }
    }
  }
}

@Preview(showBackground = true)
@Composable
private fun DietHomeScreenPreview() {
  OpenWeightTheme { DietHomeScreen() }
}
