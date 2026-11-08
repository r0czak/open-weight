package com.openweight.app.workout

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.openweight.app.R
import com.openweight.app.core.database.OpenWeightDatabase
import com.openweight.app.core.ui.ScreenScaffold
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
constructor(repository: WorkoutRepository, db: OpenWeightDatabase) : ViewModel() {
  val counts: StateFlow<WorkoutHomeCounts> =
    combine(
        db.trainingBlockDao().observeCount(),
        repository.workouts(),
        repository.exercises(),
      ) { blocks, workouts, exercises ->
        WorkoutHomeCounts(blocks, workouts.size, exercises.size)
      }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WorkoutHomeCounts())
}

/** Workout tab home: today's plan on top, then the entry points to blocks, workouts, exercises. */
@Composable
fun WorkoutHomeScreen(
  onOpenWorkouts: () -> Unit,
  onOpenExercises: () -> Unit,
  modifier: Modifier = Modifier,
  viewModel: WorkoutHomeViewModel = hiltViewModel(),
) {
  val counts by viewModel.counts.collectAsStateWithLifecycle()
  ScreenScaffold(title = stringResource(R.string.tab_workout), modifier = modifier) { padding ->
    Column(
      modifier =
        Modifier.fillMaxSize()
          .padding(padding)
          .verticalScroll(rememberScrollState())
          .padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text(stringResource(R.string.workout_today), style = MaterialTheme.typography.titleLarge)
          Text(
            stringResource(R.string.workout_today_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
      Column {
        ListItem(
          headlineContent = { Text(stringResource(R.string.workout_blocks)) },
          supportingContent = {
            Text(
              if (counts.blocks == 0) stringResource(R.string.coming_soon)
              else pluralStringResource(R.plurals.blocks_saved, counts.blocks, counts.blocks)
            )
          },
        )
        HorizontalDivider()
        ListItem(
          modifier = Modifier.clickable(onClick = onOpenWorkouts).testTag("open_workouts"),
          headlineContent = { Text(stringResource(R.string.workout_workouts)) },
          supportingContent = {
            Text(pluralStringResource(R.plurals.workout_count, counts.workouts, counts.workouts))
          },
        )
        HorizontalDivider()
        ListItem(
          modifier = Modifier.clickable(onClick = onOpenExercises).testTag("open_exercises"),
          headlineContent = { Text(stringResource(R.string.workout_exercises)) },
          supportingContent = {
            Text(pluralStringResource(R.plurals.exercise_count, counts.exercises, counts.exercises))
          },
        )
      }
    }
  }
}
