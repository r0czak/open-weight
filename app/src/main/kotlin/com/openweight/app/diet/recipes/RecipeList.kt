package com.openweight.app.diet.recipes

import com.openweight.app.diet.PanelHeader
import com.openweight.app.diet.panelHeader

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.openweight.app.R
import com.openweight.app.core.ui.CardListItem
import com.openweight.app.core.ui.MenuAccent
import com.openweight.app.core.ui.ShortcutCard
import com.openweight.app.core.ui.EmptyState
import com.openweight.app.diet.data.DietRepository
import com.openweight.app.diet.data.RecipeSummary
import com.openweight.app.diet.formatKcal
import com.openweight.app.workout.exercise.SearchField
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class RecipeListState(
  val loading: Boolean = true,
  val query: String = "",
  val items: List<RecipeSummary> = emptyList(),
  val total: Int = 0,
  val ingredientCount: Int = 0,
)

@HiltViewModel
class RecipeListViewModel @Inject constructor(repository: DietRepository) : ViewModel() {
  private val query = MutableStateFlow("")

  val state: StateFlow<RecipeListState> =
    combine(query, repository.recipes(), repository.ingredients()) { q, all, ingredients ->
        val t = q.trim()
        RecipeListState(
          loading = false,
          query = q,
          items = all.filter { t.isEmpty() || it.name.contains(t, true) || it.tags.contains(t, true) },
          total = all.size,
          ingredientCount = ingredients.size,
        )
      }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RecipeListState())

  fun onQueryChange(v: String) {
    query.value = v
  }
}

/** The "Meals & recipes" panel of the Diet tab: ingredients entry, search, recipe list. */
@Composable
fun RecipesPanel(
  onOpenRecipe: (String) -> Unit,
  onOpenIngredients: () -> Unit,
  header: PanelHeader? = null,
  viewModel: RecipeListViewModel = hiltViewModel(),
) {
  val state by viewModel.state.collectAsStateWithLifecycle()
  LazyColumn(Modifier.fillMaxSize().testTag("recipe_list"), contentPadding = PaddingValues(bottom = 88.dp)) {
    panelHeader(header)
    item(key = "ingredients") {
      ShortcutCard(
        R.drawable.ic_menu_ingredients,
        stringResource(R.string.diet_ingredients),
        pluralStringResource(R.plurals.ingredient_count, state.ingredientCount, state.ingredientCount),
        onClick = onOpenIngredients,
        tag = "open_ingredients",
        accent = MenuAccent.GREEN,
      )
    }
    item(key = "search") { SearchField(state.query, viewModel::onQueryChange, placeholder = stringResource(R.string.recipe_search)) }
    when {
      state.loading -> Unit
      state.total == 0 -> item(key = "empty") { EmptyState(stringResource(R.string.recipe_empty), hint = stringResource(R.string.recipe_empty_hint)) }
      state.items.isEmpty() -> item(key = "none") { EmptyState(stringResource(R.string.search_no_results, state.query)) }
      else ->
        items(state.items, key = { it.id }) { r ->
          CardListItem(state.items.indexOf(r), state.items.size) {
            ListItem(
              modifier = Modifier.clickable { onOpenRecipe(r.id) },
              headlineContent = { Text(r.name) },
              supportingContent = {
                val parts = buildList {
                  add(pluralStringResource(R.plurals.serving_count, r.servings, r.servings))
                  r.prepMinutes?.let { add(stringResource(R.string.recipe_minutes, it)) }
                  if (r.tags.isNotBlank()) add(r.tags)
                }
                Text(parts.joinToString(" · "))
              },
              trailingContent = {
                if (r.ingredientCount > 0) {
                  Text(
                    stringResource(R.string.recipe_kcal_per_serving, formatKcal(r.nutrition.perServing.kcal)) +
                      if (r.nutrition.complete) "" else "*",
                    style = MaterialTheme.typography.labelLarge,
                  )
                }
              },
            )
          }
        }
    }
  }
}
