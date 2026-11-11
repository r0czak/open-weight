package com.openweight.app.home

import com.openweight.app.core.ui.HeroAction
import com.openweight.app.core.ui.HeroActions
import com.openweight.app.core.ui.HeroCaption
import com.openweight.app.core.ui.HeroHeader
import com.openweight.app.core.ui.HeroLabel
import com.openweight.app.core.ui.HeroPill
import com.openweight.app.core.ui.HeroProgress
import com.openweight.app.core.ui.HeroTitle
import com.openweight.app.core.ui.HeroValue
import com.openweight.app.core.ui.PageHeading
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.openweight.app.R
import com.openweight.app.core.ui.ScreenScaffold
import com.openweight.app.core.ui.formatDateRange
import com.openweight.app.core.ui.formatDayShort
import com.openweight.app.diet.calendar.DayProgress
import com.openweight.app.diet.calendar.EatenToggle
import com.openweight.app.diet.calendar.slotLabel
import com.openweight.app.diet.formatKcal
import java.time.LocalDate
import java.time.format.TextStyle

/** Home: the current week, Monday to Sunday, with each day's workout (and later its meals). */
@Composable
fun HomeRoute(
  onOpenWorkout: (workoutId: String, date: LocalDate) -> Unit,
  onOpenRecipe: (recipeId: String) -> Unit,
  onOpenSession: (sessionId: String) -> Unit,
  onOpenWeight: () -> Unit = {},
  onOpenPowerLevel: () -> Unit = {},
  onOpenHistory: () -> Unit = {},
  viewModel: HomeViewModel = hiltViewModel(),
) {
  val state by viewModel.state.collectAsStateWithLifecycle()
  val listState = rememberLazyListState()
  // When the selected day changes (a tap in the strip, or another week), bring its card into view;
  // not on opening, so the hero stays at the top.
  var opened by remember { mutableStateOf(false) }
  LaunchedEffect(state.selected, state.loading) {
    if (state.loading) return@LaunchedEffect
    if (!opened) {
      opened = true
      return@LaunchedEffect
    }
    val index = state.days.indexOfFirst { it.date == state.selected }
    if (index >= 0) listState.animateScrollToItem(index + HEADER_ITEMS)
  }

  LazyColumn(
    state = listState,
    modifier = Modifier.fillMaxSize().testTag("week_agenda"),
    contentPadding = PaddingValues(bottom = 24.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    item(key = "hero") { HomeHero(state, onOpenWorkout, onOpenSession, onOpenWeight, onOpenPowerLevel, onOpenHistory) }
    item(key = "week") {
      PageHeading(formatDateRange(state.monday, state.monday.plusDays(6))) {
        if (!state.isCurrentWeek) {
          TextButton(onClick = viewModel::thisWeek, modifier = Modifier.testTag("this_week")) { Text(stringResource(R.string.home_today)) }
        }
        IconButton(onClick = viewModel::previousWeek, modifier = Modifier.testTag("previous_week")) {
          Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, stringResource(R.string.home_previous_week))
        }
        IconButton(onClick = viewModel::nextWeek, modifier = Modifier.testTag("next_week")) {
          Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, stringResource(R.string.home_next_week))
        }
      }
    }
    item(key = "strip") {
      Box(Modifier.padding(horizontal = 16.dp).clip(MaterialTheme.shapes.large).background(MaterialTheme.colorScheme.surfaceContainerLow)) {
        DayStrip(state.days, state.selected, onSelect = viewModel::select)
      }
    }
    items(state.days, key = { it.date.toEpochDay() }) { day ->
      Box(Modifier.padding(horizontal = 16.dp)) {
        DayCard(
          day = day,
          selected = day.date == state.selected,
          onSelect = { viewModel.select(day.date) },
          onOpenWorkout = { onOpenWorkout(it, day.date) },
          onOpenRecipe = onOpenRecipe,
          onEaten = viewModel::setEaten,
          kcalTarget = state.kcalTarget,
          proteinTarget = state.proteinTarget,
        )
      }
    }
  }
}

/** Items above the day cards: the hero, the week heading and the day strip. */
private const val HEADER_ITEMS = 3

/** The selected day at a glance: its workout and status, calories, and quick actions. */
@Composable
private fun HomeHero(
  state: HomeState,
  onOpenWorkout: (String, LocalDate) -> Unit,
  onOpenSession: (String) -> Unit,
  onOpenWeight: () -> Unit,
  onOpenPowerLevel: () -> Unit,
  onOpenHistory: () -> Unit,
) {
  val locale = LocalConfiguration.current.locales[0]
  val day = state.days.firstOrNull { it.date == state.selected }
  HeroHeader {
    HeroTitle(
      if (state.selected == state.today) stringResource(R.string.home_today)
      else state.selected.dayOfWeek.getDisplayName(TextStyle.FULL, locale)
    )
    val block = day?.block
    HeroLabel(
      listOfNotNull(
          state.selected.formatDayShort(),
          block?.let { stringResource(R.string.block_week_of, it.blockName, it.week, it.totalWeeks) },
        )
        .joinToString(" · ")
    )
    if (day != null) {
      val workout = day.workout
      HeroValue(workout?.name ?: stringResource(R.string.workout_rest_day), modifier = Modifier.padding(top = 2.dp))
      Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        when {
          workout == null -> Unit
          day.workoutDone -> HeroPill(stringResource(R.string.home_workout_done), icon = R.drawable.ic_menu_check)
          day.workoutMissed -> HeroPill(stringResource(R.string.home_workout_missed))
          else -> HeroPill(pluralStringResource(R.plurals.exercise_count, workout.exerciseCount, workout.exerciseCount))
        }
        val total = day.meals.total.kcal
        if (day.meals.meals.isNotEmpty()) HeroPill(stringResource(R.string.home_hero_kcal, formatKcal(total)))
      }
      state.openSession?.let { open ->
        HeroPill(
          stringResource(R.string.home_hero_in_progress, open.workoutName, open.doneCount, open.setCount),
          Modifier.padding(top = 8.dp).testTag("session_banner"),
          icon = R.drawable.ic_menu_play,
        )
      }
      val target = state.kcalTarget
      if (day.meals.anyEaten || target != null) {
        HeroCaption(
          stringResource(R.string.home_hero_eaten, formatKcal(day.meals.eaten.kcal), target?.let { formatKcal(it.toDouble()) } ?: formatKcal(day.meals.total.kcal)),
          Modifier.padding(top = 14.dp, bottom = 6.dp),
        )
        val goal = target?.toDouble() ?: day.meals.total.kcal
        HeroProgress(if (goal > 0) (day.meals.eaten.kcal / goal).toFloat() else 0f)
      }
    }
    HeroActions {
      val open = state.openSession
      val workout = day?.workout
      when {
        open != null -> HeroAction(R.drawable.ic_menu_play, stringResource(R.string.home_session_resume), { onOpenSession(open.id) }, Modifier.testTag("resume_session"))
        workout != null && day.date >= state.today ->
          HeroAction(R.drawable.ic_menu_play, stringResource(R.string.workout_hero_train), { onOpenWorkout(workout.id, day.date) }, Modifier.testTag("hero_train"))
        else -> HeroAction(R.drawable.ic_menu_history, stringResource(R.string.history_title), onOpenHistory, Modifier.testTag("hero_history"))
      }
      HeroAction(R.drawable.ic_menu_weight, stringResource(R.string.workout_hero_weight), onOpenWeight, Modifier.testTag("home_weight"))
      HeroAction(R.drawable.ic_menu_power, stringResource(R.string.workout_hero_power), onOpenPowerLevel, Modifier.testTag("hero_power"))
      if (open != null || (workout != null && day.date >= state.today)) {
        HeroAction(R.drawable.ic_menu_history, stringResource(R.string.history_title), onOpenHistory, Modifier.testTag("hero_history"))
      }
    }
  }
}

@Composable
private fun DayStrip(days: List<WeekDay>, selected: LocalDate, onSelect: (LocalDate) -> Unit) {
  val locale = LocalConfiguration.current.locales[0]
  Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
    days.forEach { day ->
      val isSelected = day.date == selected
      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier =
          Modifier.weight(1f)
            .clip(RoundedCornerShape(12.dp))
            .clickable { onSelect(day.date) }
            .then(
              if (isSelected) Modifier.background(MaterialTheme.colorScheme.secondaryContainer) else Modifier
            )
            .padding(vertical = 6.dp)
            .testTag("strip_${day.date}"),
      ) {
        Text(
          day.date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale),
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Box(
          contentAlignment = Alignment.Center,
          modifier =
            Modifier.padding(top = 2.dp)
              .size(32.dp)
              .clip(CircleShape)
              .then(if (day.isToday) Modifier.background(MaterialTheme.colorScheme.primary) else Modifier),
        ) {
          Text(
            day.date.dayOfMonth.toString(),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = if (day.isToday) FontWeight.Bold else FontWeight.Normal,
            color = if (day.isToday) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
          )
        }
        // Dots: training day (primary), meals planned (tertiary).
        Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
          Dot(visible = day.workout != null, color = MaterialTheme.colorScheme.primary)
          Dot(visible = day.meals.meals.isNotEmpty(), color = MaterialTheme.colorScheme.tertiary)
        }
      }
    }
  }
}

@Composable
private fun Dot(visible: Boolean, color: androidx.compose.ui.graphics.Color) {
  Box(Modifier.size(6.dp).clip(CircleShape).background(if (visible) color else color.copy(alpha = 0f)))
}

@Composable
private fun DayCard(
  day: WeekDay,
  selected: Boolean,
  onSelect: () -> Unit,
  onOpenWorkout: (String) -> Unit,
  onOpenRecipe: (String) -> Unit,
  onEaten: (mealId: String, eaten: Boolean) -> Unit = { _, _ -> },
  kcalTarget: Int? = null,
  proteinTarget: Int? = null,
) {
  Card(
    modifier = Modifier.fillMaxWidth().testTag("day_card_${day.date}"),
    colors =
      CardDefaults.cardColors(
        containerColor =
          if (day.isToday) MaterialTheme.colorScheme.surfaceContainerHigh
          else MaterialTheme.colorScheme.surfaceContainerLow
      ),
    border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
    onClick = onSelect,
  ) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
          day.date.formatDayShort(),
          style = MaterialTheme.typography.titleMedium,
          modifier = Modifier.weight(1f),
        )
        if (day.isToday) {
          Text(
            stringResource(R.string.home_today),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
          )
        }
      }
      day.block?.let {
        Text(
          stringResource(R.string.block_week_of, it.blockName, it.week, it.totalWeeks),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
      Spacer(Modifier.height(2.dp))
      val workout = day.workout
      if (workout != null) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier =
            Modifier.fillMaxWidth()
              .clip(RoundedCornerShape(12.dp))
              .background(MaterialTheme.colorScheme.primaryContainer)
              .clickable { onOpenWorkout(workout.id) }
              .padding(horizontal = 12.dp, vertical = 10.dp)
              .testTag("day_workout_${day.date}"),
        ) {
          Icon(
            painterResource(R.drawable.ic_tab_workout),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(20.dp),
          )
          Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(
              workout.name,
              style = MaterialTheme.typography.titleSmall,
              color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
              pluralStringResource(R.plurals.exercise_count, workout.exerciseCount, workout.exerciseCount),
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
          }
          if (day.workoutDone) {
            Icon(
              Icons.Filled.CheckCircle,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.onPrimaryContainer,
              modifier = Modifier.size(20.dp),
            )
            Text(
              stringResource(R.string.home_workout_done),
              style = MaterialTheme.typography.labelLarge,
              color = MaterialTheme.colorScheme.onPrimaryContainer,
              modifier = Modifier.padding(start = 4.dp, end = 4.dp).testTag("day_done_${day.date}"),
            )
          }
          if (day.workoutMissed) {
            Text(
              stringResource(R.string.home_workout_missed),
              style = MaterialTheme.typography.labelLarge,
              color = MaterialTheme.colorScheme.onPrimaryContainer,
              modifier = Modifier.padding(end = 4.dp).testTag("day_missed_${day.date}"),
            )
          }
          Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
          )
        }
      } else {
        Text(
          stringResource(if (day.block != null) R.string.workout_rest_day else R.string.home_no_block),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      if (day.otherDone.isNotEmpty()) {
        Text(
          stringResource(R.string.home_also_done, day.otherDone.joinToString { it.workoutName }),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      if (day.meals.meals.isNotEmpty()) {
        HorizontalDivider(Modifier.padding(vertical = 4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(stringResource(R.string.home_meals), style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
          Text(
            formatKcal(day.meals.total.kcal) + if (day.meals.complete) "" else "*",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.testTag("day_kcal_${day.date}"),
          )
        }
        DayProgress(day.meals, kcalTarget, proteinTarget, tag = "day_progress_${day.date}")
        day.meals.meals.forEach { m ->
          Row(verticalAlignment = Alignment.CenterVertically) {
          EatenToggle(m, day.today, onToggle = { onEaten(m.id, it) }, tag = "day_eaten_${day.date}_${m.recipeName}")
          Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier =
              Modifier.weight(1f)
                .clip(RoundedCornerShape(8.dp))
                .clickable { onOpenRecipe(m.recipeId) }
                .padding(vertical = 6.dp)
                .testTag("day_meal_${day.date}_${m.recipeName}"),
          ) {
            Text(
              slotLabel(m.slot),
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.width(76.dp),
            )
            Text(m.recipeName, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text(formatKcal(m.macros.kcal), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
          }
        }
      }
    }
  }
}
