package com.openweight.app.home

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openweight.app.core.TimeAndIds
import com.openweight.app.workout.blocks.TrainingBlockRepository
import com.openweight.app.workout.data.WorkoutRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class HomeState(
  val loading: Boolean = true,
  val today: LocalDate,
  val monday: LocalDate,
  val selected: LocalDate,
  val days: List<WeekDay> = emptyList(),
) {
  val isCurrentWeek: Boolean
    get() = monday == WeekPlan.mondayOf(today)
}

@HiltViewModel
class HomeViewModel
@Inject
constructor(
  blocks: TrainingBlockRepository,
  workouts: WorkoutRepository,
  clock: TimeAndIds,
  private val saved: SavedStateHandle,
) : ViewModel() {
  private val today = clock.today()

  // The shown week and selected day survive rotation and switching tabs.
  private val mondayFlow = saved.getStateFlow(KEY_MONDAY, WeekPlan.mondayOf(today).toEpochDay())
  private val selectedFlow = saved.getStateFlow(KEY_SELECTED, today.toEpochDay())

  val state: StateFlow<HomeState> =
    combine(
        mondayFlow.map(LocalDate::ofEpochDay),
        selectedFlow.map(LocalDate::ofEpochDay),
        blocks.scheduledBlocks(),
        workouts.workouts(),
      ) { monday, selected, scheduled, all ->
        HomeState(
          loading = false,
          today = today,
          monday = monday,
          selected = selected,
          days = WeekPlan.days(monday, today, scheduled, all),
        )
      }
      .stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        HomeState(today = today, monday = WeekPlan.mondayOf(today), selected = today),
      )

  fun select(date: LocalDate) {
    saved[KEY_SELECTED] = date.toEpochDay()
  }

  fun previousWeek() = showWeek(LocalDate.ofEpochDay(mondayFlow.value).minusWeeks(1))

  fun nextWeek() = showWeek(LocalDate.ofEpochDay(mondayFlow.value).plusWeeks(1))

  fun thisWeek() {
    saved[KEY_MONDAY] = WeekPlan.mondayOf(today).toEpochDay()
    select(today)
  }

  /** Another week opens on its Monday; the current week opens on today. */
  private fun showWeek(monday: LocalDate) {
    if (monday == WeekPlan.mondayOf(today)) return thisWeek()
    saved[KEY_MONDAY] = monday.toEpochDay()
    select(monday)
  }

  private companion object {
    const val KEY_MONDAY = "monday"
    const val KEY_SELECTED = "selected"
  }
}
