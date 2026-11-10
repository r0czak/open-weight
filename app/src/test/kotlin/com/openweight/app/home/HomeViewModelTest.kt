package com.openweight.app.home

import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.openweight.app.core.Today
import com.openweight.app.core.database.OpenWeightDatabase
import com.openweight.app.diet.data.DietRepository
import com.openweight.app.diet.data.MealPlanRepository
import com.openweight.app.testing.FakeTimeAndIds
import com.openweight.app.testing.inMemoryDatabase
import com.openweight.app.workout.blocks.TrainingBlockRepository
import com.openweight.app.workout.data.WorkoutRepository
import com.openweight.app.workout.session.SessionRepository
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.rules.TemporaryFolder
import org.junit.Rule
import kotlinx.coroutines.cancel
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CoroutineScope
import com.openweight.app.settings.SettingsRepository
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** The app stays open past midnight: Home must not keep showing yesterday as today. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class HomeViewModelTest {
  private val sunday = LocalDate.of(2026, 11, 8)
  private val monday = sunday.plusDays(1)

  private lateinit var db: OpenWeightDatabase
  private lateinit var clock: FakeTimeAndIds
  private lateinit var today: Today
  private lateinit var viewModel: HomeViewModel
  private val settingsScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

  @get:Rule val tmp = TemporaryFolder()

  @Before
  fun setUp() {
    Dispatchers.setMain(UnconfinedTestDispatcher())
    db = inMemoryDatabase()
    clock = FakeTimeAndIds(today = sunday)
    today = Today(clock)
    viewModel =
      HomeViewModel(
        TrainingBlockRepository(db, clock),
        WorkoutRepository(db, clock),
        SessionRepository(db, clock),
        MealPlanRepository(db, DietRepository(db, clock), clock),
        SettingsRepository(PreferenceDataStoreFactory.create(scope = settingsScope) { tmp.newFile("settings.preferences_pb").also { it.delete() } }),
        today,
        SavedStateHandle(),
      )
  }

  @After
  fun tearDown() {
    db.close()
    settingsScope.cancel()
    Dispatchers.resetMain()
  }

  private fun newDay(date: LocalDate) {
    clock.today = date
    today.refresh()
  }

  @Test
  fun aNewDay_movesTheWeekViewToIt() = runTest {
    val before = viewModel.state.first { !it.loading }
    assertEquals(sunday, before.selected)
    assertEquals(sunday.minusDays(6), before.monday)

    newDay(monday)

    val after = viewModel.state.first { !it.loading && it.today == monday && it.selected == monday }
    assertEquals(monday, after.monday)
    assertEquals(listOf(monday), after.days.filter { it.isToday }.map { it.date })
  }

  @Test
  fun aNewDay_leavesADayTheUserPicked_butMovesTheTodayMark() = runTest {
    viewModel.state.first { !it.loading }
    val picked = sunday.minusDays(3)
    viewModel.select(picked)

    newDay(monday)

    val after = viewModel.state.first { !it.loading && it.today == monday }
    assertEquals(picked, after.selected)
    assertEquals(sunday.minusDays(6), after.monday)
    assertEquals(emptyList<LocalDate>(), after.days.filter { it.isToday }.map { it.date })
  }
}
