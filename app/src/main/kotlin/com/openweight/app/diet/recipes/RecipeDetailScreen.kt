package com.openweight.app.diet.recipes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.openweight.app.R
import kotlinx.coroutines.flow.combine
import com.openweight.app.diet.formatNumber
import com.openweight.app.diet.household.HouseholdRepository
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material3.FilterChip
import com.openweight.app.core.ui.ScreenScaffold
import com.openweight.app.diet.data.DietRepository
import com.openweight.app.diet.data.Macros
import com.openweight.app.diet.data.RecipeDetail
import com.openweight.app.diet.formatGrams
import com.openweight.app.diet.formatKcal
import com.openweight.app.diet.formatQuantity
import com.openweight.app.workout.data.DeleteResult
import com.openweight.app.transfer.ExportRequest
import com.openweight.app.transfer.ui.ExportDialog
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface RecipeEvent {
  data class Opened(val id: String) : RecipeEvent

  data object Deleted : RecipeEvent

  data class InUse(val count: Int) : RecipeEvent
}

/** null while loading; Gone when deleted. */
sealed interface RecipeViewState {
  data object Loading : RecipeViewState

  data object Gone : RecipeViewState

  /** [householdFactor]: 1 + the portions of everyone who eats by default; 1 with no household. */
  data class Loaded(val recipe: RecipeDetail, val householdFactor: Double = 1.0) : RecipeViewState
}

@HiltViewModel(assistedFactory = RecipeDetailViewModel.Factory::class)
class RecipeDetailViewModel
@AssistedInject
constructor(private val repository: DietRepository, household: HouseholdRepository, @Assisted private val recipeId: String) : ViewModel() {
  @AssistedFactory
  interface Factory {
    fun create(recipeId: String): RecipeDetailViewModel
  }

  val state: StateFlow<RecipeViewState> =
    combine(repository.recipe(recipeId), household.members()) { r, people ->
        if (r == null) RecipeViewState.Gone
        else RecipeViewState.Loaded(r, 1 + people.filter { it.eatsByDefault }.sumOf { it.portion })
      }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RecipeViewState.Loading)

  private val _events = Channel<RecipeEvent>(Channel.BUFFERED)
  val events: Flow<RecipeEvent> = _events.receiveAsFlow()

  fun duplicate() = viewModelScope.launch { _events.send(RecipeEvent.Opened(repository.duplicateRecipe(recipeId))) }

  fun delete() =
    viewModelScope.launch {
      when (val r = repository.deleteRecipe(recipeId)) {
        DeleteResult.Deleted -> _events.send(RecipeEvent.Deleted)
        is DeleteResult.InUse -> _events.send(RecipeEvent.InUse(r.count))
      }
    }
}

/** A recipe to read and cook from: calories and macros per serving, ingredients, steps. */
@Composable
fun RecipeDetailRoute(
  recipeId: String,
  onBack: () -> Unit,
  onEdit: () -> Unit,
  onOpenRecipe: (String) -> Unit,
  viewModel: RecipeDetailViewModel =
    hiltViewModel<RecipeDetailViewModel, RecipeDetailViewModel.Factory>(key = "recipe-$recipeId") { it.create(recipeId) },
) {
  val state by viewModel.state.collectAsStateWithLifecycle()
  val snackbar = remember { SnackbarHostState() }
  val resources = LocalResources.current
  var menu by remember { mutableStateOf(false) }
  var exporting by remember { mutableStateOf(false) }
  var confirmDelete by remember { mutableStateOf(false) }
  LaunchedEffect(state) { if (state is RecipeViewState.Gone) onBack() }
  LaunchedEffect(viewModel) {
    viewModel.events.collect { e ->
      when (e) {
        is RecipeEvent.Opened -> onOpenRecipe(e.id)
        RecipeEvent.Deleted -> onBack()
        is RecipeEvent.InUse -> snackbar.showSnackbar(resources.getQuantityString(R.plurals.recipe_in_use, e.count, e.count))
      }
    }
  }
  val recipe = (state as? RecipeViewState.Loaded)?.recipe
  val householdFactor = (state as? RecipeViewState.Loaded)?.householdFactor ?: 1.0
  var forHousehold by rememberSaveable { mutableStateOf(false) }
  val scale = if (forHousehold) householdFactor else 1.0

  ScreenScaffold(
    title = recipe?.name.orEmpty(),
    onBack = onBack,
    snackbarHostState = snackbar,
    actions = {
      IconButton(onClick = onEdit, modifier = Modifier.testTag("edit_recipe")) { Icon(Icons.Filled.Edit, stringResource(R.string.recipe_edit)) }
      Box {
        IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, stringResource(R.string.action_more)) }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
          DropdownMenuItem(text = { Text(stringResource(R.string.action_duplicate)) }, onClick = { menu = false; viewModel.duplicate() })
          DropdownMenuItem(text = { Text(stringResource(R.string.action_export)) }, onClick = { menu = false; exporting = true }, modifier = Modifier.testTag("export_item"))
          DropdownMenuItem(text = { Text(stringResource(R.string.action_delete)) }, onClick = { menu = false; confirmDelete = true })
        }
        if (exporting) ExportDialog(ExportRequest.Items(recipeIds = setOf(recipeId)), onDismiss = { exporting = false })
      }
    },
  ) { padding ->
    if (recipe == null) return@ScreenScaffold
    LazyColumn(
      modifier = Modifier.fillMaxSize().padding(padding).testTag("recipe_view"),
      contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      item {
        val parts = buildList {
          add(pluralStringResource(R.plurals.serving_count, recipe.servings, recipe.servings))
          recipe.prepMinutes?.let { add(stringResource(R.string.recipe_minutes, it)) }
          if (recipe.tags.isNotBlank()) add(recipe.tags)
        }
        Text(parts.joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
      if (recipe.items.isNotEmpty()) item { MacrosCard(recipe) }
      item { SectionTitle(stringResource(R.string.recipe_ingredients)) }
      if (householdFactor > 1.0) {
        item {
          FilterChip(
            selected = forHousehold,
            onClick = { forHousehold = !forHousehold },
            label = { Text(stringResource(R.string.household_cook_for, formatNumber(householdFactor))) },
            modifier = Modifier.testTag("cook_for_household"),
          )
        }
      }
      if (recipe.items.isEmpty()) {
        item { Text(stringResource(R.string.recipe_no_ingredients), color = MaterialTheme.colorScheme.onSurfaceVariant) }
      }
      items(recipe.items, key = { it.id }) { item ->
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
          Text(item.name, modifier = Modifier.weight(1f))
          Text(formatQuantity(item.quantity * scale, item.unit), fontWeight = FontWeight.SemiBold, modifier = Modifier.testTag("qty_${item.name}"))
          Text(
            item.macros?.let { formatKcal(it.kcal) } ?: "—",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 12.dp),
          )
        }
        HorizontalDivider()
      }
      if (recipe.steps.isNotBlank()) {
        item { SectionTitle(stringResource(R.string.recipe_steps)) }
        item { Text(recipe.steps, style = MaterialTheme.typography.bodyLarge) }
      }
      if (recipe.notes.isNotBlank()) {
        item { SectionTitle(stringResource(R.string.field_notes)) }
        item { Text(recipe.notes, style = MaterialTheme.typography.bodyMedium) }
      }
    }
  }

  if (confirmDelete && recipe != null) {
    AlertDialog(
      onDismissRequest = { confirmDelete = false },
      title = { Text(stringResource(R.string.recipe_delete_title)) },
      text = { Text(stringResource(R.string.recipe_delete_text, recipe.name)) },
      confirmButton = { TextButton(onClick = { confirmDelete = false; viewModel.delete() }) { Text(stringResource(R.string.action_delete)) } },
      dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.action_cancel)) } },
    )
  }
}

@Composable
private fun SectionTitle(text: String) {
  Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
}

/** Per serving first (what you eat), the whole recipe below. */
@Composable
private fun MacrosCard(recipe: RecipeDetail) {
  val n = recipe.nutrition
  Card(
    modifier = Modifier.fillMaxWidth().testTag("macros_card"),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
  ) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Text(stringResource(R.string.recipe_per_serving), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
      MacroRow(n.perServing, large = true)
      Text(
        stringResource(R.string.recipe_whole, formatKcal(n.total.kcal), formatGrams(n.total.protein), formatGrams(n.total.carbs), formatGrams(n.total.fat)),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onPrimaryContainer,
      )
      if (!n.complete) {
        Text(
          stringResource(R.string.recipe_incomplete, n.missing.joinToString(", ")),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.error,
          modifier = Modifier.testTag("macros_incomplete"),
        )
      }
    }
  }
}

@Composable
private fun MacroRow(m: Macros, large: Boolean) {
  Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
    MacroValue(formatKcal(m.kcal), stringResource(R.string.macro_kcal_short), large)
    MacroValue(formatGrams(m.protein), stringResource(R.string.macro_protein), large)
    MacroValue(formatGrams(m.carbs), stringResource(R.string.macro_carbs), large)
    MacroValue(formatGrams(m.fat), stringResource(R.string.macro_fat), large)
  }
}

@Composable
private fun MacroValue(value: String, label: String, large: Boolean) {
  Column(horizontalAlignment = Alignment.CenterHorizontally) {
    Text(
      value,
      style = if (large) MaterialTheme.typography.titleLarge else MaterialTheme.typography.bodyLarge,
      color = MaterialTheme.colorScheme.onPrimaryContainer,
    )
    Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
  }
}
