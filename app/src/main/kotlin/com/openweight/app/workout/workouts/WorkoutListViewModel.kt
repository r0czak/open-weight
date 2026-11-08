package com.openweight.app.workout.workouts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openweight.app.workout.data.DeleteResult
import com.openweight.app.workout.data.WorkoutRepository
import com.openweight.app.workout.data.WorkoutSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class WorkoutListState(val loading: Boolean = true, val items: List<WorkoutSummary> = emptyList())

sealed interface WorkoutListEvent {
  data class Open(val workoutId: String) : WorkoutListEvent

  data class InUse(val blockCount: Int) : WorkoutListEvent
}

@HiltViewModel
class WorkoutListViewModel @Inject constructor(private val repository: WorkoutRepository) :
  ViewModel() {

  val state: StateFlow<WorkoutListState> =
    repository
      .workouts()
      .map { WorkoutListState(loading = false, items = it) }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WorkoutListState())

  private val _events = Channel<WorkoutListEvent>(Channel.BUFFERED)
  val events: Flow<WorkoutListEvent> = _events.receiveAsFlow()

  fun create(name: String, type: String) {
    viewModelScope.launch { _events.send(WorkoutListEvent.Open(repository.createWorkout(name, type))) }
  }

  fun duplicate(id: String) {
    viewModelScope.launch { _events.send(WorkoutListEvent.Open(repository.duplicateWorkout(id))) }
  }

  fun delete(id: String) {
    viewModelScope.launch {
      val result = repository.deleteWorkout(id)
      if (result is DeleteResult.InUse) _events.send(WorkoutListEvent.InUse(result.count))
    }
  }
}
