package com.openweight.app.workout.workouts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openweight.app.core.model.WeightUnit
import com.openweight.app.settings.SettingsRepository
import com.openweight.app.workout.data.DeleteResult
import com.openweight.app.workout.data.SetValues
import com.openweight.app.workout.data.WorkoutDetail
import com.openweight.app.workout.data.WorkoutRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface WorkoutEditState {
  data object Loading : WorkoutEditState

  /** The workout was deleted (here or elsewhere). */
  data object Gone : WorkoutEditState

  data class Loaded(val workout: WorkoutDetail, val unit: WeightUnit) : WorkoutEditState
}

sealed interface WorkoutEditEvent {
  data class Opened(val workoutId: String) : WorkoutEditEvent

  data class InUse(val blockCount: Int) : WorkoutEditEvent

  data object Deleted : WorkoutEditEvent
}

@HiltViewModel(assistedFactory = WorkoutEditViewModel.Factory::class)
class WorkoutEditViewModel
@AssistedInject
constructor(
  private val repository: WorkoutRepository,
  settings: SettingsRepository,
  @Assisted private val workoutId: String,
) : ViewModel() {

  @AssistedFactory
  interface Factory {
    fun create(workoutId: String): WorkoutEditViewModel
  }

  val state: StateFlow<WorkoutEditState> =
    combine(repository.workout(workoutId), settings.settings) { workout, s ->
        if (workout == null) WorkoutEditState.Gone else WorkoutEditState.Loaded(workout, s.weightUnit)
      }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WorkoutEditState.Loading)

  private val _events = Channel<WorkoutEditEvent>(Channel.BUFFERED)
  val events: Flow<WorkoutEditEvent> = _events.receiveAsFlow()

  fun updateInfo(name: String, type: String, notes: String) = launch {
    repository.updateWorkoutInfo(workoutId, name, type, notes)
  }

  fun duplicate() = launch { _events.send(WorkoutEditEvent.Opened(repository.duplicateWorkout(workoutId))) }

  fun delete() = launch {
    when (val r = repository.deleteWorkout(workoutId)) {
      DeleteResult.Deleted -> _events.send(WorkoutEditEvent.Deleted)
      is DeleteResult.InUse -> _events.send(WorkoutEditEvent.InUse(r.count))
    }
  }

  fun moveEntry(entryId: String, delta: Int) = launch { repository.moveEntry(entryId, delta) }

  fun removeEntry(entryId: String) = launch { repository.removeEntry(entryId) }

  fun updateEntryNotes(entryId: String, notes: String) = launch {
    repository.updateEntryNotes(entryId, notes)
  }

  fun addSet(entryId: String) = launch { repository.addSet(entryId) }

  fun updateSet(setId: String, values: SetValues, applyToAll: Boolean) = launch {
    repository.updateSet(setId, values, applyToAll)
  }

  fun deleteSet(setId: String) = launch { repository.deleteSet(setId) }

  private fun launch(block: suspend () -> Unit) {
    viewModelScope.launch { block() }
  }
}
