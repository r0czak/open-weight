package com.openweight.app.diet.ingredients

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.openweight.app.R
import com.openweight.app.core.model.QuantityUnit
import com.openweight.app.core.ui.CardListItem
import com.openweight.app.core.ui.EmptyState
import com.openweight.app.core.ui.ScreenScaffold
import com.openweight.app.diet.data.DietRepository
import com.openweight.app.diet.data.INGREDIENT_UNITS
import com.openweight.app.diet.data.IngredientListItem
import com.openweight.app.diet.data.IngredientValues
import com.openweight.app.diet.formatNumber
import com.openweight.app.diet.label
import com.openweight.app.diet.parseNumber
import com.openweight.app.workout.data.DeleteResult
import com.openweight.app.workout.data.DuplicateNameException
import com.openweight.app.workout.exercise.SearchField
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Matches name or shop category, ignoring case. */
fun IngredientListItem.matches(query: String): Boolean {
  val q = query.trim()
  return q.isEmpty() || name.contains(q, ignoreCase = true) || shopCategory.contains(q, ignoreCase = true)
}

// ---------------- List ----------------

data class IngredientListState(val loading: Boolean = true, val query: String = "", val items: List<IngredientListItem> = emptyList(), val total: Int = 0)

@HiltViewModel
class IngredientListViewModel @Inject constructor(repository: DietRepository) : ViewModel() {
  private val query = MutableStateFlow("")

  val state: StateFlow<IngredientListState> =
    combine(query, repository.ingredients()) { q, all -> IngredientListState(false, q, all.filter { it.matches(q) }, all.size) }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), IngredientListState())

  fun onQueryChange(v: String) {
    query.value = v
  }
}

@Composable
fun IngredientListRoute(
  onBack: () -> Unit,
  onOpen: (String) -> Unit,
  onCreate: () -> Unit,
  viewModel: IngredientListViewModel = hiltViewModel(),
) {
  val state by viewModel.state.collectAsStateWithLifecycle()
  ScreenScaffold(
    title = stringResource(R.string.diet_ingredients),
    onBack = onBack,
    floatingActionButton = {
      FloatingActionButton(onClick = onCreate, modifier = Modifier.testTag("add_ingredient")) {
        Icon(Icons.Filled.Add, stringResource(R.string.ingredient_new))
      }
    },
  ) { padding ->
    Column(Modifier.fillMaxSize().padding(padding)) {
      SearchField(state.query, viewModel::onQueryChange, placeholder = stringResource(R.string.ingredient_search))
      when {
        state.loading -> Unit
        state.total == 0 -> EmptyState(stringResource(R.string.ingredient_empty), hint = stringResource(R.string.ingredient_empty_hint))
        state.items.isEmpty() -> EmptyState(stringResource(R.string.search_no_results, state.query))
        else ->
          LazyColumn(contentPadding = PaddingValues(bottom = 88.dp)) {
            items(state.items, key = { it.id }) { item ->
              CardListItem(state.items.indexOf(item), state.items.size) {
                ListItem(
                  modifier = Modifier.clickable { onOpen(item.id) },
                  headlineContent = { Text(item.name) },
                  supportingContent = {
                    val parts = buildList {
                      if (item.shopCategory.isNotBlank()) add(item.shopCategory)
                      add(
                        item.kcalPer100?.let { stringResource(R.string.ingredient_kcal_per_100, formatNumber(it), basisLabel(item.defaultUnit)) }
                          ?: stringResource(R.string.ingredient_no_nutrition)
                      )
                    }
                    Text(parts.joinToString(" · "))
                  },
                  trailingContent = {
                    if (item.usageCount > 0) Text(pluralStringResource(R.plurals.used_in_recipes, item.usageCount, item.usageCount))
                  },
                )
              }
            }
          }
      }
    }
  }
}

/** Nutrition is per 100 ml for liquids, per 100 g otherwise. */
@Composable
fun basisLabel(unit: QuantityUnit): String = if (unit.dimension == QuantityUnit.Dimension.VOLUME) "100 ml" else "100 g"

// ---------------- Editor ----------------

data class IngredientEditState(
  val isNew: Boolean,
  val loaded: Boolean = false,
  val name: String = "",
  val category: String = "",
  val unit: QuantityUnit = QuantityUnit.G,
  val kcal: String = "",
  val protein: String = "",
  val carbs: String = "",
  val fat: String = "",
  val gramsPerPiece: String = "",
  val categories: List<String> = emptyList(),
  val error: IngredientError? = null,
  val inUseCount: Int? = null,
  /** Set when saved or deleted: the id of the saved ingredient, or "" after a delete. */
  val doneWith: String? = null,
)

enum class IngredientError { NAME_EMPTY, NAME_TAKEN, NUMBER }

@HiltViewModel(assistedFactory = IngredientEditViewModel.Factory::class)
class IngredientEditViewModel
@AssistedInject
constructor(private val repository: DietRepository, @Assisted private val ingredientId: String?) : ViewModel() {
  @AssistedFactory
  interface Factory {
    fun create(ingredientId: String?): IngredientEditViewModel
  }

  private val _state = MutableStateFlow(IngredientEditState(isNew = ingredientId == null))
  val state: StateFlow<IngredientEditState> = _state.asStateFlow()

  init {
    viewModelScope.launch { repository.shopCategories().collect { c -> _state.update { it.copy(categories = c) } } }
    viewModelScope.launch {
      val i = ingredientId?.let { repository.getIngredient(it) }
      _state.update {
        if (i == null) it.copy(loaded = true)
        else
          it.copy(
            loaded = true,
            name = i.name,
            category = i.shopCategory,
            unit = i.defaultUnit,
            kcal = i.kcalPer100?.let(::formatNumber).orEmpty(),
            protein = i.proteinPer100?.let(::formatNumber).orEmpty(),
            carbs = i.carbsPer100?.let(::formatNumber).orEmpty(),
            fat = i.fatPer100?.let(::formatNumber).orEmpty(),
            gramsPerPiece = i.gramsPerPiece?.let(::formatNumber).orEmpty(),
          )
      }
    }
  }

  fun update(f: (IngredientEditState) -> IngredientEditState) = _state.update { f(it).copy(error = null) }

  fun save() {
    val s = _state.value
    if (s.name.isBlank()) return _state.update { it.copy(error = IngredientError.NAME_EMPTY) }
    fun num(t: String): Double? = if (t.isBlank()) null else parseNumber(t) ?: -1.0
    val values =
      try {
        IngredientValues(s.name, s.category, s.unit, num(s.kcal), num(s.protein), num(s.carbs), num(s.fat), num(s.gramsPerPiece))
      } catch (e: IllegalArgumentException) {
        return _state.update { it.copy(error = IngredientError.NUMBER) }
      }
    viewModelScope.launch {
      try {
        val id = repository.saveIngredient(ingredientId, values)
        _state.update { it.copy(doneWith = id) }
      } catch (e: DuplicateNameException) {
        _state.update { it.copy(error = IngredientError.NAME_TAKEN) }
      }
    }
  }

  fun delete() {
    val id = ingredientId ?: return
    viewModelScope.launch {
      when (val r = repository.deleteIngredient(id)) {
        DeleteResult.Deleted -> _state.update { it.copy(doneWith = "") }
        is DeleteResult.InUse -> _state.update { it.copy(inUseCount = r.count) }
      }
    }
  }

  fun dismissInUse() = _state.update { it.copy(inUseCount = null) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun IngredientEditRoute(
  ingredientId: String?,
  onDone: (savedId: String?) -> Unit,
  viewModel: IngredientEditViewModel =
    hiltViewModel<IngredientEditViewModel, IngredientEditViewModel.Factory>(key = "ingredient-$ingredientId") { it.create(ingredientId) },
) {
  val s by viewModel.state.collectAsStateWithLifecycle()
  LaunchedEffect(s.doneWith) { s.doneWith?.let { onDone(it.ifEmpty { null }) } }
  var confirmDelete by remember { mutableStateOf(false) }
  val caps = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
  val decimal = KeyboardOptions(keyboardType = KeyboardType.Decimal)

  ScreenScaffold(
    title = stringResource(if (s.isNew) R.string.ingredient_new else R.string.ingredient_edit),
    onBack = { onDone(null) },
    actions = {
      if (!s.isNew) {
        IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, stringResource(R.string.action_delete)) }
      }
      TextButton(onClick = viewModel::save, modifier = Modifier.testTag("save_ingredient")) { Text(stringResource(R.string.action_save)) }
    },
  ) { padding ->
    if (!s.loaded) return@ScreenScaffold
    Column(
      Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      OutlinedTextField(
        value = s.name,
        onValueChange = { v -> viewModel.update { it.copy(name = v) } },
        label = { Text(stringResource(R.string.field_name)) },
        isError = s.error == IngredientError.NAME_EMPTY || s.error == IngredientError.NAME_TAKEN,
        supportingText = {
          when (s.error) {
            IngredientError.NAME_EMPTY -> Text(stringResource(R.string.error_name_empty))
            IngredientError.NAME_TAKEN -> Text(stringResource(R.string.error_ingredient_exists))
            else -> Unit
          }
        },
        singleLine = true,
        keyboardOptions = caps,
        modifier = Modifier.fillMaxWidth().testTag("ingredient_name"),
      )

      Text(stringResource(R.string.ingredient_unit), style = MaterialTheme.typography.labelLarge)
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        INGREDIENT_UNITS.forEach { u ->
          FilterChip(
            selected = s.unit == u,
            onClick = { viewModel.update { it.copy(unit = u) } },
            label = { Text(unitChoiceLabel(u)) },
            modifier = Modifier.testTag("unit_${u.name}"),
          )
        }
      }

      OutlinedTextField(
        value = s.category,
        onValueChange = { v -> viewModel.update { it.copy(category = v) } },
        label = { Text(stringResource(R.string.ingredient_category)) },
        singleLine = true,
        keyboardOptions = caps,
        modifier = Modifier.fillMaxWidth(),
      )
      FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        s.categories.forEach { c ->
          FilterChip(selected = s.category.equals(c, ignoreCase = true), onClick = { viewModel.update { it.copy(category = c) } }, label = { Text(c) })
        }
      }

      Text(
        stringResource(R.string.ingredient_nutrition_title, basisLabel(s.unit)),
        style = MaterialTheme.typography.labelLarge,
      )
      Text(stringResource(R.string.ingredient_nutrition_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        NumberBox(s.kcal, { v -> viewModel.update { it.copy(kcal = v) } }, stringResource(R.string.macro_kcal), Modifier.weight(1f).testTag("kcal"), decimal)
        NumberBox(s.protein, { v -> viewModel.update { it.copy(protein = v) } }, stringResource(R.string.macro_protein_g), Modifier.weight(1f).testTag("protein"), decimal)
      }
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        NumberBox(s.carbs, { v -> viewModel.update { it.copy(carbs = v) } }, stringResource(R.string.macro_carbs_g), Modifier.weight(1f).testTag("carbs"), decimal)
        NumberBox(s.fat, { v -> viewModel.update { it.copy(fat = v) } }, stringResource(R.string.macro_fat_g), Modifier.weight(1f).testTag("fat"), decimal)
      }
      OutlinedTextField(
        value = s.gramsPerPiece,
        onValueChange = { v -> viewModel.update { it.copy(gramsPerPiece = v) } },
        label = { Text(stringResource(R.string.ingredient_grams_per_piece)) },
        supportingText = { Text(stringResource(R.string.ingredient_grams_per_piece_hint)) },
        singleLine = true,
        keyboardOptions = decimal,
        modifier = Modifier.fillMaxWidth().testTag("grams_per_piece"),
      )
      if (s.error == IngredientError.NUMBER) {
        Text(stringResource(R.string.error_ingredient_numbers), color = MaterialTheme.colorScheme.error)
      }
    }
  }

  if (confirmDelete) {
    AlertDialog(
      onDismissRequest = { confirmDelete = false },
      title = { Text(stringResource(R.string.ingredient_delete_title)) },
      text = { Text(stringResource(R.string.ingredient_delete_text, s.name)) },
      confirmButton = { TextButton(onClick = { confirmDelete = false; viewModel.delete() }) { Text(stringResource(R.string.action_delete)) } },
      dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.action_cancel)) } },
    )
  }
  s.inUseCount?.let { n ->
    AlertDialog(
      onDismissRequest = viewModel::dismissInUse,
      title = { Text(stringResource(R.string.ingredient_in_use_title)) },
      text = { Text(pluralStringResource(R.plurals.ingredient_in_use_text, n, n)) },
      confirmButton = { TextButton(onClick = viewModel::dismissInUse) { Text(stringResource(R.string.action_ok)) } },
    )
  }
}

@Composable
fun unitChoiceLabel(u: QuantityUnit): String =
  when (u) {
    QuantityUnit.G -> stringResource(R.string.unit_grams)
    QuantityUnit.ML -> stringResource(R.string.unit_millilitres)
    QuantityUnit.PCS -> stringResource(R.string.unit_pieces)
    else -> u.label
  }

@Composable
private fun NumberBox(value: String, onChange: (String) -> Unit, label: String, modifier: Modifier, options: KeyboardOptions) {
  OutlinedTextField(value = value, onValueChange = onChange, label = { Text(label) }, singleLine = true, keyboardOptions = options, modifier = modifier)
}
