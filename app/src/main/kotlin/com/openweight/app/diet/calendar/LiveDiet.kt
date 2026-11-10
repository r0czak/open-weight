package com.openweight.app.diet.calendar

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Box
import com.openweight.app.R
import com.openweight.app.diet.data.DayMeals
import com.openweight.app.diet.data.PlannedMeal
import com.openweight.app.diet.formatGrams
import com.openweight.app.diet.formatKcal

/** A meal's "eaten" tick: its own tap target, read by TalkBack as "Porridge eaten, checkbox". */
@Composable
fun EatenToggle(meal: PlannedMeal, onToggle: (Boolean) -> Unit, tag: String, modifier: Modifier = Modifier) {
  val label = stringResource(R.string.meal_eaten_label, meal.recipeName)
  Box(
    contentAlignment = Alignment.Center,
    modifier =
      modifier
        .minimumInteractiveComponentSize()
        .clip(CircleShape)
        .toggleable(value = meal.eaten, role = Role.Checkbox, onValueChange = onToggle)
        .semantics { contentDescription = label }
        .testTag(tag),
  ) {
    if (meal.eaten) {
      Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
    } else {
      Icon(painterResource(R.drawable.ic_circle_outline), contentDescription = null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(24.dp))
    }
  }
}

/**
 * "Eaten 1,450 of 2,900 kcal · 95 of 190 g protein" and, with a target, what's left of it. Shows
 * the plan's totals until something is eaten.
 */
@Composable
fun dayProgressText(day: DayMeals, kcalTarget: Int?, proteinTarget: Int?): String {
  val eaten = day.eaten
  val parts = mutableListOf<String>()
  parts += stringResource(R.string.day_eaten_kcal, formatKcal(eaten.kcal), formatKcal(day.total.kcal))
  parts += stringResource(R.string.day_eaten_protein, formatGrams(eaten.protein), formatGrams(day.total.protein))
  if (kcalTarget != null) {
    val left = kcalTarget - eaten.kcal
    parts += if (left >= 0) stringResource(R.string.day_left_kcal, formatKcal(left)) else stringResource(R.string.day_over_kcal, formatKcal(-left))
  }
  if (proteinTarget != null) {
    val left = proteinTarget - eaten.protein
    if (left > 0) parts += stringResource(R.string.day_left_protein, formatGrams(left))
  }
  return parts.joinToString(" · ")
}

/** Shown once something is eaten or a target is set; before that the day's totals say it all. */
@Composable
fun DayProgress(day: DayMeals, kcalTarget: Int?, proteinTarget: Int?, tag: String, modifier: Modifier = Modifier) {
  if (!day.anyEaten && kcalTarget == null && proteinTarget == null) return
  Text(
    dayProgressText(day, kcalTarget, proteinTarget),
    style = MaterialTheme.typography.bodySmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = modifier.testTag(tag),
  )
}
