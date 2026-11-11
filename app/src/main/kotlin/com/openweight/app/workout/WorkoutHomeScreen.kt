package com.openweight.app.workout

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.openweight.app.R
import com.openweight.app.core.ui.MenuAccent
import com.openweight.app.core.ui.MenuDivider
import com.openweight.app.core.ui.MenuRow
import com.openweight.app.core.ui.MenuSection
import com.openweight.app.core.Today
import com.openweight.app.core.model.WeightUnit
import com.openweight.app.core.model.format
import com.openweight.app.core.ui.HeroAction
import com.openweight.app.core.ui.HeroActions
import com.openweight.app.core.ui.HeroCaption
import com.openweight.app.core.ui.HeroHeader
import com.openweight.app.core.ui.HeroLabel
import com.openweight.app.core.ui.HeroProgress
import com.openweight.app.core.ui.HeroTitle
import com.openweight.app.core.ui.HeroValue
import com.openweight.app.core.ui.PageHeading
import com.openweight.app.core.ui.StatTile
import com.openweight.app.core.ui.TileRow
import com.openweight.app.home.WeekDay
import com.openweight.app.home.WeekPlan
import com.openweight.app.progress.BodyWeightRepository
import com.openweight.app.progress.WeightRules
import com.openweight.app.progress.strength.PowerLevel
import com.openweight.app.progress.strength.StrengthRepository
import com.openweight.app.progress.strength.Tier
import com.openweight.app.progress.strength.color
import com.openweight.app.progress.strength.label
import com.openweight.app.settings.SettingsRepository
import com.openweight.app.workout.session.Session
import com.openweight.app.workout.session.SessionRepository
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import com.openweight.app.workout.blocks.TrainingBlockRepository
import com.openweight.app.workout.data.WorkoutRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** Null until loaded, so the menu never flashes "0 workouts". */
data class WorkoutHomeCounts(val blocks: Int, val workouts: Int, val exercises: Int)

/** What the Workout tab's hero and tiles show: this week's training, today, body weight and power level. */
data class WorkoutHomeState(
  /** False until the week has loaded: the hero shows no numbers before then. */
  val loaded: Boolean = false,
  val counts: WorkoutHomeCounts? = null,
  val week: List<WeekDay> = emptyList(),
  val openSession: Session? = null,
  val latestWeightKg: Double? = null,
  val weightChangeKg: Double? = null,
  val tier: Tier? = null,
  val unit: WeightUnit = WeightUnit.KG,
) {
  val today: WeekDay?
    get() = week.firstOrNull { it.isToday }

  val planned: Int
    get() = week.count { it.workout != null }

  val done: Int
    get() = week.count { it.workoutDone }
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class WorkoutHomeViewModel
@Inject
constructor(
  workouts: WorkoutRepository,
  blocks: TrainingBlockRepository,
  sessions: SessionRepository,
  bodyWeights: BodyWeightRepository,
  strength: StrengthRepository,
  settings: SettingsRepository,
  today: Today,
) : ViewModel() {
  private val counts =
    combine(blocks.blocks(), workouts.workouts(), workouts.exercises()) { b, w, e -> WorkoutHomeCounts(b.size, w.size, e.size) }

  private val week =
    today.date.flatMapLatest { day ->
      val monday = WeekPlan.mondayOf(day)
      combine(blocks.scheduledBlocks(), workouts.workouts(), sessions.done(monday, monday.plusDays(6))) { scheduled, all, done ->
        WeekPlan.days(monday, day, scheduled, all, done = done)
      }
    }

  private val progress =
    combine(bodyWeights.entries(), strength.powerLevel(), settings.settings) { entries, level, s ->
      Triple(entries, (level as? PowerLevel.Ready)?.report?.overallTier, s.weightUnit)
    }

  val state: StateFlow<WorkoutHomeState> =
    combine(counts, week, sessions.open(), progress) { c, w, open, (entries, tier, unit) ->
        WorkoutHomeState(true, c, w, open, entries.lastOrNull()?.weightKg, WeightRules.trend(entries).changeKg, tier, unit)
      }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WorkoutHomeState())
}

/**
 * Workout tab: a hero with this week's training and quick actions (train, history, weight, power
 * level), tiles for power level and body weight, then the plan (blocks, workouts, exercises).
 */
@Composable
fun WorkoutHomeScreen(
  onOpenBlocks: () -> Unit,
  onOpenWorkouts: () -> Unit,
  onOpenExercises: () -> Unit,
  modifier: Modifier = Modifier,
  onOpenHistory: () -> Unit = {},
  onOpenWeight: () -> Unit = {},
  onOpenPowerLevel: () -> Unit = {},
  onOpenWorkout: (workoutId: String, date: LocalDate) -> Unit = { _, _ -> },
  onOpenSession: (sessionId: String) -> Unit = {},
  viewModel: WorkoutHomeViewModel = hiltViewModel(),
) {
  val state by viewModel.state.collectAsStateWithLifecycle()
  val counts = state.counts
  Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
    HeroHeader {
      HeroTitle(stringResource(R.string.tab_workout))
      val block = state.today?.block
      HeroLabel(
        if (block != null) stringResource(R.string.workout_hero_week_block, stringResource(R.string.block_week_of, block.blockName, block.week, block.totalWeeks))
        else stringResource(R.string.workout_hero_week)
      )
      if (!state.loaded) {
        // Same height as the loaded hero, so nothing below it moves when the week arrives.
        HeroValue(" ")
        HeroProgress(0f, Modifier.padding(top = 12.dp).alpha(0f))
      } else if (state.planned > 0) {
        HeroValue("${state.done}/${state.planned}", unit = pluralStringResource(R.plurals.workout_hero_unit, state.planned))
        HeroProgress(state.done.toFloat() / state.planned, Modifier.padding(top = 12.dp))
      } else {
        HeroValue("${state.done}", unit = pluralStringResource(R.plurals.workout_hero_done_unit, state.done))
        HeroCaption(stringResource(R.string.workout_hero_no_plan), Modifier.padding(top = 4.dp))
      }
      val today = state.today
      HeroActions {
        val open = state.openSession
        when {
          open != null -> HeroAction(R.drawable.ic_menu_play, stringResource(R.string.home_session_resume), { onOpenSession(open.id) }, Modifier.testTag("hero_train"))
          today?.workout != null -> HeroAction(R.drawable.ic_menu_play, stringResource(R.string.workout_hero_train), { onOpenWorkout(today.workout.id, today.date) }, Modifier.testTag("hero_train"))
          else -> HeroAction(R.drawable.ic_menu_play, stringResource(R.string.workout_hero_train), onOpenWorkouts, Modifier.testTag("hero_train"))
        }
        HeroAction(R.drawable.ic_menu_history, stringResource(R.string.history_title), onOpenHistory, Modifier.testTag("open_history"))
        HeroAction(R.drawable.ic_menu_weight, stringResource(R.string.workout_hero_weight), onOpenWeight, Modifier.testTag("open_weight"))
        HeroAction(R.drawable.ic_menu_power, stringResource(R.string.workout_hero_power), onOpenPowerLevel, Modifier.testTag("open_power_level"))
      }
    }

    Spacer(Modifier.height(10.dp))
    TileRow {
      StatTile(
        R.drawable.ic_menu_power,
        stringResource(R.string.power_title),
        if (!state.loaded) " " else state.tier?.label() ?: stringResource(R.string.workout_tile_power_none),
        Modifier.weight(1f).testTag("tile_power"),
        caption =
          if (!state.loaded) " "
          else state.tier?.let { stringResource(R.string.workout_tile_power_caption, it.ordinal + 1, Tier.entries.size) } ?: stringResource(R.string.workout_tile_power_hint),
        accent = MenuAccent.PINK,
        onClick = onOpenPowerLevel,
      ) {
        val tier = state.tier
        if (tier != null) TierBar(tier)
      }
      StatTile(
        R.drawable.ic_menu_weight,
        stringResource(R.string.weight_title),
        if (!state.loaded) " " else state.latestWeightKg?.let { state.unit.format(it) } ?: "—",
        Modifier.weight(1f).testTag("tile_weight"),
        caption =
          if (!state.loaded) " "
          else when (val change = state.weightChangeKg) {
            null -> if (state.latestWeightKg == null) stringResource(R.string.workout_tile_weight_hint) else stringResource(R.string.weight_change_unknown_short)
            else -> stringResource(R.string.workout_tile_weight_change, (if (change >= 0) "+" else "−") + state.unit.format(kotlin.math.abs(change)))
          },
        accent = MenuAccent.GREEN,
        onClick = onOpenWeight,
      )
    }

    PageHeading(stringResource(R.string.workout_section_plan))
    MenuSection(title = null) {
      MenuRow(
        R.drawable.ic_menu_blocks,
        stringResource(R.string.workout_blocks),
        Modifier.testTag("open_blocks"),
        subtitle = counts?.let { pluralStringResource(R.plurals.block_count, it.blocks, it.blocks) },
        accent = MenuAccent.BLUE,
        onClick = onOpenBlocks,
      )
      MenuDivider()
      MenuRow(
        R.drawable.ic_menu_workouts,
        stringResource(R.string.workout_workouts),
        Modifier.testTag("open_workouts"),
        subtitle = counts?.let { pluralStringResource(R.plurals.workout_count, it.workouts, it.workouts) },
        accent = MenuAccent.VIOLET,
        onClick = onOpenWorkouts,
      )
      MenuDivider()
      MenuRow(
        R.drawable.ic_menu_exercises,
        stringResource(R.string.workout_exercises),
        Modifier.testTag("open_exercises"),
        subtitle = counts?.let { pluralStringResource(R.plurals.exercise_count, it.exercises, it.exercises) },
        accent = MenuAccent.TEAL,
        onClick = onOpenExercises,
      )
    }
  }
}

/** Seven small segments, filled up to [tier] in its colour. */
@Composable
private fun TierBar(tier: Tier) {
  Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
    Tier.entries.forEach { t ->
      Box(
        Modifier.weight(1f).height(6.dp).clip(CircleShape)
          .background(if (t.ordinal <= tier.ordinal) tier.color else MaterialTheme.colorScheme.surfaceContainerHighest)
      )
    }
  }
}
