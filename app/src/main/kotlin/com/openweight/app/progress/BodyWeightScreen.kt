package com.openweight.app.progress

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.openweight.app.core.Today
import com.openweight.app.core.model.WeightUnit
import com.openweight.app.core.model.format
import com.openweight.app.core.model.formatNumber
import com.openweight.app.core.model.fromKg
import com.openweight.app.core.model.label
import com.openweight.app.core.model.toKg
import com.openweight.app.core.ui.CardListItem
import com.openweight.app.core.ui.EmptyState
import com.openweight.app.core.ui.LineChart
import com.openweight.app.core.ui.ScreenScaffold
import com.openweight.app.core.ui.formatDayShort
import com.openweight.app.diet.data.DayMeals
import com.openweight.app.diet.data.MealPlanRepository
import com.openweight.app.diet.formatGrams
import com.openweight.app.diet.formatKcal
import com.openweight.app.diet.parseNumber
import com.openweight.app.home.WeekPlan
import com.openweight.app.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlin.math.abs
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class BodyWeightState(
  val loading: Boolean = true,
  val today: LocalDate,
  val entries: List<WeighIn> = emptyList(),
  val trend: WeightTrend = WeightTrend(emptyList(), null, null),
  val unit: WeightUnit = WeightUnit.KG,
  /** This week's days so far that have meals ticked as eaten. */
  val eatenDays: List<DayMeals> = emptyList(),
  val kcalTarget: Int? = null,
  val proteinTarget: Int? = null,
) {
  val todays: WeighIn?
    get() = entries.lastOrNull { it.date == today }
}

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class BodyWeightViewModel
@Inject
constructor(private val repository: BodyWeightRepository, meals: MealPlanRepository, settings: SettingsRepository, today: Today) : ViewModel() {
  val state: StateFlow<BodyWeightState> =
    combine(
        repository.entries(),
        settings.settings,
        today.date,
        today.date.flatMapLatest { day -> meals.days(WeekPlan.mondayOf(day), day) },
      ) { entries, s, day, week ->
        BodyWeightState(false, day, entries, WeightRules.trend(entries), s.weightUnit, week.filter { it.anyEaten }, s.kcalTarget, s.proteinTarget)
      }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BodyWeightState(today = today.date.value))

  fun log(date: LocalDate, kg: Double) = viewModelScope.launch { repository.log(date, kg) }

  fun delete(id: String) = viewModelScope.launch { repository.delete(id) }
}

/** Today's weigh-in, the weekly trend with its two-week change, and every weigh-in. */
@Composable
fun BodyWeightRoute(onBack: () -> Unit, viewModel: BodyWeightViewModel = hiltViewModel()) {
  val state by viewModel.state.collectAsStateWithLifecycle()
  var editing by remember { mutableStateOf<WeighIn?>(null) }
  val unit = state.unit
  ScreenScaffold(title = stringResource(R.string.weight_title), onBack = onBack) { padding ->
    if (state.loading) return@ScreenScaffold
    LazyColumn(
      Modifier.fillMaxSize().padding(padding).testTag("weight_list"),
      contentPadding = PaddingValues(bottom = 24.dp),
    ) {
      item { TodayEntry(state, onSave = { viewModel.log(state.today, it) }) }
      item { TrendCard(state.trend, unit) }
      item { WeekEatenCard(state) }
      if (state.entries.isEmpty()) {
        item { EmptyState(stringResource(R.string.weight_empty), hint = stringResource(R.string.weight_empty_hint)) }
      } else {
        item {
          Text(
            stringResource(R.string.weight_all),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
          )
        }
        val shown = state.entries.reversed()
        items(shown, key = { it.id }) { e ->
          CardListItem(shown.indexOf(e), shown.size) {
            ListItem(
              modifier = Modifier.clickable { editing = e }.testTag("weight_${e.date}"),
              headlineContent = { Text(unit.format(e.weightKg)) },
              supportingContent = { Text(e.date.formatDayShort()) },
            )
          }
        }
      }
    }
  }
  editing?.let { e ->
    WeightDialog(
      title = e.date.formatDayShort(),
      initialKg = e.weightKg,
      unit = unit,
      onSave = { kg -> editing = null; viewModel.log(e.date, kg) },
      onDelete = { editing = null; viewModel.delete(e.id) },
      onDismiss = { editing = null },
    )
  }
}

@Composable
private fun TodayEntry(state: BodyWeightState, onSave: (Double) -> Unit) {
  val unit = state.unit
  // Prefilled with today's weigh-in, or the last one, so a small change is a small edit.
  val start = (state.todays ?: state.entries.lastOrNull())?.weightKg?.let(unit::formatNumber).orEmpty()
  var text by rememberSaveable(start) { mutableStateOf(start) }
  var error by remember { mutableStateOf(false) }
  Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Text(
      stringResource(if (state.todays == null) R.string.weight_today else R.string.weight_today_logged),
      style = MaterialTheme.typography.titleMedium,
    )
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
      OutlinedTextField(
        value = text,
        onValueChange = { text = it; error = false },
        label = { Text(stringResource(R.string.session_weight, unit.label)) },
        isError = error,
        supportingText = { if (error) Text(stringResource(R.string.weight_error, unit.format(WeightRules.MIN_KG), unit.format(WeightRules.MAX_KG))) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.weight(1f).testTag("weight_input"),
      )
      Button(
        onClick = {
          val kg = parseNumber(text)?.let(unit::toKg)
          if (kg == null || kg !in WeightRules.MIN_KG..WeightRules.MAX_KG) error = true else onSave(kg)
        },
        modifier = Modifier.testTag("weight_save"),
      ) {
        Text(stringResource(R.string.action_save))
      }
    }
  }
}

@Composable
private fun TrendCard(trend: WeightTrend, unit: WeightUnit) {
  val latest = trend.latest ?: return
  Card(
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).testTag("weight_trend"),
  ) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
      Text(stringResource(R.string.weight_week_average, latest.monday.formatDayShort(), unit.format(latest.averageKg)), style = MaterialTheme.typography.titleMedium)
      val change = trend.changeKg
      Text(
        if (change == null) stringResource(R.string.weight_change_unknown)
        else {
          val amount = unit.format(abs(change))
          when {
            abs(unit.fromKg(change)) < 0.05 -> stringResource(R.string.weight_change_flat)
            change > 0 -> stringResource(R.string.weight_change_up, amount)
            else -> stringResource(R.string.weight_change_down, amount)
          }
        },
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.testTag("weight_change"),
      )
      if (trend.weeks.size >= 2) {
        val values = trend.weeks.map { it.averageKg }
        LineChart(
          values = values,
          minLabel = unit.format(values.min()),
          maxLabel = unit.format(values.max()),
          firstLabel = trend.weeks.first().monday.formatDayShort(),
          lastLabel = trend.weeks.last().monday.formatDayShort(),
          description = stringResource(R.string.weight_chart_description, unit.format(values.first()), unit.format(values.last())),
          modifier = Modifier.padding(top = 8.dp),
        )
      }
    }
  }
}

/** Average calories and protein over this week's days with eaten meals, next to the weight trend. */
@Composable
private fun WeekEatenCard(state: BodyWeightState) {
  val days = state.eatenDays
  if (days.isEmpty()) return
  val kcal = days.map { it.eaten.kcal }.average()
  val protein = days.map { it.eaten.protein }.average()
  Card(
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp).testTag("week_eaten"),
  ) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
      Text(stringResource(R.string.week_eaten_title), style = MaterialTheme.typography.titleMedium)
      Text(stringResource(R.string.week_eaten, formatKcal(kcal), formatGrams(protein)), modifier = Modifier.testTag("week_eaten_text"))
      Text(
        pluralStringResource(R.plurals.week_eaten_days, days.size, days.size),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      val target =
        listOfNotNull(state.kcalTarget?.let { formatKcal(it.toDouble()) }, state.proteinTarget?.let { formatGrams(it.toDouble()) }).joinToString(" · ")
      if (target.isNotEmpty()) {
        Text(stringResource(R.string.week_eaten_target, target), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
    }
  }
}

@Composable
private fun WeightDialog(
  title: String,
  initialKg: Double,
  unit: WeightUnit,
  onSave: (Double) -> Unit,
  onDelete: () -> Unit,
  onDismiss: () -> Unit,
) {
  var text by remember { mutableStateOf(unit.formatNumber(initialKg)) }
  var error by remember { mutableStateOf(false) }
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(title) },
    text = {
      OutlinedTextField(
        value = text,
        onValueChange = { text = it; error = false },
        label = { Text(stringResource(R.string.session_weight, unit.label)) },
        isError = error,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth().testTag("weight_edit_input"),
      )
    },
    confirmButton = {
      TextButton(
        onClick = {
          val kg = parseNumber(text)?.let(unit::toKg)
          if (kg == null || kg !in WeightRules.MIN_KG..WeightRules.MAX_KG) error = true else onSave(kg)
        },
        modifier = Modifier.testTag("weight_edit_save"),
      ) {
        Text(stringResource(R.string.action_save))
      }
    },
    dismissButton = {
      Row {
        TextButton(onClick = onDelete, modifier = Modifier.testTag("weight_delete")) { Text(stringResource(R.string.action_delete)) }
        TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
      }
    },
  )
}
