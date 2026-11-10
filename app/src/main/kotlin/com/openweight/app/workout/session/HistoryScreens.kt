package com.openweight.app.workout.session

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.openweight.app.R
import com.openweight.app.core.model.WeightUnit
import com.openweight.app.core.model.format
import com.openweight.app.core.ui.EmptyState
import com.openweight.app.core.ui.LineChart
import com.openweight.app.core.ui.ScreenScaffold
import com.openweight.app.core.ui.formatDayShort
import com.openweight.app.core.ui.formatMedium
import com.openweight.app.settings.SettingsRepository
import com.openweight.app.workout.data.WorkoutRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** "60 kg × 10", or "10 reps" for bodyweight. */
@Composable
internal fun setText(set: LoggedSet, unit: WeightUnit): String =
  if (set.weightKg == null) pluralStringResource(R.plurals.reps_only, set.reps, set.reps)
  else stringResource(R.string.session_set_value, unit.format(set.weightKg), set.reps)

// ---- History list

data class HistoryState(val loading: Boolean = true, val sessions: List<SessionSummary> = emptyList(), val unit: WeightUnit = WeightUnit.KG)

@HiltViewModel
class HistoryViewModel @Inject constructor(sessions: SessionRepository, settings: SettingsRepository) : ViewModel() {
  val state: StateFlow<HistoryState> =
    combine(sessions.history(), settings.settings) { list, s -> HistoryState(false, list, s.weightUnit) }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryState())
}

/** Every finished session, newest first. */
@Composable
fun HistoryRoute(onBack: () -> Unit, onOpen: (sessionId: String) -> Unit, viewModel: HistoryViewModel = hiltViewModel()) {
  val state by viewModel.state.collectAsStateWithLifecycle()
  ScreenScaffold(title = stringResource(R.string.history_title), onBack = onBack) { padding ->
    Box(Modifier.fillMaxSize().padding(padding)) {
      when {
        state.loading -> Unit
        state.sessions.isEmpty() -> EmptyState(stringResource(R.string.history_empty), hint = stringResource(R.string.history_empty_hint))
        else ->
          LazyColumn(Modifier.fillMaxSize().testTag("history_list"), contentPadding = PaddingValues(bottom = 24.dp)) {
            items(state.sessions, key = { it.id }) { s ->
              ListItem(
                modifier = Modifier.clickable { onOpen(s.id) }.testTag("history_${s.id}"),
                overlineContent = { Text(s.plannedDate.formatDayShort()) },
                headlineContent = { Text(s.workoutName) },
                supportingContent = {
                  val minutes = s.duration.toMinutes().toInt()
                  Text(
                    listOf(
                        pluralStringResource(R.plurals.session_done_minutes, minutes, minutes),
                        stringResource(R.string.session_done_sets, s.doneCount, s.setCount),
                        state.unit.format(s.volumeKg),
                      )
                      .joinToString(" · ")
                  )
                },
              )
              HorizontalDivider()
            }
          }
      }
    }
  }
}

// ---- One session

@HiltViewModel(assistedFactory = SessionDetailViewModel.Factory::class)
class SessionDetailViewModel
@AssistedInject
constructor(private val sessions: SessionRepository, settings: SettingsRepository, @Assisted private val sessionId: String) : ViewModel() {
  @AssistedFactory
  interface Factory {
    fun create(sessionId: String): SessionDetailViewModel
  }

  val state: StateFlow<Pair<Session?, WeightUnit>?> =
    combine(sessions.session(sessionId), settings.settings) { s, set -> s to set.weightUnit }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

  fun delete() = viewModelScope.launch { sessions.deleteFinished(sessionId) }
}

/** A finished session: its totals and every set, done or skipped. */
@Composable
fun SessionDetailRoute(
  sessionId: String,
  onBack: () -> Unit,
  onOpenExercise: (exerciseId: String) -> Unit,
  viewModel: SessionDetailViewModel =
    hiltViewModel<SessionDetailViewModel, SessionDetailViewModel.Factory>(key = "session-detail-$sessionId") { it.create(sessionId) },
) {
  val state by viewModel.state.collectAsStateWithLifecycle()
  val session = state?.first
  val unit = state?.second ?: WeightUnit.KG
  LaunchedEffect(state) { if (state != null && session == null) onBack() }
  var menu by remember { mutableStateOf(false) }
  var confirmDelete by remember { mutableStateOf(false) }
  ScreenScaffold(
    title = session?.workoutName.orEmpty(),
    onBack = onBack,
    actions = {
      if (session != null) {
        Box {
          IconButton(onClick = { menu = true }, modifier = Modifier.testTag("session_detail_menu")) {
            Icon(Icons.Filled.MoreVert, stringResource(R.string.action_more))
          }
          DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(
              text = { Text(stringResource(R.string.history_delete)) },
              onClick = { menu = false; confirmDelete = true },
              modifier = Modifier.testTag("delete_session"),
            )
          }
        }
      }
    },
  ) { padding ->
    if (session == null) return@ScreenScaffold
    LazyColumn(
      Modifier.fillMaxSize().padding(padding).testTag("session_detail"),
      contentPadding = PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      item {
        val minutes = session.duration(session.finishedAt ?: session.startedAt).toMinutes().toInt()
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
          Text(session.plannedDate.formatMedium(), style = MaterialTheme.typography.titleMedium)
          Text(
            listOf(
                pluralStringResource(R.plurals.session_done_minutes, minutes, minutes),
                stringResource(R.string.session_done_sets, session.doneCount, session.setCount),
                stringResource(R.string.session_done_volume, unit.format(session.volumeKg)),
              )
              .joinToString(" · "),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
      items(session.exercises, key = { it.position }) { e ->
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), modifier = Modifier.fillMaxWidth()) {
          Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp)) {
            val exerciseId = e.exerciseId
            if (exerciseId != null) {
              TextButton(onClick = { onOpenExercise(exerciseId) }, modifier = Modifier.testTag("detail_exercise_${e.position}")) {
                Text(stringResource(R.string.history_exercise_link, e.name), style = MaterialTheme.typography.titleMedium)
              }
            } else {
              Text(e.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(vertical = 8.dp))
            }
            e.sets.forEachIndexed { i, set ->
              val logged = LoggedSet(set.weightKg, set.reps, set.done)
              Text(
                stringResource(R.string.history_set_line, i + 1, if (set.done) setText(logged, unit) else stringResource(R.string.history_skipped)),
                style = MaterialTheme.typography.bodyMedium,
                color = if (set.done) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }
      }
    }
  }
  if (confirmDelete) {
    AlertDialog(
      onDismissRequest = { confirmDelete = false },
      title = { Text(stringResource(R.string.history_delete_title)) },
      text = { Text(stringResource(R.string.history_delete_text)) },
      confirmButton = {
        TextButton(onClick = { confirmDelete = false; viewModel.delete() }, modifier = Modifier.testTag("confirm_delete_session")) {
          Text(stringResource(R.string.action_delete))
        }
      },
      dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.action_cancel)) } },
    )
  }
}

// ---- One exercise over time

data class ExerciseHistoryState(
  val name: String = "",
  val sessions: List<ExerciseSession> = emptyList(),
  val unit: WeightUnit = WeightUnit.KG,
  val loading: Boolean = true,
)

@HiltViewModel(assistedFactory = ExerciseHistoryViewModel.Factory::class)
class ExerciseHistoryViewModel
@AssistedInject
constructor(sessions: SessionRepository, workouts: WorkoutRepository, settings: SettingsRepository, @Assisted exerciseId: String) : ViewModel() {
  @AssistedFactory
  interface Factory {
    fun create(exerciseId: String): ExerciseHistoryViewModel
  }

  val state: StateFlow<ExerciseHistoryState> =
    combine(sessions.exerciseHistory(exerciseId), settings.settings, flowOf(exerciseId)) { list, s, id ->
        ExerciseHistoryState(workouts.getExercise(id)?.name.orEmpty(), list, s.weightUnit, loading = false)
      }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExerciseHistoryState())
}

/** An exercise's best set, a chart of its heaviest set per session, and every session that trained it. */
@Composable
fun ExerciseHistoryRoute(
  exerciseId: String,
  onBack: () -> Unit,
  viewModel: ExerciseHistoryViewModel =
    hiltViewModel<ExerciseHistoryViewModel, ExerciseHistoryViewModel.Factory>(key = "exercise-history-$exerciseId") { it.create(exerciseId) },
) {
  val state by viewModel.state.collectAsStateWithLifecycle()
  val unit = state.unit
  ScreenScaffold(title = state.name, onBack = onBack) { padding ->
    if (state.loading) return@ScreenScaffold
    if (state.sessions.isEmpty()) {
      EmptyState(stringResource(R.string.exercise_history_empty), modifier = Modifier.padding(padding))
      return@ScreenScaffold
    }
    LazyColumn(
      Modifier.fillMaxSize().padding(padding).testTag("exercise_history"),
      contentPadding = PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      item {
        Progress.bestSet(state.sessions)?.let {
          Text(stringResource(R.string.exercise_best, setText(it, unit)), style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("exercise_best"))
        }
      }
      val weighted = state.sessions.filter { it.topWeightKg != null }
      if (weighted.size >= 2) {
        item {
          val values = weighted.map { it.topWeightKg!! }
          Column {
            Text(stringResource(R.string.exercise_chart_title), style = MaterialTheme.typography.labelLarge)
            LineChart(
              values = values,
              minLabel = unit.format(values.min()),
              maxLabel = unit.format(values.max()),
              firstLabel = weighted.first().date.formatDayShort(),
              lastLabel = weighted.last().date.formatDayShort(),
              description =
                pluralStringResource(R.plurals.exercise_chart_description, values.size, values.size, unit.format(values.first()), unit.format(values.last())),
              modifier = Modifier.padding(top = 8.dp).testTag("exercise_chart"),
            )
          }
        }
      }
      items(state.sessions.reversed(), key = { it.sessionId }) { s ->
        Column {
          Text(s.date.formatDayShort(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
          Text(s.sets.map { setText(it, unit) }.joinToString(", "), style = MaterialTheme.typography.bodyMedium)
        }
      }
    }
  }
}
