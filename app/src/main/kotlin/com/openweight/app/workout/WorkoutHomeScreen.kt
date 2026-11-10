package com.openweight.app.workout

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.openweight.app.core.ui.MenuAccent
import com.openweight.app.core.ui.MenuDivider
import com.openweight.app.core.ui.MenuRow
import com.openweight.app.core.ui.MenuSection
import com.openweight.app.core.ui.ScreenScaffold
import com.openweight.app.workout.blocks.TrainingBlockRepository
import com.openweight.app.workout.data.WorkoutRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** Null until loaded, so the menu never flashes "0 workouts". */
data class WorkoutHomeCounts(val blocks: Int, val workouts: Int, val exercises: Int)

@HiltViewModel
class WorkoutHomeViewModel
@Inject
constructor(workouts: WorkoutRepository, blocks: TrainingBlockRepository) : ViewModel() {
  val counts: StateFlow<WorkoutHomeCounts?> =
    combine(blocks.blocks(), workouts.workouts(), workouts.exercises()) { b, w, e ->
        WorkoutHomeCounts(b.size, w.size, e.size)
      }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

/** Workout tab: where blocks, workouts and exercises are managed. What's planned lives on Home. */
@Composable
fun WorkoutHomeScreen(
  onOpenBlocks: () -> Unit,
  onOpenWorkouts: () -> Unit,
  onOpenExercises: () -> Unit,
  modifier: Modifier = Modifier,
  onOpenHistory: () -> Unit = {},
  onOpenWeight: () -> Unit = {},
  onOpenPowerLevel: () -> Unit = {},
  viewModel: WorkoutHomeViewModel = hiltViewModel(),
) {
  val counts by viewModel.counts.collectAsStateWithLifecycle()
  ScreenScaffold(title = stringResource(R.string.tab_workout), modifier = modifier) { padding ->
    Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) {
      MenuSection(stringResource(R.string.workout_section_plan)) {
        MenuRow(
          R.drawable.ic_menu_blocks,
          stringResource(R.string.workout_blocks),
          Modifier.testTag("open_blocks"),
          subtitle = counts?.let { pluralStringResource(R.plurals.block_count, it.blocks, it.blocks) },
          onClick = onOpenBlocks,
        )
        MenuDivider()
        MenuRow(
          R.drawable.ic_menu_workouts,
          stringResource(R.string.workout_workouts),
          Modifier.testTag("open_workouts"),
          subtitle = counts?.let { pluralStringResource(R.plurals.workout_count, it.workouts, it.workouts) },
          onClick = onOpenWorkouts,
        )
        MenuDivider()
        MenuRow(
          R.drawable.ic_menu_exercises,
          stringResource(R.string.workout_exercises),
          Modifier.testTag("open_exercises"),
          subtitle = counts?.let { pluralStringResource(R.plurals.exercise_count, it.exercises, it.exercises) },
          onClick = onOpenExercises,
        )
      }
      MenuSection(stringResource(R.string.workout_section_progress)) {
        MenuRow(
          R.drawable.ic_menu_history,
          stringResource(R.string.history_title),
          Modifier.testTag("open_history"),
          subtitle = stringResource(R.string.history_hint),
          accent = MenuAccent.TERTIARY,
          onClick = onOpenHistory,
        )
        MenuDivider()
        MenuRow(
          R.drawable.ic_menu_weight,
          stringResource(R.string.weight_title),
          Modifier.testTag("open_weight"),
          subtitle = stringResource(R.string.weight_hint),
          accent = MenuAccent.TERTIARY,
          onClick = onOpenWeight,
        )
        MenuDivider()
        MenuRow(
          R.drawable.ic_menu_power,
          stringResource(R.string.power_title),
          Modifier.testTag("open_power_level"),
          subtitle = stringResource(R.string.power_hint),
          accent = MenuAccent.TERTIARY,
          onClick = onOpenPowerLevel,
        )
      }
    }
  }
}
