package com.openweight.app.workout

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.openweight.app.core.ui.ScreenScaffold
import com.openweight.app.workout.blocks.TrainingBlockRepository
import com.openweight.app.workout.data.WorkoutRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class WorkoutHomeCounts(val blocks: Int = 0, val workouts: Int = 0, val exercises: Int = 0)

@HiltViewModel
class WorkoutHomeViewModel
@Inject
constructor(workouts: WorkoutRepository, blocks: TrainingBlockRepository) : ViewModel() {
  val counts: StateFlow<WorkoutHomeCounts> =
    combine(blocks.blocks(), workouts.workouts(), workouts.exercises()) { b, w, e ->
        WorkoutHomeCounts(b.size, w.size, e.size)
      }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WorkoutHomeCounts())
}

/** Workout tab: where blocks, workouts and exercises are managed. What's planned lives on Home. */
@Composable
fun WorkoutHomeScreen(
  onOpenBlocks: () -> Unit,
  onOpenWorkouts: () -> Unit,
  onOpenExercises: () -> Unit,
  modifier: Modifier = Modifier,
  onOpenHistory: () -> Unit = {},
  viewModel: WorkoutHomeViewModel = hiltViewModel(),
) {
  val counts by viewModel.counts.collectAsStateWithLifecycle()
  ScreenScaffold(title = stringResource(R.string.tab_workout), modifier = modifier) { padding ->
    Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(vertical = 8.dp)) {
      ListItem(
        modifier = Modifier.clickable(onClick = onOpenBlocks).testTag("open_blocks"),
        headlineContent = { Text(stringResource(R.string.workout_blocks)) },
        supportingContent = { Text(pluralStringResource(R.plurals.block_count, counts.blocks, counts.blocks)) },
      )
      HorizontalDivider()
      ListItem(
        modifier = Modifier.clickable(onClick = onOpenWorkouts).testTag("open_workouts"),
        headlineContent = { Text(stringResource(R.string.workout_workouts)) },
        supportingContent = { Text(pluralStringResource(R.plurals.workout_count, counts.workouts, counts.workouts)) },
      )
      HorizontalDivider()
      ListItem(
        modifier = Modifier.clickable(onClick = onOpenExercises).testTag("open_exercises"),
        headlineContent = { Text(stringResource(R.string.workout_exercises)) },
        supportingContent = {
          Text(pluralStringResource(R.plurals.exercise_count, counts.exercises, counts.exercises))
        },
      )
      HorizontalDivider()
      ListItem(
        modifier = Modifier.clickable(onClick = onOpenHistory).testTag("open_history"),
        headlineContent = { Text(stringResource(R.string.history_title)) },
        supportingContent = { Text(stringResource(R.string.history_hint)) },
      )
    }
  }
}
