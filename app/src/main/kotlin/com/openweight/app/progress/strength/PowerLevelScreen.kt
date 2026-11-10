package com.openweight.app.progress.strength

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.openweight.app.R
import com.openweight.app.core.model.WeightUnit
import com.openweight.app.core.model.format
import com.openweight.app.core.model.fromKg
import com.openweight.app.core.model.label
import com.openweight.app.core.ui.EmptyState
import com.openweight.app.core.ui.ScreenScaffold
import com.openweight.app.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.Locale
import javax.inject.Inject
import kotlin.math.floor
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class PowerLevelState(val loading: Boolean = true, val level: PowerLevel? = null, val unit: WeightUnit = WeightUnit.KG)

@HiltViewModel
class PowerLevelViewModel @Inject constructor(strength: StrengthRepository, settings: SettingsRepository) : ViewModel() {
  val state: StateFlow<PowerLevelState> =
    combine(strength.powerLevel(), settings.settings) { level, s -> PowerLevelState(false, level, s.weightUnit) }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PowerLevelState())
}

@Composable
fun Tier.label(): String =
  stringResource(
    when (this) {
      Tier.UNTRAINED -> R.string.tier_untrained
      Tier.NOVICE -> R.string.tier_novice
      Tier.INTERMEDIATE -> R.string.tier_intermediate
      Tier.PROFICIENT -> R.string.tier_proficient
      Tier.ADVANCED -> R.string.tier_advanced
      Tier.ELITE -> R.string.tier_elite
      Tier.WORLD_CLASS -> R.string.tier_world_class
    }
  )

@Composable
fun Muscle.label(): String =
  stringResource(
    when (this) {
      Muscle.CHEST -> R.string.muscle_chest
      Muscle.SHOULDERS -> R.string.muscle_shoulders
      Muscle.TRAPS -> R.string.muscle_traps
      Muscle.BICEPS -> R.string.muscle_biceps
      Muscle.TRICEPS -> R.string.muscle_triceps
      Muscle.FOREARMS -> R.string.muscle_forearms
      Muscle.UPPER_BACK -> R.string.muscle_upper_back
      Muscle.LOWER_BACK -> R.string.muscle_lower_back
      Muscle.ABS -> R.string.muscle_abs
      Muscle.GLUTES -> R.string.muscle_glutes
      Muscle.QUADS -> R.string.muscle_quads
      Muscle.HAMSTRINGS -> R.string.muscle_hamstrings
      Muscle.CALVES -> R.string.muscle_calves
    }
  )

/** The power level: an overall tier, a body map coloured by tier, then each muscle and lift. */
@Composable
fun PowerLevelRoute(onBack: () -> Unit, onOpenWeight: () -> Unit, onOpenExercise: (String) -> Unit, viewModel: PowerLevelViewModel = hiltViewModel()) {
  val state by viewModel.state.collectAsStateWithLifecycle()
  ScreenScaffold(title = stringResource(R.string.power_title), onBack = onBack) { padding ->
    if (state.loading) return@ScreenScaffold
    when (val level = state.level) {
      null,
      PowerLevel.NeedsBodyWeight ->
        Column(Modifier.fillMaxSize().padding(padding).testTag("power_needs_weight"), horizontalAlignment = Alignment.CenterHorizontally) {
          EmptyState(stringResource(R.string.power_needs_weight), hint = stringResource(R.string.power_needs_weight_hint))
          Button(onClick = onOpenWeight, modifier = Modifier.testTag("power_log_weight")) { Text(stringResource(R.string.power_log_weight)) }
        }
      is PowerLevel.Ready -> PowerLevelContent(level, state.unit, onOpenExercise, Modifier.padding(padding))
    }
  }
}

@Composable
private fun PowerLevelContent(level: PowerLevel.Ready, unit: WeightUnit, onOpenExercise: (String) -> Unit, modifier: Modifier) {
  val report = level.report
  LazyColumn(modifier.fillMaxSize().testTag("power_level"), contentPadding = PaddingValues(bottom = 24.dp)) {
    item { OverallCard(level, unit) }
    if (report.lifts.isEmpty()) {
      item { EmptyState(stringResource(R.string.power_empty), hint = stringResource(R.string.power_empty_hint)) }
    } else {
      item { BodyMaps(report) }
      item { Legend() }
      item { Header(stringResource(R.string.power_muscles)) }
      items(report.muscles.values.sortedByDescending { it.score }, key = { "m_${it.muscle}" }) { MuscleRow(it) }
      item { Header(stringResource(R.string.power_lifts)) }
      items(report.lifts, key = { "l_${it.name}" }) { lift -> LiftRow(lift, report.bodyWeightKg, unit, onOpenExercise) }
    }
    if (report.unscored.isNotEmpty()) {
      item {
        Text(
          stringResource(R.string.power_unscored, report.unscored.joinToString(", ")),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(16.dp).testTag("power_unscored"),
        )
      }
    }
  }
}

@Composable
private fun OverallCard(level: PowerLevel.Ready, unit: WeightUnit) {
  val report = level.report
  val overall = report.overall
  val tier = report.overallTier
  Card(Modifier.fillMaxWidth().padding(16.dp).testTag("power_overall")) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Text(stringResource(R.string.power_overall), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
      if (overall == null || tier == null) {
        Text(stringResource(R.string.power_no_level), style = MaterialTheme.typography.titleMedium)
      } else {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
          TierBadge(tier, big = true)
          Column {
            Text(tier.label(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.testTag("power_tier"))
            Text(
              stringResource(R.string.power_tier_of, tier.ordinal + 1, Tier.entries.size, towardsNext(overall)),
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
        TierProgress(overall)
        val strongest = report.strongest
        val weakest = report.weakest
        if (strongest != null && weakest != null && strongest.muscle != weakest.muscle) {
          Text(stringResource(R.string.power_strongest_weakest, strongest.muscle.label(), weakest.muscle.label()), style = MaterialTheme.typography.bodyMedium)
        }
      }
      val standard = stringResource(if (level.standard == StrengthStandard.MEN) R.string.strength_standard_men else R.string.strength_standard_women)
      Text(
        stringResource(R.string.power_basis, unit.format(report.bodyWeightKg), standard),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

/** The way from this tier to the next: the fraction of the score past its whole part. */
@Composable
private fun TierProgress(score: Double, modifier: Modifier = Modifier) {
  val tier = StrengthRules.tierOf(score)
  val within = if (tier == Tier.WORLD_CLASS) 1f else (score - floor(score)).toFloat().coerceIn(0f, 1f)
  LinearProgressIndicator(
    progress = { within },
    modifier = modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
    color = tier.color,
    trackColor = MaterialTheme.colorScheme.surfaceVariant,
    strokeCap = StrokeCap.Round,
    drawStopIndicator = {},
  )
}

@Composable
private fun TierBadge(tier: Tier, big: Boolean = false) {
  val size = if (big) 44.dp else 28.dp
  Box(Modifier.size(size).clip(CircleShape).background(tier.color), contentAlignment = Alignment.Center) {
    Text("${tier.ordinal + 1}", style = if (big) MaterialTheme.typography.titleLarge else MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = androidx.compose.ui.graphics.Color.Black)
  }
}

@Composable
private fun BodyMaps(report: StrengthReport) {
  val summary =
    stringResource(
      R.string.power_body_description,
      report.muscles.values.sortedByDescending { it.score }.map { "${it.muscle.label()}: ${it.tier.label()}" }.joinToString(", "),
    )
  Row(
    Modifier.fillMaxWidth().padding(horizontal = 16.dp).semantics(mergeDescendants = true) { contentDescription = summary }.testTag("power_body"),
    horizontalArrangement = Arrangement.SpaceEvenly,
  ) {
    for (side in BodySide.entries) {
      Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
        BodyFigure(side, report.muscles, Modifier.height(280.dp))
        Text(
          stringResource(if (side == BodySide.FRONT) R.string.power_front else R.string.power_back),
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Legend() {
  FlowRow(
    Modifier.fillMaxWidth().padding(16.dp).testTag("power_legend"),
    horizontalArrangement = Arrangement.spacedBy(12.dp),
    verticalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    for (tier in Tier.entries) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(12.dp).clip(CircleShape).background(tier.color))
        Spacer(Modifier.width(6.dp))
        Text("${tier.ordinal + 1} ${tier.label()}", style = MaterialTheme.typography.bodySmall)
      }
    }
  }
}

@Composable
private fun Header(text: String) {
  Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp))
}

@Composable
private fun MuscleRow(score: MuscleScore) {
  ListItem(
    modifier = Modifier.testTag("muscle_${score.muscle}"),
    leadingContent = { TierBadge(score.tier) },
    headlineContent = { Text(score.muscle.label()) },
    supportingContent = {
      Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.power_muscle_line, score.tier.label(), towardsNext(score.score)))
        TierProgress(score.score)
      }
    },
  )
  HorizontalDivider()
}

@Composable
private fun LiftRow(lift: LiftScore, bodyWeightKg: Double, unit: WeightUnit, onOpenExercise: (String) -> Unit) {
  val id = lift.exerciseId
  val best =
    when {
      lift.standard.load == Load.REPS -> stringResource(R.string.power_lift_reps, lift.reps)
      lift.weightKg == null -> stringResource(R.string.power_lift_bodyweight, lift.reps)
      else -> stringResource(R.string.power_lift_weight, unit.format(lift.weightKg), lift.reps)
    }
  val detail =
    if (lift.standard.load == Load.REPS) best
    else stringResource(R.string.power_lift_detail, best, unit.formatWhole(lift.value * bodyWeightKg), String.format(Locale.US, "%.2f", lift.value))
  val nextTier = Tier.entries.getOrNull(lift.tier.ordinal + 1)
  val next =
    when (val goal = lift.next) {
      null -> stringResource(R.string.power_next_top)
      is NextGoal.OneRepMax -> stringResource(R.string.power_next_1rm, nextTier!!.label(), unit.formatWhole(goal.kg))
      is NextGoal.Reps -> stringResource(R.string.power_next_reps, nextTier!!.label(), goal.reps)
      is NextGoal.Added -> stringResource(R.string.power_next_added, nextTier!!.label(), unit.formatWhole(goal.kg))
    }
  ListItem(
    modifier = (if (id != null) Modifier.clickable { onOpenExercise(id) } else Modifier).testTag("lift_${lift.name}"),
    leadingContent = { TierBadge(lift.tier) },
    headlineContent = { Text(lift.name) },
    supportingContent = {
      Column {
        Text(stringResource(R.string.power_lift_tier, lift.tier.label(), detail))
        Text(next, color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
    },
  )
  HorizontalDivider()
}

/** Estimates and goals in whole units: decimals would claim a precision they don't have. */
private fun WeightUnit.formatWhole(kg: Double): String = "${kotlin.math.round(fromKg(kg)).toInt()} $label"

/** "40% of the way to Advanced", or that this is the top tier. */
@Composable
private fun towardsNext(score: Double): String {
  val next = Tier.entries.getOrNull(StrengthRules.tierOf(score).ordinal + 1) ?: return stringResource(R.string.power_top_tier)
  val percent = ((score - floor(score)) * 100).toInt().coerceIn(0, 99)
  return stringResource(R.string.power_towards, percent, next.label())
}
