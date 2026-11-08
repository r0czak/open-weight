package com.openweight.app.workout.workouts

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.openweight.app.R
import com.openweight.app.core.model.WeightUnit
import com.openweight.app.core.ui.EmptyState
import com.openweight.app.core.ui.ScreenScaffold
import com.openweight.app.settings.SettingsRepository
import com.openweight.app.workout.data.ExerciseListItem
import com.openweight.app.workout.data.SetValues
import com.openweight.app.workout.data.WorkoutRepository
import com.openweight.app.workout.exercise.SearchField
import com.openweight.app.workout.exercise.matches
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PickerState(
  val query: String = "",
  val items: List<ExerciseListItem> = emptyList(),
  val exactMatch: Boolean = false,
  val unit: WeightUnit = WeightUnit.KG,
  /** The exercise chosen, waiting for its sets in the dialog. */
  val chosen: ExerciseListItem? = null,
  val done: Boolean = false,
)

@HiltViewModel(assistedFactory = ExercisePickerViewModel.Factory::class)
class ExercisePickerViewModel
@AssistedInject
constructor(
  private val repository: WorkoutRepository,
  settings: SettingsRepository,
  @Assisted private val workoutId: String,
) : ViewModel() {
  @AssistedFactory
  interface Factory {
    fun create(workoutId: String): ExercisePickerViewModel
  }

  private val local = MutableStateFlow(PickerState())

  val state: StateFlow<PickerState> =
    combine(local, repository.exercises(), settings.settings) { l, all, s ->
        l.copy(
          items = all.filter { it.matches(l.query) },
          exactMatch = all.any { it.name.equals(l.query.trim(), ignoreCase = true) },
          unit = s.weightUnit,
        )
      }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PickerState())

  fun onQueryChange(value: String) = local.update { it.copy(query = value) }

  fun choose(item: ExerciseListItem?) = local.update { it.copy(chosen = item) }

  /** Creates an exercise named like the search text and moves on to its sets. */
  fun createFromQuery() {
    val name = local.value.query.trim()
    if (name.isEmpty()) return
    viewModelScope.launch {
      val id = repository.saveExercise(null, name, "", "", "")
      choose(ExerciseListItem(id, name, "", "", 0))
    }
  }

  fun add(setCount: Int, values: SetValues) {
    val exercise = local.value.chosen ?: return
    viewModelScope.launch {
      repository.addExercise(workoutId, exercise.id, setCount, values)
      local.update { it.copy(chosen = null, done = true) }
    }
  }
}

@Composable
fun ExercisePickerRoute(
  workoutId: String,
  onDone: () -> Unit,
  viewModel: ExercisePickerViewModel =
    hiltViewModel<ExercisePickerViewModel, ExercisePickerViewModel.Factory>(key = "picker-$workoutId") {
      it.create(workoutId)
    },
) {
  val state by viewModel.state.collectAsStateWithLifecycle()
  LaunchedEffect(state.done) { if (state.done) onDone() }

  ScreenScaffold(title = stringResource(R.string.workout_add_exercise), onBack = onDone) { padding ->
    Column(Modifier.fillMaxSize().padding(padding)) {
      SearchField(state.query, viewModel::onQueryChange)
      LazyColumn {
        if (state.query.isNotBlank() && !state.exactMatch) {
          item {
            ListItem(
              modifier = Modifier.clickable(onClick = viewModel::createFromQuery).testTag("create_exercise"),
              leadingContent = { Icon(Icons.Filled.Add, contentDescription = null) },
              headlineContent = { Text(stringResource(R.string.exercise_create_named, state.query.trim())) },
            )
            HorizontalDivider()
          }
        }
        items(state.items, key = { it.id }) { item ->
          ListItem(
            modifier = Modifier.clickable { viewModel.choose(item) },
            headlineContent = { Text(item.name) },
            supportingContent = {
              val details = listOf(item.muscleGroup, item.equipment).filter { it.isNotBlank() }
              if (details.isNotEmpty()) Text(details.joinToString(" · "))
            },
          )
          HorizontalDivider()
        }
        if (state.items.isEmpty() && state.query.isBlank()) {
          item { EmptyState(stringResource(R.string.exercise_empty), hint = stringResource(R.string.picker_empty_hint)) }
        }
      }
    }
  }

  state.chosen?.let { exercise ->
    AddSetsDialog(
      exerciseName = exercise.name,
      unit = state.unit,
      onAdd = viewModel::add,
      onDismiss = { viewModel.choose(null) },
    )
  }
}
