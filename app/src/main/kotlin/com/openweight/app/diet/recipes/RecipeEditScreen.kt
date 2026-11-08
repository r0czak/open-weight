package com.openweight.app.diet.recipes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.openweight.app.R
import com.openweight.app.core.model.QuantityUnit
import com.openweight.app.core.ui.ScreenScaffold
import com.openweight.app.diet.data.DietRepository
import com.openweight.app.diet.data.RecipeInfo
import com.openweight.app.diet.data.RecipeItem
import com.openweight.app.diet.data.validQuantity
import com.openweight.app.diet.formatKcal
import com.openweight.app.diet.formatNumber
import com.openweight.app.diet.formatQuantity
import com.openweight.app.diet.label
import com.openweight.app.diet.parseNumber
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RecipeForm(
  val loaded: Boolean = false,
  val name: String = "",
  val servings: String = "2",
  val prepMinutes: String = "",
  val tags: String = "",
  val steps: String = "",
  val notes: String = "",
  val error: Boolean = false,
  val justCreated: Boolean = false,
  val done: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel(assistedFactory = RecipeEditViewModel.Factory::class)
class RecipeEditViewModel
@AssistedInject
constructor(
  private val repository: DietRepository,
  private val saved: SavedStateHandle,
  @Assisted initialId: String?,
) : ViewModel() {
  @AssistedFactory
  interface Factory {
    fun create(recipeId: String?): RecipeEditViewModel
  }

  /** Null until a new recipe is first saved. */
  val recipeId: StateFlow<String?> = saved.getStateFlow(KEY_ID, initialId)

  private val _form = MutableStateFlow(RecipeForm(loaded = initialId == null))
  val form: StateFlow<RecipeForm> = _form.asStateFlow()

  val items: StateFlow<List<RecipeItem>> =
    recipeId
      .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else repository.recipe(id).map { it?.items.orEmpty() } }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

  init {
    if (initialId != null) {
      viewModelScope.launch {
        val r = repository.recipe(initialId).first()
        _form.update {
          if (r == null) it.copy(done = true)
          else
            it.copy(
              loaded = true,
              name = r.name,
              servings = r.servings.toString(),
              prepMinutes = r.prepMinutes?.toString().orEmpty(),
              tags = r.tags,
              steps = r.steps,
              notes = r.notes,
            )
        }
      }
    }
  }

  fun update(f: (RecipeForm) -> RecipeForm) = _form.update { f(it).copy(error = false) }

  /** New recipe: creates it and stays, so ingredients can be added. Existing: saves and closes. */
  fun save() {
    val f = _form.value
    val info =
      try {
        RecipeInfo(
          name = f.name,
          servings = f.servings.trim().toIntOrNull() ?: -1,
          prepMinutes = f.prepMinutes.trim().takeIf { it.isNotEmpty() }?.let { it.toIntOrNull() ?: -1 },
          steps = f.steps,
          tags = f.tags,
          notes = f.notes,
        )
      } catch (e: IllegalArgumentException) {
        return _form.update { it.copy(error = true) }
      }
    viewModelScope.launch {
      val id = recipeId.value
      if (id == null) {
        saved[KEY_ID] = repository.createRecipe(info)
        _form.update { it.copy(justCreated = true) }
      } else {
        repository.updateRecipeInfo(id, info)
        _form.update { it.copy(done = true) }
      }
    }
  }

  fun moveItem(itemId: String, delta: Int) = viewModelScope.launch { repository.moveItem(itemId, delta) }

  fun removeItem(itemId: String) = viewModelScope.launch { repository.removeItem(itemId) }

  fun updateItem(itemId: String, quantity: Double, unit: QuantityUnit) =
    viewModelScope.launch { repository.updateItem(itemId, quantity, unit) }

  private companion object {
    const val KEY_ID = "recipeId"
  }
}

/** Name and servings come first (items 0 and 1), then the ingredients header. */
private const val INGREDIENTS_HEADER_INDEX = 2

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RecipeEditRoute(
  recipeId: String?,
  onDone: () -> Unit,
  onAddIngredient: (recipeId: String) -> Unit,
  viewModel: RecipeEditViewModel =
    hiltViewModel<RecipeEditViewModel, RecipeEditViewModel.Factory>(key = "recipe-edit-$recipeId") { it.create(recipeId) },
) {
  val form by viewModel.form.collectAsStateWithLifecycle()
  val id by viewModel.recipeId.collectAsStateWithLifecycle()
  val items by viewModel.items.collectAsStateWithLifecycle()
  LaunchedEffect(form.done) { if (form.done) onDone() }
  val listState = rememberLazyListState()
  // Right after a new recipe is first saved, bring its (new) ingredients section into view.
  LaunchedEffect(form.justCreated) { if (form.justCreated) listState.animateScrollToItem(INGREDIENTS_HEADER_INDEX) }
  var editing by remember { mutableStateOf<RecipeItem?>(null) }
  val caps = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
  val number = KeyboardOptions(keyboardType = KeyboardType.Number)

  ScreenScaffold(
    title = stringResource(if (recipeId == null && !form.justCreated) R.string.recipe_new else R.string.recipe_edit),
    onBack = onDone,
    actions = {
      TextButton(onClick = viewModel::save, modifier = Modifier.testTag("save_recipe")) { Text(stringResource(R.string.action_save)) }
    },
  ) { padding ->
    if (!form.loaded) return@ScreenScaffold
    LazyColumn(
      state = listState,
      modifier = Modifier.fillMaxSize().padding(padding).testTag("recipe_form"),
      contentPadding = PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      item {
        OutlinedTextField(
          value = form.name,
          onValueChange = { v -> viewModel.update { it.copy(name = v) } },
          label = { Text(stringResource(R.string.field_name)) },
          singleLine = true,
          keyboardOptions = caps,
          isError = form.error && form.name.isBlank(),
          modifier = Modifier.fillMaxWidth().testTag("recipe_name"),
        )
      }
      item {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedTextField(
            value = form.servings,
            onValueChange = { v -> viewModel.update { it.copy(servings = v) } },
            label = { Text(stringResource(R.string.recipe_servings)) },
            singleLine = true,
            keyboardOptions = number,
            modifier = Modifier.weight(1f).testTag("recipe_servings"),
          )
          OutlinedTextField(
            value = form.prepMinutes,
            onValueChange = { v -> viewModel.update { it.copy(prepMinutes = v) } },
            label = { Text(stringResource(R.string.recipe_prep_minutes)) },
            singleLine = true,
            keyboardOptions = number,
            modifier = Modifier.weight(1f),
          )
        }
      }
      if (form.error) {
        item { Text(stringResource(R.string.error_recipe_form), color = MaterialTheme.colorScheme.error) }
      }

      item {
        Text(stringResource(R.string.recipe_ingredients), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
      }
      val currentId = id
      if (currentId == null) {
        item {
          Text(stringResource(R.string.recipe_save_first), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
      } else {
        items(items, key = { it.id }) { item ->
          ItemRow(
            item = item,
            first = items.first().id == item.id,
            last = items.last().id == item.id,
            onClick = { editing = item },
            onMove = { viewModel.moveItem(item.id, it) },
            onRemove = { viewModel.removeItem(item.id) },
          )
          HorizontalDivider()
        }
        item {
          OutlinedButton(onClick = { onAddIngredient(currentId) }, modifier = Modifier.fillMaxWidth().testTag("add_recipe_ingredient")) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Text(stringResource(R.string.recipe_add_ingredient), modifier = Modifier.padding(start = 8.dp))
          }
        }
      }

      item {
        OutlinedTextField(
          value = form.tags,
          onValueChange = { v -> viewModel.update { it.copy(tags = v) } },
          label = { Text(stringResource(R.string.recipe_tags)) },
          placeholder = { Text(stringResource(R.string.recipe_tags_hint)) },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
      }
      item {
        OutlinedTextField(
          value = form.steps,
          onValueChange = { v -> viewModel.update { it.copy(steps = v) } },
          label = { Text(stringResource(R.string.recipe_steps)) },
          placeholder = { Text(stringResource(R.string.recipe_steps_hint)) },
          minLines = 4,
          keyboardOptions = caps,
          modifier = Modifier.fillMaxWidth(),
        )
      }
      item {
        OutlinedTextField(
          value = form.notes,
          onValueChange = { v -> viewModel.update { it.copy(notes = v) } },
          label = { Text(stringResource(R.string.field_notes)) },
          minLines = 2,
          keyboardOptions = caps,
          modifier = Modifier.fillMaxWidth(),
        )
      }
    }
  }

  editing?.let { item ->
    QuantityDialog(
      title = item.name,
      initialQuantity = item.quantity,
      initialUnit = item.unit,
      onDismiss = { editing = null },
      onConfirm = { q, u ->
        editing = null
        viewModel.updateItem(item.id, q, u)
      },
    )
  }
}

@Composable
private fun ItemRow(item: RecipeItem, first: Boolean, last: Boolean, onClick: () -> Unit, onMove: (Int) -> Unit, onRemove: () -> Unit) {
  var menu by remember { mutableStateOf(false) }
  ListItem(
    modifier = Modifier.clickable(onClick = onClick).testTag("item_${item.name}"),
    headlineContent = { Text(item.name) },
    supportingContent = { Text(formatQuantity(item.quantity, item.unit) + (item.macros?.let { " · " + formatKcal(it.kcal) } ?: "")) },
    trailingContent = {
      Box {
        IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, stringResource(R.string.action_more)) }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
          if (!first) DropdownMenuItem(text = { Text(stringResource(R.string.action_move_up)) }, onClick = { menu = false; onMove(-1) })
          if (!last) DropdownMenuItem(text = { Text(stringResource(R.string.action_move_down)) }, onClick = { menu = false; onMove(1) })
          DropdownMenuItem(text = { Text(stringResource(R.string.recipe_remove_ingredient)) }, onClick = { menu = false; onRemove() })
        }
      }
    },
  )
}

/** Quantity and unit of one recipe line, e.g. 200 g or 2 pcs. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QuantityDialog(
  title: String,
  initialQuantity: Double?,
  initialUnit: QuantityUnit,
  onDismiss: () -> Unit,
  onConfirm: (Double, QuantityUnit) -> Unit,
) {
  var text by remember { mutableStateOf(initialQuantity?.let(::formatNumber).orEmpty()) }
  var unit by remember { mutableStateOf(initialUnit) }
  var error by remember { mutableStateOf(false) }
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(title) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
          value = text,
          onValueChange = {
            text = it
            error = false
          },
          label = { Text(stringResource(R.string.recipe_quantity)) },
          isError = error,
          supportingText = { if (error) Text(stringResource(R.string.error_quantity)) },
          singleLine = true,
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
          modifier = Modifier.fillMaxWidth().testTag("quantity"),
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          QuantityUnit.entries.forEach { u ->
            FilterChip(selected = unit == u, onClick = { unit = u }, label = { Text(u.label) }, modifier = Modifier.testTag("qunit_${u.name}"))
          }
        }
      }
    },
    confirmButton = {
      TextButton(
        onClick = {
          val q = parseNumber(text)
          if (q == null || !validQuantity(q, unit)) error = true else onConfirm(q, unit)
        },
        modifier = Modifier.testTag("confirm_quantity"),
      ) {
        Text(stringResource(R.string.action_save))
      }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
  )
}
