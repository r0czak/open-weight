package com.openweight.app

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.openweight.app.diet.DietHomeScreen
import com.openweight.app.home.HomeRoute
import com.openweight.app.workout.workouts.WorkoutOverviewRoute
import java.time.LocalDate
import com.openweight.app.settings.SettingsRoute
import com.openweight.app.workout.WorkoutHomeScreen
import com.openweight.app.workout.blocks.BlockEditRoute
import com.openweight.app.workout.blocks.BlockListRoute
import com.openweight.app.workout.exercise.ExerciseEditRoute
import com.openweight.app.workout.exercise.ExerciseListRoute
import com.openweight.app.workout.workouts.ExercisePickerRoute
import com.openweight.app.workout.workouts.WorkoutEditRoute
import com.openweight.app.workout.workouts.WorkoutListRoute
import kotlinx.serialization.Serializable

// Navigation keys. Each tab has its own back stack, so switching tabs keeps your place in each.
@Serializable data object HomeHome : NavKey

@Serializable data object WorkoutHome : NavKey

@Serializable data object DietHome : NavKey

@Serializable data object SettingsHome : NavKey

// Workout tab
@Serializable data object BlockList : NavKey

@Serializable data class BlockEdit(val blockId: String) : NavKey

@Serializable data object WorkoutList : NavKey

/** Read-only workout, as opened from a day on Home ([epochDay] = that day, if any). */
@Serializable data class WorkoutOverview(val workoutId: String, val epochDay: Long? = null) : NavKey

@Serializable data class WorkoutEdit(val workoutId: String) : NavKey

@Serializable data class ExercisePicker(val workoutId: String) : NavKey

@Serializable data object ExerciseList : NavKey

/** [exerciseId] null = a new exercise. */
@Serializable data class ExerciseEdit(val exerciseId: String?) : NavKey

enum class TopLevelDestination(
  @param:StringRes val label: Int,
  @param:DrawableRes val icon: Int,
) {
  HOME(R.string.tab_home, R.drawable.ic_tab_home),
  WORKOUT(R.string.tab_workout, R.drawable.ic_tab_workout),
  DIET(R.string.tab_diet, R.drawable.ic_tab_diet),
  SETTINGS(R.string.tab_settings, R.drawable.ic_tab_settings),
}

@Composable
fun OpenWeightAppUi(modifier: Modifier = Modifier) {
  var current by rememberSaveable { mutableStateOf(TopLevelDestination.HOME) }
  val stacks: Map<TopLevelDestination, NavBackStack<NavKey>> =
    mapOf(
      TopLevelDestination.HOME to rememberNavBackStack(HomeHome),
      TopLevelDestination.WORKOUT to rememberNavBackStack(WorkoutHome),
      TopLevelDestination.DIET to rememberNavBackStack(DietHome),
      TopLevelDestination.SETTINGS to rememberNavBackStack(SettingsHome),
    )
  val backStack = stacks.getValue(current)
  val tabStateHolder = rememberSaveableStateHolder()

  // Back on the root of another tab returns to Home before leaving the app.
  BackHandler(enabled = current != TopLevelDestination.HOME && backStack.size == 1) {
    current = TopLevelDestination.HOME
  }

  Scaffold(
    modifier = modifier,
    contentWindowInsets = WindowInsets(0, 0, 0, 0),
    bottomBar = {
      NavigationBar {
        TopLevelDestination.entries.forEach { destination ->
          NavigationBarItem(
            modifier = Modifier.testTag("tab_${destination.name}"),
            selected = destination == current,
            onClick = {
              if (destination == current) {
                // Tapping the current tab again goes back to its first screen.
                val stack = stacks.getValue(destination)
                while (stack.size > 1) stack.removeAt(stack.lastIndex)
              } else {
                current = destination
              }
            },
            icon = { Icon(painterResource(destination.icon), contentDescription = null) },
            label = { Text(stringResource(destination.label)) },
          )
        }
      }
    },
  ) { padding ->
    // Each tab's screens keep their state (scroll, selected panel, typed text) while another tab
    // is shown: the tab's whole subtree is saved under its own key and restored on return.
    // Never pops a tab's first screen, even if a screen reports "done" twice.
    val back: () -> Unit = { if (backStack.size > 1) backStack.removeAt(backStack.lastIndex) }
    tabStateHolder.SaveableStateProvider(current.name) {
      NavDisplay(
        backStack = backStack,
        modifier = Modifier.padding(padding),
        onBack = back,
        entryDecorators =
          listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(),
          ),
        entryProvider =
          entryProvider {
            entry<HomeHome> {
            HomeRoute(onOpenWorkout = { id, date -> backStack.add(WorkoutOverview(id, date.toEpochDay())) })
          }
          entry<WorkoutOverview> { key ->
            WorkoutOverviewRoute(
              workoutId = key.workoutId,
              date = key.epochDay?.let(LocalDate::ofEpochDay),
              onBack = back,
              onEdit = { backStack.add(WorkoutEdit(key.workoutId)) },
            )
          }
          entry<WorkoutHome> {
            WorkoutHomeScreen(
              onOpenBlocks = { backStack.add(BlockList) },
              onOpenWorkouts = { backStack.add(WorkoutList) },
              onOpenExercises = { backStack.add(ExerciseList) },
            )
          }
          entry<BlockList> { BlockListRoute(onBack = back, onOpen = { backStack.add(BlockEdit(it)) }) }
          entry<BlockEdit> { key ->
            BlockEditRoute(blockId = key.blockId, onBack = back, onOpenWorkout = { backStack.add(WorkoutEdit(it)) })
          }
          entry<WorkoutList> {
            WorkoutListRoute(onBack = back, onOpen = { backStack.add(WorkoutEdit(it)) })
          }
          entry<WorkoutEdit> { key ->
            WorkoutEditRoute(
              workoutId = key.workoutId,
              onBack = back,
              onOpenWorkout = {
                backStack.removeLastOrNull()
                backStack.add(WorkoutEdit(it))
              },
              onAddExercise = { backStack.add(ExercisePicker(key.workoutId)) },
            )
          }
          entry<ExercisePicker> { key -> ExercisePickerRoute(workoutId = key.workoutId, onDone = back) }
          entry<ExerciseList> {
            ExerciseListRoute(
              onBack = back,
              onOpen = { backStack.add(ExerciseEdit(it)) },
              onCreate = { backStack.add(ExerciseEdit(null)) },
            )
          }
          entry<ExerciseEdit> { key -> ExerciseEditRoute(exerciseId = key.exerciseId, onDone = back) }
            entry<DietHome> { DietHomeScreen() }
            entry<SettingsHome> { SettingsRoute() }
          },
      )
    }
  }
}
