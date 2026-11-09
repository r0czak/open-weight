package com.openweight.app.diet.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.openweight.app.R
import com.openweight.app.core.TimeAndIds
import com.openweight.app.core.model.MealSlot
import com.openweight.app.core.ui.formatDayShort
import com.openweight.app.core.ui.pickerMillisToDate
import com.openweight.app.core.ui.toPickerMillis
import com.openweight.app.diet.data.DayMeals
import com.openweight.app.diet.data.Macros
import com.openweight.app.diet.data.MealPlanRepository
import com.openweight.app.diet.data.PlannedMeal
import com.openweight.app.diet.formatGrams
import com.openweight.app.diet.formatKcal
import com.openweight.app.diet.formatNumber
import com.openweight.app.home.WeekPlan
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class CalendarState(
  val today: LocalDate,
  val month: YearMonth,
  val selected: LocalDate,
  /** Every day of the shown month. */
  val days: List<DayMeals> = emptyList(),
) {
  val selectedDay: DayMeals
    get() = days.firstOrNull { it.date == selected } ?: DayMeals(selected, emptyList())
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CalendarViewModel
@Inject
constructor(private val meals: MealPlanRepository, clock: TimeAndIds, private val saved: SavedStateHandle) : ViewModel() {
  val today: LocalDate = clock.today()
  private val selectedFlow = saved.getStateFlow(KEY_SELECTED, today.toEpochDay())

  val state: StateFlow<CalendarState> =
    selectedFlow
      .map { LocalDate.ofEpochDay(it) }
      .flatMapLatest { selected ->
        val month = YearMonth.from(selected)
        meals.days(month.atDay(1), month.atEndOfMonth()).map { CalendarState(today, month, selected, it) }
      }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CalendarState(today, YearMonth.from(today), today))

  fun select(date: LocalDate) {
    saved[KEY_SELECTED] = date.toEpochDay()
  }

  /** Another month opens on its first day; the current month opens on today. */
  fun showMonth(month: YearMonth) = select(if (month == YearMonth.from(today)) today else month.atDay(1))

  fun setServings(id: String, servings: Double) = viewModelScope.launch { meals.setServings(id, servings) }

  fun remove(id: String) = viewModelScope.launch { meals.removeMeal(id) }

  fun clearDay(date: LocalDate) = viewModelScope.launch { meals.clearDay(date) }

  fun copyDay(from: LocalDate, to: LocalDate, replace: Boolean, done: (Int) -> Unit) =
    viewModelScope.launch { done(meals.copyDay(from, to, replace)) }

  fun copyWeekToNext(day: LocalDate, replace: Boolean, done: (Int) -> Unit) =
    viewModelScope.launch {
      val monday = WeekPlan.mondayOf(day)
      done(meals.copyWeek(monday, monday.plusWeeks(1), replace))
    }

  private companion object {
    const val KEY_SELECTED = "calendar_selected"
  }
}

/** The Diet tab's Calendar panel: a month, and the selected day's meals with their totals. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarPanel(
  snackbar: SnackbarHostState,
  onAddMeal: (LocalDate, MealSlot) -> Unit,
  onOpenRecipe: (String) -> Unit,
  onOpenPlans: () -> Unit,
  viewModel: CalendarViewModel = hiltViewModel(),
) {
  val state by viewModel.state.collectAsStateWithLifecycle()
  var editing by remember { mutableStateOf<PlannedMeal?>(null) }
  var menu by remember { mutableStateOf(false) }
  var copyingDay by remember { mutableStateOf(false) }
  var copyTarget by remember { mutableStateOf<LocalDate?>(null) }
  var copyingWeek by remember { mutableStateOf(false) }
  var confirmClear by remember { mutableStateOf(false) }
  val resources = LocalResources.current
  val scope = rememberCoroutineScope()
  val copied: (Int) -> Unit = { n -> scope.launch { snackbar.showSnackbar(resources.getQuantityString(R.plurals.meals_copied, n, n)) } }
  val day = state.selectedDay

  LazyColumn(Modifier.fillMaxSize().testTag("calendar"), contentPadding = PaddingValues(bottom = 24.dp)) {
    item {
      ListItem(
        modifier = Modifier.clickable(onClick = onOpenPlans).testTag("open_plans"),
        headlineContent = { Text(stringResource(R.string.diet_plans)) },
        supportingContent = { Text(stringResource(R.string.diet_plans_hint)) },
      )
      HorizontalDivider()
    }
    item { MonthGrid(state, onSelect = viewModel::select, onMonth = viewModel::showMonth) }
    item {
      Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
          Text(
            day.date.formatDayShort() + if (day.date == state.today) " · " + stringResource(R.string.home_today) else "",
            style = MaterialTheme.typography.titleMedium,
          )
          if (day.meals.isNotEmpty()) DayTotals(day.total, day.complete)
        }
        Box {
          IconButton(onClick = { menu = true }, modifier = Modifier.testTag("day_menu")) {
            Icon(Icons.Filled.MoreVert, stringResource(R.string.action_more))
          }
          DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text(stringResource(R.string.meal_copy_day)) }, onClick = { menu = false; copyingDay = true })
            DropdownMenuItem(text = { Text(stringResource(R.string.meal_copy_week)) }, onClick = { menu = false; copyingWeek = true })
            DropdownMenuItem(text = { Text(stringResource(R.string.meal_clear_day)) }, onClick = { menu = false; confirmClear = true })
          }
        }
      }
    }
    MealSlot.entries.forEach { slot ->
      item(key = "slot-${slot.name}") {
        SlotSection(
          slot = slot,
          meals = day.meals.filter { it.slot == slot },
          onMeal = { editing = it },
          onAdd = { onAddMeal(day.date, slot) },
        )
      }
    }
  }

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
  if (copyingDay) {
    val picker = rememberDatePickerState(initialSelectedDateMillis = day.date.plusDays(1).toPickerMillis())
    DatePickerDialog(
      onDismissRequest = { copyingDay = false },
      confirmButton = {
        TextButton(onClick = {
          copyingDay = false
          picker.selectedDateMillis?.let(::pickerMillisToDate)?.takeIf { it != day.date }?.let { copyTarget = it }
        }) { Text(stringResource(R.string.action_ok)) }
      },
      dismissButton = { TextButton(onClick = { copyingDay = false }) { Text(stringResource(R.string.action_cancel)) } },
    ) { DatePicker(state = picker, title = { Text(stringResource(R.string.meal_copy_day_to), Modifier.padding(16.dp)) }) }
  }
  copyTarget?.let { target ->
    AddOrReplaceDialog(
      title = stringResource(R.string.meal_copy_day_title, day.date.formatDayShort(), target.formatDayShort()),
      onDismiss = { copyTarget = null },
      onChoose = { replace ->
        copyTarget = null
        viewModel.copyDay(day.date, target, replace, copied)
      },
    )
  }
  if (copyingWeek) {
    AddOrReplaceDialog(
      title = stringResource(R.string.meal_copy_week_title),
      onDismiss = { copyingWeek = false },
      onChoose = { replace ->
        copyingWeek = false
        viewModel.copyWeekToNext(day.date, replace, copied)
      },
    )
  }
  if (confirmClear) {
    AlertDialog(
      onDismissRequest = { confirmClear = false },
      title = { Text(stringResource(R.string.meal_clear_day_title, day.date.formatDayShort())) },
      confirmButton = { TextButton(onClick = { confirmClear = false; viewModel.clearDay(day.date) }) { Text(stringResource(R.string.meal_clear_day)) } },
      dismissButton = { TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.action_cancel)) } },
    )
  }
}

@Composable
fun AddOrReplaceDialog(title: String, onDismiss: () -> Unit, onChoose: (replace: Boolean) -> Unit) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(title) },
    text = { Text(stringResource(R.string.meal_add_or_replace)) },
    confirmButton = {
      Row {
        TextButton(onClick = { onChoose(true) }, modifier = Modifier.testTag("choose_replace")) { Text(stringResource(R.string.meal_replace)) }
        TextButton(onClick = { onChoose(false) }, modifier = Modifier.testTag("choose_add")) { Text(stringResource(R.string.action_add)) }
      }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
  )
}

@Composable
fun DayTotals(total: Macros, complete: Boolean) {
  Text(
    stringResource(R.string.day_totals, formatKcal(total.kcal), formatGrams(total.protein), formatGrams(total.carbs), formatGrams(total.fat)) +
      if (complete) "" else " *",
    style = MaterialTheme.typography.bodySmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = Modifier.testTag("day_totals"),
  )
}

@Composable
private fun SlotSection(slot: MealSlot, meals: List<PlannedMeal>, onMeal: (PlannedMeal) -> Unit, onAdd: () -> Unit) {
  Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text(slotLabel(slot), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
      TextButton(onClick = onAdd, modifier = Modifier.testTag("add_${slot.name}")) {
        Icon(Icons.Filled.Add, contentDescription = null)
        Text(stringResource(R.string.action_add))
      }
    }
    meals.forEach { m -> MealRow(m, onClick = { onMeal(m) }) }
    HorizontalDivider(Modifier.padding(top = 4.dp))
  }
}

@Composable
fun MealRow(m: PlannedMeal, onClick: () -> Unit, modifier: Modifier = Modifier) {
  Row(
    Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick).padding(vertical = 8.dp).then(modifier).testTag("meal_${m.recipeName}"),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Column(Modifier.weight(1f)) {
      Text(m.recipeName, maxLines = 1, overflow = TextOverflow.Ellipsis)
      Text(
        androidx.compose.ui.res.pluralStringResource(R.plurals.servings_decimal, if (m.servings == 1.0) 1 else 2, formatNumber(m.servings)),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    Text(formatKcal(m.macros.kcal) + if (m.complete) "" else "*", fontWeight = FontWeight.SemiBold)
  }
}

@Composable
private fun MonthGrid(state: CalendarState, onSelect: (LocalDate) -> Unit, onMonth: (YearMonth) -> Unit) {
  val locale = LocalConfiguration.current.locales[0]
  val withMeals = state.days.filter { it.meals.isNotEmpty() }.map { it.date }.toSet()
  Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      IconButton(onClick = { onMonth(state.month.minusMonths(1)) }, modifier = Modifier.testTag("previous_month")) {
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, stringResource(R.string.calendar_previous_month))
      }
      Text(
        state.month.month.getDisplayName(TextStyle.FULL_STANDALONE, locale).replaceFirstChar { it.titlecase(locale) } + " " + state.month.year,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.weight(1f).testTag("month_title"),
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
      )
      IconButton(onClick = { onMonth(state.month.plusMonths(1)) }, modifier = Modifier.testTag("next_month")) {
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, stringResource(R.string.calendar_next_month))
      }
    }
    Row(Modifier.fillMaxWidth()) {
      java.time.DayOfWeek.entries.forEach { d ->
        Text(
          d.getDisplayName(TextStyle.NARROW, locale),
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          textAlign = androidx.compose.ui.text.style.TextAlign.Center,
          modifier = Modifier.weight(1f),
        )
      }
    }
    val first = state.month.atDay(1)
    val leading = first.dayOfWeek.value - 1 // Monday = 0 empty cells
    val cells = List(leading) { null } + (1..state.month.lengthOfMonth()).map { state.month.atDay(it) }
    cells.chunked(7).forEach { week ->
      Row(Modifier.fillMaxWidth()) {
        week.forEach { date -> DayCell(date, state, withMeals, onSelect, Modifier.weight(1f)) }
        repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
      }
    }
  }
}

@Composable
private fun DayCell(date: LocalDate?, state: CalendarState, withMeals: Set<LocalDate>, onSelect: (LocalDate) -> Unit, modifier: Modifier) {
  if (date == null) {
    Spacer(modifier)
    return
  }
  val selected = date == state.selected
  val today = date == state.today
  Column(
    modifier
      .aspectRatio(1f)
      .padding(2.dp)
      .clip(RoundedCornerShape(12.dp))
      .then(if (selected) Modifier.background(MaterialTheme.colorScheme.secondaryContainer) else Modifier)
      .clickable { onSelect(date) }
      .testTag("cal_$date"),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center,
  ) {
    Box(
      contentAlignment = Alignment.Center,
      modifier = Modifier.size(28.dp).clip(CircleShape).then(if (today) Modifier.background(MaterialTheme.colorScheme.primary) else Modifier),
    ) {
      Text(
        date.dayOfMonth.toString(),
        style = MaterialTheme.typography.bodyMedium,
        color = if (today) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
      )
    }
    Box(
      Modifier.padding(top = 2.dp).size(5.dp).clip(CircleShape)
        .background(if (date in withMeals) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.surface.copy(alpha = 0f))
    )
  }
}
