package com.openweight.app.workout

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.openweight.app.R
import com.openweight.app.core.TimeAndIds
import com.openweight.app.core.model.formatReps
import com.openweight.app.core.ui.ScreenScaffold
import com.openweight.app.core.ui.formatDayShort
import com.openweight.app.core.ui.formatMedium
import com.openweight.app.workout.blocks.PlannedDay
import com.openweight.app.workout.blocks.ScheduleResolver
import com.openweight.app.workout.blocks.TrainingBlockRepository
import com.openweight.app.workout.data.WorkoutDetail
import com.openweight.app.workout.data.WorkoutRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class UpcomingDay(val date: LocalDate, val workoutName: String?, val inBlock: Boolean)

data class TodayState(
  val loading: Boolean = true,
  val today: PlannedDay? = null,
  /** Today's workout with its exercises, when one is planned. */
  val workout: WorkoutDetail? = null,
  val upcoming: List<UpcomingDay> = emptyList(),
  /** When no block is active today: the start of the next one, if any. */
  val nextBlockStart: LocalDate? = null,
  val blockCount: Int = 0,
  val workoutCount: Int = 0,
  val exerciseCount: Int = 0,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class WorkoutHomeViewModel
@Inject
constructor(
  workouts: WorkoutRepository,
  blocks: TrainingBlockRepository,
  clock: TimeAndIds,
) : ViewModel() {
  private val today = clock.today()

  private val plan =
    combine(blocks.scheduledBlocks(), workouts.workouts()) { scheduled, all ->
      val names = all.associate { it.id to it.name }
      val days = ScheduleResolver.plannedDays(scheduled, today, UPCOMING_DAYS + 1)
      Triple(
        days.first(),
        days.drop(1).map { d ->
          UpcomingDay(
            d.date,
            (d as? PlannedDay.InBlock)?.workoutId?.let(names::get),
            d is PlannedDay.InBlock,
          )
        },
        scheduled.filter { it.startDate.isAfter(today) }.minOfOrNull { it.startDate },
      )
    }

  private val todayWorkout =
    plan.flatMapLatest { (day, _, _) ->
      val id = (day as? PlannedDay.InBlock)?.workoutId
      if (id == null) flowOf(null) else workouts.workout(id)
    }

  private val counts =
    combine(blocks.blocks(), workouts.workouts(), workouts.exercises()) { b, w, e ->
      Triple(b.size, w.size, e.size)
    }

  val state: StateFlow<TodayState> =
    combine(plan, todayWorkout, counts) { (day, upcoming, next), workout, (b, w, e) ->
        TodayState(
          loading = false,
          today = day,
          workout = workout,
          upcoming = upcoming,
          nextBlockStart = next,
          blockCount = b,
          workoutCount = w,
          exerciseCount = e,
        )
      }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayState())

  private companion object {
    const val UPCOMING_DAYS = 6
  }
}

/** Workout tab home: today's plan on top, then the coming days and the entry points. */
@Composable
fun WorkoutHomeScreen(
  onOpenWorkout: (String) -> Unit,
  onOpenBlocks: () -> Unit,
  onOpenWorkouts: () -> Unit,
  onOpenExercises: () -> Unit,
  modifier: Modifier = Modifier,
  viewModel: WorkoutHomeViewModel = hiltViewModel(),
) {
  val state by viewModel.state.collectAsStateWithLifecycle()
  ScreenScaffold(title = stringResource(R.string.tab_workout), modifier = modifier) { padding ->
    Column(
      modifier =
        Modifier.fillMaxSize()
          .padding(padding)
          .verticalScroll(rememberScrollState())
          .padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      if (!state.loading) TodayCard(state, onOpenWorkout)
      if (state.upcoming.any { it.inBlock }) ComingUp(state.upcoming)
      Column {
        ListItem(
          modifier = Modifier.clickable(onClick = onOpenBlocks).testTag("open_blocks"),
          headlineContent = { Text(stringResource(R.string.workout_blocks)) },
          supportingContent = {
            Text(pluralStringResource(R.plurals.block_count, state.blockCount, state.blockCount))
          },
        )
        HorizontalDivider()
        ListItem(
          modifier = Modifier.clickable(onClick = onOpenWorkouts).testTag("open_workouts"),
          headlineContent = { Text(stringResource(R.string.workout_workouts)) },
          supportingContent = {
            Text(pluralStringResource(R.plurals.workout_count, state.workoutCount, state.workoutCount))
          },
        )
        HorizontalDivider()
        ListItem(
          modifier = Modifier.clickable(onClick = onOpenExercises).testTag("open_exercises"),
          headlineContent = { Text(stringResource(R.string.workout_exercises)) },
          supportingContent = {
            Text(pluralStringResource(R.plurals.exercise_count, state.exerciseCount, state.exerciseCount))
          },
        )
      }
    }
  }
}

@Composable
private fun TodayCard(state: TodayState, onOpenWorkout: (String) -> Unit) {
  val day = state.today
  Card(modifier = Modifier.fillMaxWidth().testTag("today_card")) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Text(
        stringResource(R.string.workout_today_date, day?.date?.formatDayShort().orEmpty()),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
      )
      when {
        day !is PlannedDay.InBlock -> {
          Text(stringResource(R.string.workout_today), style = MaterialTheme.typography.titleLarge)
          Text(
            state.nextBlockStart?.let { stringResource(R.string.workout_today_next_block, it.formatMedium()) }
              ?: stringResource(R.string.workout_today_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        day.workoutId == null -> {
          Text(stringResource(R.string.workout_rest_day), style = MaterialTheme.typography.titleLarge)
          BlockLine(day)
        }
        else -> {
          Text(
            state.workout?.name.orEmpty(),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.testTag("today_workout"),
          )
          BlockLine(day)
          state.workout?.entries?.forEach { entry ->
            Row(Modifier.fillMaxWidth()) {
              Text(
                entry.exerciseName,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
              )
              val first = entry.sets.firstOrNull()
              if (first != null) {
                Text(
                  "${entry.sets.size} × ${formatReps(first.repsMin, first.repsMax)}",
                  style = MaterialTheme.typography.bodyMedium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
          }
          Button(onClick = { onOpenWorkout(day.workoutId) }, modifier = Modifier.testTag("open_today_workout")) {
            Text(stringResource(R.string.workout_open))
          }
        }
      }
    }
  }
}

@Composable
private fun BlockLine(day: PlannedDay.InBlock) {
  Text(
    stringResource(R.string.block_week_of, day.blockName, day.week, day.totalWeeks),
    style = MaterialTheme.typography.bodyMedium,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
  )
}

@Composable
private fun ComingUp(days: List<UpcomingDay>) {
  Column {
    Text(
      stringResource(R.string.workout_coming_up),
      style = MaterialTheme.typography.titleSmall,
      color = MaterialTheme.colorScheme.primary,
      modifier = Modifier.padding(bottom = 4.dp),
    )
    days.forEach { d ->
      Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(d.date.formatDayShort(), modifier = Modifier.weight(0.35f), style = MaterialTheme.typography.bodyMedium)
        Text(
          when {
            !d.inBlock -> stringResource(R.string.workout_no_block)
            d.workoutName == null -> stringResource(R.string.workout_rest)
            else -> d.workoutName
          },
          style = MaterialTheme.typography.bodyMedium,
          color =
            if (d.workoutName == null) MaterialTheme.colorScheme.onSurfaceVariant
            else MaterialTheme.colorScheme.onSurface,
          modifier = Modifier.weight(0.65f),
        )
      }
    }
  }
}
