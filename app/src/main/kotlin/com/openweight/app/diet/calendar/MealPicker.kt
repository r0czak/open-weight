package com.openweight.app.diet.calendar

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.openweight.app.R
import com.openweight.app.core.model.MealSlot
import com.openweight.app.core.ui.EmptyState
import com.openweight.app.core.ui.ScreenScaffold
import com.openweight.app.diet.data.DietRepository
import com.openweight.app.diet.data.MealPlanRepository
import com.openweight.app.diet.data.RecipeSummary
import com.openweight.app.diet.formatKcal
import com.openweight.app.diet.formatNumber
import com.openweight.app.diet.parseNumber
import com.openweight.app.workout.exercise.SearchField
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.DayOfWeek
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Where a picked recipe goes: a calendar date, or a weekday of a diet plan. */
sealed interface MealTarget {
  data class Day(val date: LocalDate) : MealTarget

  data class Plan(val planId: String, val day: DayOfWeek) : MealTarget

  /** Another recipe in a calendar meal's place, for that day only. */
  data class Swap(val mealId: String, val date: LocalDate) : MealTarget
}

@Composable
fun slotLabel(slot: MealSlot): String =
  stringResource(
    when (slot) {
      MealSlot.BREAKFAST -> R.string.slot_breakfast
      MealSlot.LUNCH -> R.string.slot_lunch
      MealSlot.DINNER -> R.string.slot_dinner
      MealSlot.SNACK -> R.string.slot_snack
    }
  )

data class MealPickerState(
  val query: String = "",
  val slot: MealSlot = MealSlot.BREAKFAST,
  val items: List<RecipeSummary> = emptyList(),
  val total: Int = 0,
  val chosen: RecipeSummary? = null,
  val done: Boolean = false,
)

@HiltViewModel(assistedFactory = MealPickerViewModel.Factory::class)
class MealPickerViewModel
@AssistedInject
constructor(
  diet: DietRepository,
  private val meals: MealPlanRepository,
  @Assisted private val target: MealTarget,
  @Assisted initialSlot: MealSlot,
) : ViewModel() {
  @AssistedFactory
  interface Factory {
    fun create(target: MealTarget, initialSlot: MealSlot): MealPickerViewModel
  }

  private val local = MutableStateFlow(MealPickerState(slot = initialSlot))

  val state: StateFlow<MealPickerState> =
    combine(local, diet.recipes()) { l, all ->
        val q = l.query.trim()
        l.copy(items = all.filter { q.isEmpty() || it.name.contains(q, true) || it.tags.contains(q, true) }, total = all.size)
      }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MealPickerState(slot = initialSlot))

  fun onQueryChange(v: String) = local.update { it.copy(query = v) }

  fun onSlotChange(s: MealSlot) = local.update { it.copy(slot = s) }

  fun choose(r: RecipeSummary?) = local.update { it.copy(chosen = r) }

  fun add(servings: Double) {
    val recipe = local.value.chosen ?: return
    val slot = local.value.slot
    viewModelScope.launch {
      when (target) {
        is MealTarget.Day -> meals.addMeal(target.date, slot, recipe.id, servings)
        is MealTarget.Plan -> meals.addPlanMeal(target.planId, target.day, slot, recipe.id, servings)
        is MealTarget.Swap -> meals.swapRecipe(target.mealId, recipe.id, servings)
      }
      local.update { it.copy(chosen = null, done = true) }
    }
  }
}

/** Choose a recipe (and the meal slot) to put on a day or in a diet plan. */
@Composable
fun MealPickerRoute(
  target: MealTarget,
  initialSlot: MealSlot,
  title: String,
  onDone: () -> Unit,
  viewModel: MealPickerViewModel =
    hiltViewModel<MealPickerViewModel, MealPickerViewModel.Factory>(key = "meal-picker-$target-$initialSlot") {
      it.create(target, initialSlot)
    },
) {
  val state by viewModel.state.collectAsStateWithLifecycle()
  LaunchedEffect(state.done) { if (state.done) onDone() }
  ScreenScaffold(title = title, onBack = onDone) { padding ->
    Column(Modifier.fillMaxSize().padding(padding)) {
      Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        MealSlot.entries.forEach { s ->
          FilterChip(
            selected = state.slot == s,
            onClick = { viewModel.onSlotChange(s) },
            label = { Text(slotLabel(s)) },
            modifier = Modifier.testTag("slot_${s.name}"),
          )
        }
      }
      SearchField(state.query, viewModel::onQueryChange, placeholder = stringResource(R.string.recipe_search))
      if (state.total == 0) {
        EmptyState(stringResource(R.string.recipe_empty), hint = stringResource(R.string.meal_picker_no_recipes))
      }
      LazyColumn {
        items(state.items, key = { it.id }) { r ->
          ListItem(
            modifier = Modifier.clickable { viewModel.choose(r) }.testTag("pick_recipe_${r.name}"),
            headlineContent = { Text(r.name) },
            supportingContent = { Text(pluralStringResource(R.plurals.serving_count, r.servings, r.servings)) },
            trailingContent = { if (r.ingredientCount > 0) Text(stringResource(R.string.per_serving_kcal, formatKcal(r.nutrition.perServing.kcal))) },
          )
          HorizontalDivider()
        }
      }
    }
  }
  state.chosen?.let { r ->
    ServingsDialog(
      title = r.name,
      initial = 1.0,
      kcalPerServing = r.nutrition.perServing.kcal,
      onDismiss = { viewModel.choose(null) },
      onConfirm = viewModel::add,
    )
  }
}

/**
 * How many servings of a recipe a meal has (steps of 0.5). Optional actions for an existing meal:
 * open its recipe, or remove it.
 */
@Composable
fun ServingsDialog(
  title: String,
  initial: Double,
  kcalPerServing: Double?,
  onDismiss: () -> Unit,
  onConfirm: (Double) -> Unit,
  onRemove: (() -> Unit)? = null,
  onOpenRecipe: (() -> Unit)? = null,
  onSwap: (() -> Unit)? = null,
) {
  var text by remember { mutableStateOf(formatNumber(initial)) }
  val value = parseNumber(text)
  val valid = value != null && value > 0 && value <= MealPlanRepository.MAX_SERVINGS
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(title) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedButton(onClick = { text = formatNumber(((value ?: 1.0) - 0.5).coerceAtLeast(0.5)) }) { Text("−") }
          OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text(stringResource(R.string.recipe_servings)) },
            singleLine = true,
            isError = !valid,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.weight(1f).testTag("servings"),
          )
          OutlinedButton(onClick = { text = formatNumber(((value ?: 0.5) + 0.5).coerceAtMost(MealPlanRepository.MAX_SERVINGS)) }) { Text("+") }
        }
        if (valid && kcalPerServing != null) {
          Text(formatKcal(kcalPerServing * value))
        }
        if (onOpenRecipe != null || onRemove != null || onSwap != null) {
          FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            onOpenRecipe?.let { TextButton(onClick = it, modifier = Modifier.testTag("open_meal_recipe")) { Text(stringResource(R.string.meal_open_recipe)) } }
            onSwap?.let { TextButton(onClick = it, modifier = Modifier.testTag("swap_meal")) { Text(stringResource(R.string.meal_swap)) } }
            onRemove?.let { TextButton(onClick = it, modifier = Modifier.testTag("remove_meal")) { Text(stringResource(R.string.meal_remove)) } }
          }
        }
      }
    },
    confirmButton = {
      TextButton(onClick = { if (valid) onConfirm(value) }, enabled = valid, modifier = Modifier.testTag("confirm_servings")) {
        Text(stringResource(R.string.action_save))
      }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
  )
}
