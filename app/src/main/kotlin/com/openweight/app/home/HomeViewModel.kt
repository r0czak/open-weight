package com.openweight.app.home

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openweight.app.core.Today
import com.openweight.app.diet.data.MealPlanRepository
import com.openweight.app.workout.blocks.TrainingBlockRepository
import com.openweight.app.workout.data.WorkoutRepository
import com.openweight.app.workout.session.Session
import com.openweight.app.workout.session.SessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeState(
  val loading: Boolean = true,
  val today: LocalDate,
  val monday: LocalDate,
  val selected: LocalDate,
  val days: List<WeekDay> = emptyList(),
  /** The training session in progress, to resume. */
  val openSession: Session? = null,
) {
  val isCurrentWeek: Boolean
    get() = monday == WeekPlan.mondayOf(today)
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel
@Inject
constructor(
  blocks: TrainingBlockRepository,
  workouts: WorkoutRepository,
  meals: MealPlanRepository,
  sessions: SessionRepository,
  private val todayNow: Today,
  private val saved: SavedStateHandle,
) : ViewModel() {
  private val today: LocalDate
    get() = todayNow.date.value

  // The shown week and selected day survive rotation and switching tabs.
  private val mondayFlow = saved.getStateFlow(KEY_MONDAY, WeekPlan.mondayOf(today).toEpochDay())
  private val selectedFlow = saved.getStateFlow(KEY_SELECTED, today.toEpochDay())

  init {
    // A new day began while the app was open: a week view still on "today" moves to the new one.
    viewModelScope.launch {
      var previous = today
      todayNow.date.drop(1).collect { now ->
        if (selectedFlow.value == previous.toEpochDay()) thisWeek()
        previous = now
      }
    }
  }

  private val weekMeals =
    mondayFlow.flatMapLatest { epoch ->
      val monday = LocalDate.ofEpochDay(epoch)
      meals.days(monday, monday.plusDays(6))
    }

  private val shown =
    combine(todayNow.date, mondayFlow.map(LocalDate::ofEpochDay), selectedFlow.map(LocalDate::ofEpochDay)) { today, monday, selected ->
      Triple(today, monday, selected)
    }

  private val weekDone =
    mondayFlow.flatMapLatest { epoch ->
      val monday = LocalDate.ofEpochDay(epoch)
      sessions.done(monday, monday.plusDays(6))
    }

  private val training = combine(weekDone, sessions.open()) { done, open -> done to open }

  val state: StateFlow<HomeState> =
    combine(
        shown,
        combine(blocks.scheduledBlocks(), workouts.workouts()) { scheduled, all -> scheduled to all },
        weekMeals,
        training,
      ) { (today, monday, selected), (scheduled, all), dayMeals, (done, open) ->
        HomeState(
          loading = false,
          today = today,
          monday = monday,
          selected = selected,
          days = WeekPlan.days(monday, today, scheduled, all, dayMeals, done),
          openSession = open,
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
