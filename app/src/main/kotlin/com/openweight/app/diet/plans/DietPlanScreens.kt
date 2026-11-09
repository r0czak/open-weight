package com.openweight.app.diet.plans

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.openweight.app.R
import com.openweight.app.core.TimeAndIds
import com.openweight.app.core.ui.EmptyState
import com.openweight.app.core.ui.ScreenScaffold
import com.openweight.app.core.ui.formatMedium
import com.openweight.app.core.ui.pickerMillisToDate
import com.openweight.app.core.ui.toPickerMillis
import com.openweight.app.diet.calendar.DayTotals
import com.openweight.app.diet.calendar.ServingsDialog
import com.openweight.app.diet.calendar.slotLabel
import com.openweight.app.diet.data.ApplyMode
import com.openweight.app.diet.data.ApplyResult
import com.openweight.app.diet.data.DietPlanDetail
import com.openweight.app.diet.data.DietPlanSummary
import com.openweight.app.diet.data.MealPlanRepository
import com.openweight.app.diet.data.PlanMeal
import com.openweight.app.diet.formatKcal
import com.openweight.app.diet.formatNumber
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

// ---------------- List ----------------

@HiltViewModel
class DietPlanListViewModel @Inject constructor(private val meals: MealPlanRepository) : ViewModel() {
  val plans: StateFlow<List<DietPlanSummary>?> = meals.plans().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

  private val _opened = Channel<String>(Channel.BUFFERED)
  val opened: Flow<String> = _opened.receiveAsFlow()

  fun create(name: String) = viewModelScope.launch { _opened.send(meals.createPlan(name)) }
}

@Composable
fun DietPlanListRoute(onBack: () -> Unit, onOpen: (String) -> Unit, viewModel: DietPlanListViewModel = hiltViewModel()) {
  val plans by viewModel.plans.collectAsStateWithLifecycle()
  var creating by remember { mutableStateOf(false) }
  LaunchedEffect(viewModel) { viewModel.opened.collect(onOpen) }
  ScreenScaffold(
    title = stringResource(R.string.diet_plans),
    onBack = onBack,
    floatingActionButton = {
      FloatingActionButton(onClick = { creating = true }, modifier = Modifier.testTag("add_plan")) { Icon(Icons.Filled.Add, stringResource(R.string.plan_new)) }
    },
  ) { padding ->
    val list = plans ?: return@ScreenScaffold
    if (list.isEmpty()) {
      EmptyState(stringResource(R.string.plan_empty), hint = stringResource(R.string.plan_empty_hint), modifier = Modifier.padding(padding))
    } else {
      LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 88.dp)) {
        items(list, key = { it.id }) { p ->
          ListItem(
            modifier = Modifier.clickable { onOpen(p.id) },
            headlineContent = { Text(p.name) },
            supportingContent = {
              Text(
                pluralStringResource(R.plurals.plan_meals_per_week, p.mealCount, p.mealCount) + " · " +
                  stringResource(R.string.plan_avg_per_day, formatKcal(p.averageKcalPerDay))
              )
            },
          )
          HorizontalDivider()
        }
      }
    }
  }
  if (creating) {
    NameDialog(
      title = stringResource(R.string.plan_new),
      initialName = "",
      initialNotes = null,
      onDismiss = { creating = false },
      onConfirm = { name, _ -> creating = false; viewModel.create(name) },
    )
  }
}

@Composable
private fun NameDialog(title: String, initialName: String, initialNotes: String?, onDismiss: () -> Unit, onConfirm: (String, String) -> Unit) {
  var name by remember { mutableStateOf(initialName) }
  var notes by remember { mutableStateOf(initialNotes.orEmpty()) }
  val caps = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(title) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
          value = name,
          onValueChange = { name = it },
          label = { Text(stringResource(R.string.field_name)) },
          placeholder = { Text(stringResource(R.string.plan_name_hint)) },
          singleLine = true,
          keyboardOptions = caps,
          modifier = Modifier.fillMaxWidth().testTag("plan_name"),
        )
        if (initialNotes != null) {
          OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text(stringResource(R.string.field_notes)) }, minLines = 3, keyboardOptions = caps, modifier = Modifier.fillMaxWidth())
        }
      }
    },
    confirmButton = {
      TextButton(onClick = { if (name.isNotBlank()) onConfirm(name, notes) }, enabled = name.isNotBlank(), modifier = Modifier.testTag("confirm_plan")) {
        Text(stringResource(R.string.action_save))
      }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
  )
}

// ---------------- Editor ----------------

@HiltViewModel(assistedFactory = DietPlanEditViewModel.Factory::class)
class DietPlanEditViewModel
@AssistedInject
constructor(private val meals: MealPlanRepository, clock: TimeAndIds, @Assisted private val planId: String) : ViewModel() {
  @AssistedFactory
  interface Factory {
    fun create(planId: String): DietPlanEditViewModel
  }

  val today: LocalDate = clock.today()

  /** Null while loading; [GONE] after a delete. */
  val plan: StateFlow<DietPlanDetail?> = meals.plan(planId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

  private val _applied = Channel<ApplyResult>(Channel.BUFFERED)
  val applied: Flow<ApplyResult> = _applied.receiveAsFlow()

  fun rename(name: String, notes: String) = viewModelScope.launch { meals.updatePlan(planId, name, notes) }

  fun setServings(id: String, s: Double) = viewModelScope.launch { meals.setPlanMealServings(id, s) }

  fun remove(id: String) = viewModelScope.launch { meals.removePlanMeal(id) }

  fun delete(done: () -> Unit) = viewModelScope.launch { meals.deletePlan(planId); done() }

  fun apply(from: LocalDate, to: LocalDate, mode: ApplyMode) = viewModelScope.launch { _applied.send(meals.applyPlan(planId, from, to, mode)) }
}

@Composable
fun DietPlanEditRoute(
  planId: String,
  onBack: () -> Unit,
  onAddMeal: (DayOfWeek) -> Unit,
  onOpenRecipe: (String) -> Unit,
  viewModel: DietPlanEditViewModel =
    hiltViewModel<DietPlanEditViewModel, DietPlanEditViewModel.Factory>(key = "plan-$planId") { it.create(planId) },
) {
  val plan by viewModel.plan.collectAsStateWithLifecycle()
  val snackbar = remember { SnackbarHostState() }
  val resources = LocalResources.current
  val locale = LocalConfiguration.current.locales[0]
  var menu by remember { mutableStateOf(false) }
  var renaming by remember { mutableStateOf(false) }
  var confirmDelete by remember { mutableStateOf(false) }
  var applying by remember { mutableStateOf(false) }
  var editing by remember { mutableStateOf<PlanMeal?>(null) }
  LaunchedEffect(viewModel) {
    viewModel.applied.collect { r ->
      snackbar.showSnackbar(
        resources.getQuantityString(R.plurals.plan_applied, r.daysFilled, r.daysFilled) +
          if (r.daysSkipped > 0) " " + resources.getQuantityString(R.plurals.plan_applied_skipped, r.daysSkipped, r.daysSkipped) else ""
      )
    }
  }

  ScreenScaffold(
    title = plan?.name.orEmpty(),
    onBack = onBack,
    snackbarHostState = snackbar,
    actions = {
      Box {
        IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, stringResource(R.string.action_more)) }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
          DropdownMenuItem(text = { Text(stringResource(R.string.plan_edit_details)) }, onClick = { menu = false; renaming = true })
          DropdownMenuItem(text = { Text(stringResource(R.string.action_delete)) }, onClick = { menu = false; confirmDelete = true })
        }
      }
    },
  ) { padding ->
    val p = plan ?: return@ScreenScaffold
    LazyColumn(
      modifier = Modifier.fillMaxSize().padding(padding).testTag("plan_content"),
      contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      item {
        Button(onClick = { applying = true }, enabled = p.meals.isNotEmpty(), modifier = Modifier.fillMaxWidth().testTag("apply_plan")) {
          Text(stringResource(R.string.plan_apply))
        }
        Text(stringResource(R.string.plan_apply_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (p.notes.isNotBlank()) Text(p.notes, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
      }
      items(DayOfWeek.entries, key = { it.name }) { day ->
        val dayMeals = p.meals.filter { it.dayOfWeek == day }
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
          Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Column(Modifier.weight(1f)) {
                Text(day.getDisplayName(TextStyle.FULL_STANDALONE, locale).replaceFirstChar { it.titlecase(locale) }, style = MaterialTheme.typography.titleMedium)
                if (dayMeals.isNotEmpty()) DayTotals(p.dayTotal(day), dayMeals.all { it.complete })
              }
              TextButton(onClick = { onAddMeal(day) }, modifier = Modifier.testTag("plan_add_${day.name}")) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Text(stringResource(R.string.action_add))
              }
            }
            dayMeals.forEach { m ->
              Row(
                Modifier.fillMaxWidth().clickable { editing = m }.padding(vertical = 6.dp).testTag("plan_meal_${day.name}_${m.recipeName}"),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Text(slotLabel(m.slot), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(0.3f))
                Text(
                  m.recipeName + if (m.servings != 1.0) " × " + formatNumber(m.servings) else "",
                  style = MaterialTheme.typography.bodyMedium,
                  modifier = Modifier.weight(0.5f),
                )
                Text(formatKcal(m.macros.kcal), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(0.2f))
              }
            }
          }
        }
      }
    }
  }

  val p = plan ?: return
  editing?.let { m ->
    ServingsDialog(
      title = m.recipeName,
      initial = m.servings,
      kcalPerServing = m.macros.kcal / m.servings,
      onDismiss = { editing = null },
      onConfirm = { editing = null; viewModel.setServings(m.id, it) },
      onRemove = { editing = null; viewModel.remove(m.id) },
      onOpenRecipe = { editing = null; onOpenRecipe(m.recipeId) },
    )
  }
  if (renaming) {
    NameDialog(stringResource(R.string.plan_edit_details), p.name, p.notes, onDismiss = { renaming = false }) { n, notes ->
      renaming = false
      viewModel.rename(n, notes)
    }
  }
  if (confirmDelete) {
    AlertDialog(
      onDismissRequest = { confirmDelete = false },
      title = { Text(stringResource(R.string.plan_delete_title)) },
      text = { Text(stringResource(R.string.plan_delete_text, p.name)) },
      confirmButton = { TextButton(onClick = { confirmDelete = false; viewModel.delete(onBack) }) { Text(stringResource(R.string.action_delete)) } },
      dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.action_cancel)) } },
    )
  }
  if (applying) {
    ApplyPlanDialog(today = viewModel.today, onDismiss = { applying = false }) { from, to, mode ->
      applying = false
      viewModel.apply(from, to, mode)
    }
  }
}

/** Date range and what happens to days that already have meals. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ApplyPlanDialog(today: LocalDate, onDismiss: () -> Unit, onApply: (LocalDate, LocalDate, ApplyMode) -> Unit) {
  val nextMonday = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY))
  var from by remember { mutableStateOf(nextMonday) }
  var to by remember { mutableStateOf(nextMonday.plusWeeks(4).minusDays(1)) }
  var mode by remember { mutableStateOf(ApplyMode.SKIP_DAYS_WITH_MEALS) }
  var picking by remember { mutableStateOf<String?>(null) }
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(stringResource(R.string.plan_apply)) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.block_starts), style = MaterialTheme.typography.labelLarge)
        OutlinedButton(onClick = { picking = "from" }, modifier = Modifier.fillMaxWidth()) { Text(from.formatMedium()) }
        Text(stringResource(R.string.block_ends), style = MaterialTheme.typography.labelLarge)
        OutlinedButton(onClick = { picking = "to" }, modifier = Modifier.fillMaxWidth()) { Text(to.formatMedium()) }
        if (to.isBefore(from)) Text(stringResource(R.string.error_block_dates), color = MaterialTheme.colorScheme.error)
        Text(stringResource(R.string.plan_busy_days), style = MaterialTheme.typography.labelLarge)
        listOf(ApplyMode.SKIP_DAYS_WITH_MEALS to R.string.plan_mode_skip, ApplyMode.REPLACE to R.string.plan_mode_replace).forEach { (m, label) ->
          Row(
            Modifier.fillMaxWidth().selectable(selected = mode == m, onClick = { mode = m }, role = Role.RadioButton).testTag("mode_${m.name}"),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            RadioButton(selected = mode == m, onClick = null)
            Text(stringResource(label), modifier = Modifier.padding(start = 8.dp))
          }
        }
      }
    },
    confirmButton = {
      TextButton(onClick = { onApply(from, to, mode) }, enabled = !to.isBefore(from), modifier = Modifier.testTag("confirm_apply")) {
        Text(stringResource(R.string.plan_apply_button))
      }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
  )
  picking?.let { which ->
    val state = rememberDatePickerState(initialSelectedDateMillis = (if (which == "from") from else to).toPickerMillis())
    DatePickerDialog(
      onDismissRequest = { picking = null },
      confirmButton = {
        TextButton(onClick = {
          state.selectedDateMillis?.let(::pickerMillisToDate)?.let { if (which == "from") from = it else to = it }
          picking = null
        }) { Text(stringResource(R.string.action_ok)) }
      },
      dismissButton = { TextButton(onClick = { picking = null }) { Text(stringResource(R.string.action_cancel)) } },
    ) { DatePicker(state = state) }
  }
}
