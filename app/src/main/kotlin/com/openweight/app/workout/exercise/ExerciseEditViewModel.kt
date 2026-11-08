package com.openweight.app.workout.exercise

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openweight.app.workout.data.DeleteResult
import com.openweight.app.workout.data.DuplicateNameException
import com.openweight.app.workout.data.WorkoutRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ExerciseEditState(
  val isNew: Boolean,
  val loaded: Boolean = false,
  val name: String = "",
  val muscleGroup: String = "",
  val equipment: String = "",
  val notes: String = "",
  val nameError: NameError? = null,
  /** Set when a delete was refused because this many workouts use the exercise. */
  val inUseCount: Int? = null,
  val done: Boolean = false,
)

enum class NameError { EMPTY, DUPLICATE }

@HiltViewModel(assistedFactory = ExerciseEditViewModel.Factory::class)
class ExerciseEditViewModel
@AssistedInject
constructor(
  private val repository: WorkoutRepository,
  @Assisted private val exerciseId: String?,
) : ViewModel() {

  @AssistedFactory
  interface Factory {
    fun create(exerciseId: String?): ExerciseEditViewModel
  }

  private val _state = MutableStateFlow(ExerciseEditState(isNew = exerciseId == null))
  val state: StateFlow<ExerciseEditState> = _state.asStateFlow()

  init {
    if (exerciseId == null) {
      _state.update { it.copy(loaded = true) }
    } else {
      viewModelScope.launch {
        val e = repository.getExercise(exerciseId)
        _state.update {
          if (e == null) it.copy(done = true)
          else
            it.copy(
              loaded = true,
              name = e.name,
              muscleGroup = e.muscleGroup,
              equipment = e.equipment,
              notes = e.notes,
            )
        }
      }
    }
  }

  fun onNameChange(v: String) = _state.update { it.copy(name = v, nameError = null) }

  fun onMuscleGroupChange(v: String) = _state.update { it.copy(muscleGroup = v) }

  fun onEquipmentChange(v: String) = _state.update { it.copy(equipment = v) }

  fun onNotesChange(v: String) = _state.update { it.copy(notes = v) }

  fun save() {
    val s = _state.value
    if (s.name.isBlank()) {
      _state.update { it.copy(nameError = NameError.EMPTY) }
      return
    }
    viewModelScope.launch {
      try {
        repository.saveExercise(exerciseId, s.name, s.muscleGroup, s.equipment, s.notes)
        _state.update { it.copy(done = true) }
      } catch (e: DuplicateNameException) {
        _state.update { it.copy(nameError = NameError.DUPLICATE) }
      }
    }
  }

  fun delete() {
    val id = exerciseId ?: return
    viewModelScope.launch {
      when (val result = repository.deleteExercise(id)) {
        DeleteResult.Deleted -> _state.update { it.copy(done = true) }
        is DeleteResult.InUse -> _state.update { it.copy(inUseCount = result.count) }
      }
    }
  }

  fun dismissInUse() = _state.update { it.copy(inUseCount = null) }
}
