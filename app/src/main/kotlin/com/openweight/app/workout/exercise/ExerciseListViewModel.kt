package com.openweight.app.workout.exercise

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openweight.app.workout.data.ExerciseListItem
import com.openweight.app.workout.data.WorkoutRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import com.openweight.app.transfer.KindResult
import com.openweight.app.workout.data.StarterExercises
import javax.inject.Inject
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class ExerciseListState(
  val query: String = "",
  val items: List<ExerciseListItem> = emptyList(),
  val totalCount: Int = 0,
  val loading: Boolean = true,
)

@HiltViewModel
class ExerciseListViewModel @Inject constructor(repository: WorkoutRepository, private val starter: StarterExercises) : ViewModel() {
  private val query = MutableStateFlow("")

  val state: StateFlow<ExerciseListState> =
    combine(query.asStateFlow(), repository.exercises()) { q, all ->
        ExerciseListState(q, all.filter { it.matches(q) }, all.size, loading = false)
      }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExerciseListState())

  fun onQueryChange(value: String) {
    query.value = value
  }

  /** Adds the common exercises the library doesn't have yet. */
  fun addStarter(done: (KindResult) -> Unit) {
    viewModelScope.launch { done(starter.add()) }
  }
}

/** Matches the name, muscle group or equipment, ignoring case. */
fun ExerciseListItem.matches(query: String): Boolean {
  val q = query.trim()
  return q.isEmpty() ||
    name.contains(q, ignoreCase = true) ||
    muscleGroup.contains(q, ignoreCase = true) ||
    equipment.contains(q, ignoreCase = true)
}
