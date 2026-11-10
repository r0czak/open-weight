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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.openweight.app.core.model.MealSlot
import com.openweight.app.core.ui.formatDayShort
import com.openweight.app.diet.DietHomeScreen
import com.openweight.app.diet.calendar.MealPickerRoute
import com.openweight.app.diet.calendar.MealTarget
import com.openweight.app.diet.plans.DietPlanEditRoute
import com.openweight.app.diet.plans.DietPlanListRoute
import java.time.DayOfWeek
import java.time.format.TextStyle
import com.openweight.app.diet.ingredients.IngredientEditRoute
import com.openweight.app.diet.ingredients.IngredientListRoute
import com.openweight.app.diet.recipes.IngredientPickerRoute
import com.openweight.app.diet.recipes.RecipeDetailRoute
import com.openweight.app.diet.recipes.RecipeEditRoute
import com.openweight.app.home.HomeRoute
import com.openweight.app.workout.session.ExerciseHistoryRoute
import com.openweight.app.workout.session.HistoryRoute
import com.openweight.app.workout.session.LiveSessionRoute
import com.openweight.app.workout.session.SessionDetailRoute
import com.openweight.app.workout.workouts.WorkoutOverviewRoute
import java.time.LocalDate
import com.openweight.app.progress.BodyWeightRoute
import com.openweight.app.settings.SettingsRoute
import com.openweight.app.transfer.ui.ExportRoute
import com.openweight.app.transfer.ui.ImportRoute
import kotlinx.coroutines.flow.StateFlow
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

/** The import preview for the file in [com.openweight.app.transfer.ImportSession]. */
@Serializable data object ImportPreview : NavKey

@Serializable data object ExportPicker : NavKey

// Workout tab
@Serializable data object BlockList : NavKey

@Serializable data class BlockEdit(val blockId: String) : NavKey

@Serializable data object WorkoutList : NavKey

// Diet tab
@Serializable data class RecipeView(val recipeId: String) : NavKey

/** [recipeId] null = a new recipe. */
@Serializable data class RecipeEdit(val recipeId: String?) : NavKey

@Serializable data class IngredientPicker(val recipeId: String) : NavKey

@Serializable data object IngredientList : NavKey

/** Pick a recipe for a calendar day ([epochDay]) or a diet plan's weekday ([planId] + [dayOfWeek]). */
@Serializable
data class MealPicker(
  val slot: String,
  val epochDay: Long? = null,
  val planId: String? = null,
  val dayOfWeek: String? = null,
  /** Set when swapping that calendar meal's recipe. */
  val swapMealId: String? = null,
) : NavKey

@Serializable data object DietPlanList : NavKey

@Serializable data class DietPlanEdit(val planId: String) : NavKey

/** [ingredientId] null = a new ingredient. */
@Serializable data class IngredientEdit(val ingredientId: String?) : NavKey

/** Read-only workout, as opened from a day on Home ([epochDay] = that day, if any). */
@Serializable data class WorkoutOverview(val workoutId: String, val epochDay: Long? = null) : NavKey

@Serializable data object History : NavKey

@Serializable data object BodyWeight : NavKey

@Serializable data class SessionDetail(val sessionId: String) : NavKey

@Serializable data class ExerciseHistory(val exerciseId: String) : NavKey

/** Live training: the session being trained, or its summary once finished. */
@Serializable data class LiveSession(val sessionId: String) : NavKey

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

/**
 * [importOpens] counts files opened for import (from Settings, or shared by another app); each new
 * one shows the import preview in the Settings tab.
 */
@Composable
fun OpenWeightAppUi(modifier: Modifier = Modifier, importOpens: StateFlow<Int>? = null) {
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

  if (importOpens != null) {
    val opens by importOpens.collectAsStateWithLifecycle()
    var handledOpens by rememberSaveable { mutableIntStateOf(opens) }
    LaunchedEffect(opens) {
      if (opens > handledOpens) {
        handledOpens = opens
        current = TopLevelDestination.SETTINGS
        val settings = stacks.getValue(TopLevelDestination.SETTINGS)
        if (settings.lastOrNull() != ImportPreview) settings.add(ImportPreview)
      }
    }
  }

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
            HomeRoute(
              onOpenWorkout = { id, date -> backStack.add(WorkoutOverview(id, date.toEpochDay())) },
              onOpenRecipe = { backStack.add(RecipeView(it)) },
              onOpenSession = { backStack.add(LiveSession(it)) },
              onOpenWeight = { backStack.add(BodyWeight) },
            )
          }
          entry<BodyWeight> { BodyWeightRoute(onBack = back) }
          entry<LiveSession> { key -> LiveSessionRoute(sessionId = key.sessionId, onBack = back) }
          entry<WorkoutOverview> { key ->
            WorkoutOverviewRoute(
              workoutId = key.workoutId,
              date = key.epochDay?.let(LocalDate::ofEpochDay),
              onBack = back,
              onEdit = { backStack.add(WorkoutEdit(key.workoutId)) },
              onOpenSession = { backStack.add(LiveSession(it)) },
            )
          }
          entry<WorkoutHome> {
            WorkoutHomeScreen(
              onOpenBlocks = { backStack.add(BlockList) },
              onOpenWorkouts = { backStack.add(WorkoutList) },
              onOpenExercises = { backStack.add(ExerciseList) },
              onOpenHistory = { backStack.add(History) },
              onOpenWeight = { backStack.add(BodyWeight) },
            )
          }
          entry<History> { HistoryRoute(onBack = back, onOpen = { backStack.add(SessionDetail(it)) }) }
          entry<SessionDetail> { key ->
            SessionDetailRoute(sessionId = key.sessionId, onBack = back, onOpenExercise = { backStack.add(ExerciseHistory(it)) })
          }
          entry<ExerciseHistory> { key -> ExerciseHistoryRoute(exerciseId = key.exerciseId, onBack = back) }
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
              onTrain = { backStack.add(WorkoutOverview(key.workoutId)) },
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
          entry<ExerciseEdit> { key ->
            ExerciseEditRoute(
              exerciseId = key.exerciseId,
              onDone = back,
              onOpenProgress = { key.exerciseId?.let { backStack.add(ExerciseHistory(it)) } },
            )
          }
            entry<DietHome> {
            DietHomeScreen(
              onOpenRecipe = { backStack.add(RecipeView(it)) },
              onCreateRecipe = { backStack.add(RecipeEdit(null)) },
              onOpenIngredients = { backStack.add(IngredientList) },
              onAddMeal = { date, slot -> backStack.add(MealPicker(slot.name, epochDay = date.toEpochDay())) },
              onSwapMeal = { id, date, slot -> backStack.add(MealPicker(slot.name, epochDay = date.toEpochDay(), swapMealId = id)) },
              onOpenPlans = { backStack.add(DietPlanList) },
            )
          }
          entry<MealPicker> { key ->
            val target =
              when {
                key.planId != null -> MealTarget.Plan(key.planId, DayOfWeek.valueOf(key.dayOfWeek!!))
                key.swapMealId != null -> MealTarget.Swap(key.swapMealId, LocalDate.ofEpochDay(key.epochDay!!))
                else -> MealTarget.Day(LocalDate.ofEpochDay(key.epochDay!!))
              }
            MealPickerRoute(
              target = target,
              initialSlot = MealSlot.valueOf(key.slot),
              title =
                when (target) {
                  is MealTarget.Day -> stringResource(R.string.meal_add_title, target.date.formatDayShort())
                  is MealTarget.Swap -> stringResource(R.string.meal_swap_title, target.date.formatDayShort())
                  is MealTarget.Plan -> stringResource(R.string.meal_add_title, target.day.getDisplayName(TextStyle.FULL_STANDALONE, LocalConfiguration.current.locales[0]))
                },
              onDone = back,
            )
          }
          entry<DietPlanList> { DietPlanListRoute(onBack = back, onOpen = { backStack.add(DietPlanEdit(it)) }) }
          entry<DietPlanEdit> { key ->
            DietPlanEditRoute(
              planId = key.planId,
              onBack = back,
              onAddMeal = { day -> backStack.add(MealPicker(MealSlot.BREAKFAST.name, planId = key.planId, dayOfWeek = day.name)) },
              onOpenRecipe = { backStack.add(RecipeView(it)) },
            )
          }
          entry<RecipeView> { key ->
            RecipeDetailRoute(
              recipeId = key.recipeId,
              onBack = back,
              onEdit = { backStack.add(RecipeEdit(key.recipeId)) },
              onOpenRecipe = {
                backStack.removeLastOrNull()
                backStack.add(RecipeView(it))
              },
            )
          }
          entry<RecipeEdit> { key ->
            RecipeEditRoute(
              recipeId = key.recipeId,
              onDone = back,
              onAddIngredient = { backStack.add(IngredientPicker(it)) },
            )
          }
          entry<IngredientPicker> { key -> IngredientPickerRoute(recipeId = key.recipeId, onDone = back) }
          entry<IngredientList> {
            IngredientListRoute(
              onBack = back,
              onOpen = { backStack.add(IngredientEdit(it)) },
              onCreate = { backStack.add(IngredientEdit(null)) },
            )
          }
          entry<IngredientEdit> { key -> IngredientEditRoute(ingredientId = key.ingredientId, onDone = { back() }) }
            entry<SettingsHome> { SettingsRoute(onOpenExport = { backStack.add(ExportPicker) }) }
            entry<ImportPreview> { ImportRoute(onBack = back) }
            entry<ExportPicker> { ExportRoute(onBack = back) }
          },
      )
    }
  }
}
