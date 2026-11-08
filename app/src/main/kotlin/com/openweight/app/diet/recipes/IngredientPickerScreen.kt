package com.openweight.app.diet.recipes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.openweight.app.R
import com.openweight.app.core.model.QuantityUnit
import com.openweight.app.core.ui.EmptyState
import com.openweight.app.core.ui.ScreenScaffold
import com.openweight.app.diet.data.DietRepository
import com.openweight.app.diet.data.IngredientListItem
import com.openweight.app.diet.data.IngredientValues
import com.openweight.app.diet.ingredients.matches
import com.openweight.app.workout.exercise.SearchField
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class IngredientPickerState(
  val query: String = "",
  val items: List<IngredientListItem> = emptyList(),
  val exactMatch: Boolean = false,
  val chosen: IngredientListItem? = null,
  val done: Boolean = false,
)

@HiltViewModel(assistedFactory = IngredientPickerViewModel.Factory::class)
class IngredientPickerViewModel
@AssistedInject
constructor(private val repository: DietRepository, @Assisted private val recipeId: String) : ViewModel() {
  @AssistedFactory
  interface Factory {
    fun create(recipeId: String): IngredientPickerViewModel
  }

  private val local = MutableStateFlow(IngredientPickerState())

  val state: StateFlow<IngredientPickerState> =
    combine(local, repository.ingredients()) { l, all ->
        l.copy(items = all.filter { it.matches(l.query) }, exactMatch = all.any { it.name.equals(l.query.trim(), true) })
      }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), IngredientPickerState())

  fun onQueryChange(v: String) = local.update { it.copy(query = v) }

  fun choose(item: IngredientListItem?) = local.update { it.copy(chosen = item) }

  /** Creates an ingredient named like the search (in grams, no values yet) and moves on. */
  fun createFromQuery() {
    val name = local.value.query.trim()
    if (name.isEmpty()) return
    viewModelScope.launch {
      val id = repository.saveIngredient(null, IngredientValues(name, "", QuantityUnit.G))
      choose(IngredientListItem(id, name, "", QuantityUnit.G, null, 0))
    }
  }

  fun add(quantity: Double, unit: QuantityUnit) {
    val ingredient = local.value.chosen ?: return
    viewModelScope.launch {
      repository.addItem(recipeId, ingredient.id, quantity, unit)
      local.update { it.copy(chosen = null, done = true) }
    }
  }
}

@Composable
fun IngredientPickerRoute(
  recipeId: String,
  onDone: () -> Unit,
  viewModel: IngredientPickerViewModel =
    hiltViewModel<IngredientPickerViewModel, IngredientPickerViewModel.Factory>(key = "ipicker-$recipeId") { it.create(recipeId) },
) {
  val state by viewModel.state.collectAsStateWithLifecycle()
  LaunchedEffect(state.done) { if (state.done) onDone() }
  ScreenScaffold(title = stringResource(R.string.recipe_add_ingredient), onBack = onDone) { padding ->
    Column(Modifier.fillMaxSize().padding(padding)) {
      SearchField(state.query, viewModel::onQueryChange, placeholder = stringResource(R.string.ingredient_search))
      LazyColumn {
        if (state.query.isNotBlank() && !state.exactMatch) {
          item {
            ListItem(
              modifier = Modifier.clickable(onClick = viewModel::createFromQuery).testTag("create_ingredient"),
              leadingContent = { Icon(Icons.Filled.Add, contentDescription = null) },
              headlineContent = { Text(stringResource(R.string.exercise_create_named, state.query.trim())) },
              supportingContent = { Text(stringResource(R.string.ingredient_create_hint)) },
            )
            HorizontalDivider()
          }
        }
        items(state.items, key = { it.id }) { item ->
          ListItem(
            modifier = Modifier.clickable { viewModel.choose(item) },
            headlineContent = { Text(item.name) },
            supportingContent = { if (item.shopCategory.isNotBlank()) Text(item.shopCategory) },
          )
          HorizontalDivider()
        }
        if (state.items.isEmpty() && state.query.isBlank()) {
          item { EmptyState(stringResource(R.string.ingredient_empty), hint = stringResource(R.string.picker_empty_hint)) }
        }
      }
    }
  }
  state.chosen?.let { ingredient ->
    QuantityDialog(
      title = ingredient.name,
      initialQuantity = null,
      initialUnit = ingredient.defaultUnit,
      onDismiss = { viewModel.choose(null) },
      onConfirm = viewModel::add,
    )
  }
}
