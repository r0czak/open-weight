package com.openweight.app.workout.workouts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.openweight.app.R
import com.openweight.app.core.model.WeightUnit
import com.openweight.app.core.ui.ScreenScaffold
import com.openweight.app.core.ui.formatDayShort
import com.openweight.app.settings.SettingsRepository
import com.openweight.app.workout.blocks.PlannedDay
import com.openweight.app.workout.blocks.ScheduleResolver
import com.openweight.app.workout.blocks.TrainingBlockRepository
import com.openweight.app.workout.data.WorkoutDetail
import com.openweight.app.workout.data.WorkoutRepository
import com.openweight.app.workout.session.Session
import com.openweight.app.workout.session.SessionRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface WorkoutOverviewState {
  data object Loading : WorkoutOverviewState

  data object Gone : WorkoutOverviewState

  data class Loaded(
    val workout: WorkoutDetail,
    val unit: WeightUnit,
    val date: LocalDate?,
    /** The block and week on [date], when the workout is opened from a day. */
    val day: PlannedDay.InBlock?,
    /** The session in progress, of this workout or another; only one can be open. */
    val openSession: Session? = null,
  ) : WorkoutOverviewState
}

@HiltViewModel(assistedFactory = WorkoutOverviewViewModel.Factory::class)
class WorkoutOverviewViewModel
@AssistedInject
constructor(
  workouts: WorkoutRepository,
  blocks: TrainingBlockRepository,
  settings: SettingsRepository,
  private val sessions: SessionRepository,
  @Assisted private val workoutId: String,
  @Assisted private val date: LocalDate?,
) : ViewModel() {
  @AssistedFactory
  interface Factory {
    fun create(workoutId: String, date: LocalDate?): WorkoutOverviewViewModel
  }

  val state: StateFlow<WorkoutOverviewState> =
    combine(workouts.workout(workoutId), settings.settings, blocks.scheduledBlocks(), sessions.open()) { w, s, scheduled, open ->
        if (w == null) {
          WorkoutOverviewState.Gone
        } else {
          val day = date?.let { ScheduleResolver.plannedDay(scheduled, it) as? PlannedDay.InBlock }
          WorkoutOverviewState.Loaded(w, s.weightUnit, date, day, open)
        }
      }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WorkoutOverviewState.Loading)

  /** Starts a session for this workout and day, or returns the one already open. */
  fun start(started: (sessionId: String) -> Unit) {
    viewModelScope.launch { started(sessions.start(workoutId, date)) }
  }
}

/**
 * What a workout holds, read-only, as the step before training: "Start" opens a live session
 * (or resumes the open one); editing stays one tap away.
 */
@Composable
fun WorkoutOverviewRoute(
  workoutId: String,
  date: LocalDate?,
  onBack: () -> Unit,
  onEdit: () -> Unit,
  onOpenSession: (sessionId: String) -> Unit,
  viewModel: WorkoutOverviewViewModel =
    hiltViewModel<WorkoutOverviewViewModel, WorkoutOverviewViewModel.Factory>(
      key = "overview-$workoutId-$date"
    ) {
      it.create(workoutId, date)
    },
) {
  val state by viewModel.state.collectAsStateWithLifecycle()
  LaunchedEffect(state) { if (state is WorkoutOverviewState.Gone) onBack() }
  val loaded = state as? WorkoutOverviewState.Loaded

  ScreenScaffold(
    title = loaded?.workout?.name.orEmpty(),
    onBack = onBack,
    actions = {
      IconButton(onClick = onEdit, modifier = Modifier.testTag("edit_workout")) {
        Icon(Icons.Filled.Edit, stringResource(R.string.workout_edit))
      }
    },
  ) { padding ->
    if (loaded == null) return@ScreenScaffold
    val w = loaded.workout
    LazyColumn(
      modifier = Modifier.fillMaxSize().padding(padding),
      contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      item {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
          loaded.date?.let {
            Text(
              it.formatDayShort(),
              style = MaterialTheme.typography.labelLarge,
              color = MaterialTheme.colorScheme.primary,
            )
          }
          loaded.day?.let {
            Text(
              stringResource(R.string.block_week_of, it.blockName, it.week, it.totalWeeks),
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          if (w.notes.isNotBlank()) Text(w.notes, style = MaterialTheme.typography.bodyMedium)
        }
      }
      item {
        val open = loaded.openSession
        val other = open != null && open.workoutId != w.id
        val hasSets = w.entries.any { it.sets.isNotEmpty() }
        Button(
          onClick = { if (open != null) onOpenSession(open.id) else viewModel.start(onOpenSession) },
          enabled = open != null || hasSets,
          modifier = Modifier.fillMaxWidth().testTag("start_workout"),
        ) {
          Icon(Icons.Filled.PlayArrow, contentDescription = null)
          Text(
            stringResource(
              when {
                other -> R.string.workout_open_other_session
                open != null -> R.string.workout_resume
                else -> R.string.workout_start
              }
            ),
            modifier = Modifier.padding(start = 8.dp),
          )
        }
        val note =
          when {
            other -> stringResource(R.string.workout_other_session, open!!.workoutName)
            !hasSets -> stringResource(R.string.workout_start_empty)
            else -> null
          }
        if (note != null) {
          Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
        }
      }
      items(w.entries, key = { it.id }) { entry ->
        Card(
          modifier = Modifier.fillMaxWidth(),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        ) {
          Column(Modifier.padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 8.dp)) {
            Text(
              "${w.entries.indexOf(entry) + 1}. ${entry.exerciseName}",
              style = MaterialTheme.typography.titleMedium,
            )
            if (entry.notes.isNotBlank()) {
              Text(
                entry.notes,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            SetHeader()
            entry.sets.forEachIndexed { i, set ->
              HorizontalDivider(Modifier.padding(end = 12.dp))
              SetRow(i + 1, set, loaded.unit, onClick = null)
            }
          }
        }
      }
    }
  }
}
