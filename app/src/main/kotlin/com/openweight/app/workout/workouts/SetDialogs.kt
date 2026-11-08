package com.openweight.app.workout.workouts

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.openweight.app.R
import com.openweight.app.core.model.WeightUnit
import com.openweight.app.core.model.formatNumber
import com.openweight.app.core.model.formatRest
import com.openweight.app.core.model.label
import com.openweight.app.core.model.toKg
import com.openweight.app.workout.data.PlannedSet
import com.openweight.app.workout.data.SetValues
import com.openweight.app.workout.data.WorkoutRepository

private val REST_PRESETS = listOf(60, 90, 120, 150, 180)

/** Text-field state for one set's values, in the user's weight unit. */
class SetForm(initial: SetValues, private val unit: WeightUnit) {
  var repsMin by mutableStateOf(initial.repsMin.toString())
  var repsMax by mutableStateOf(initial.repsMax?.toString().orEmpty())
  var weight by mutableStateOf(initial.weightKg?.let(unit::formatNumber).orEmpty())
  var rest by mutableStateOf(initial.restSeconds.toString())

  /** The values, or null with [error] set when a field is invalid. */
  fun toValues(): SetValues? {
    error = null
    val min = repsMin.trim().toIntOrNull()
    val max = repsMax.trim().takeIf { it.isNotEmpty() }?.let { it.toIntOrNull() ?: -1 }
    val w = weight.trim().replace(',', '.').takeIf { it.isNotEmpty() }?.let { it.toDoubleOrNull() ?: -1.0 }
    val r = rest.trim().toIntOrNull()
    return when {
      min == null || min < 1 || min > SetValues.MAX_REPS -> null.also { error = FormError.REPS }
      max != null && (max < min || max > SetValues.MAX_REPS) -> null.also { error = FormError.REPS_MAX }
      w != null && w < 0 -> null.also { error = FormError.WEIGHT }
      r == null || r < 0 || r > SetValues.MAX_REST_SECONDS -> null.also { error = FormError.REST }
      else -> SetValues(min, max?.takeIf { it != min }, w?.let(unit::toKg), r)
    }
  }

  var error by mutableStateOf<FormError?>(null)
    private set
}

enum class FormError { REPS, REPS_MAX, WEIGHT, REST, SETS }

@Composable
private fun SetFields(form: SetForm, unit: WeightUnit) {
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
      NumberField(
        value = form.repsMin,
        onChange = { form.repsMin = it },
        label = stringResource(R.string.set_reps_from),
        isError = form.error == FormError.REPS,
        modifier = Modifier.weight(1f).testTag("reps_min"),
      )
      Text("–")
      NumberField(
        value = form.repsMax,
        onChange = { form.repsMax = it },
        label = stringResource(R.string.set_reps_to),
        isError = form.error == FormError.REPS_MAX,
        modifier = Modifier.weight(1f).testTag("reps_max"),
      )
    }
    NumberField(
      value = form.weight,
      onChange = { form.weight = it },
      label = stringResource(R.string.set_weight, unit.label),
      placeholder = stringResource(R.string.set_weight_empty),
      isError = form.error == FormError.WEIGHT,
      decimal = true,
      modifier = Modifier.fillMaxWidth().testTag("weight"),
    )
    NumberField(
      value = form.rest,
      onChange = { form.rest = it },
      label = stringResource(R.string.set_rest_seconds),
      isError = form.error == FormError.REST,
      modifier = Modifier.fillMaxWidth().testTag("rest"),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
      REST_PRESETS.forEach { seconds ->
        FilterChip(
          selected = form.rest == seconds.toString(),
          onClick = { form.rest = seconds.toString() },
          label = { Text(formatRest(seconds)) },
        )
      }
    }
    form.error?.let { Text(errorText(it), color = MaterialTheme.colorScheme.error) }
  }
}

@Composable
private fun errorText(error: FormError): String =
  when (error) {
    FormError.REPS -> stringResource(R.string.error_reps)
    FormError.REPS_MAX -> stringResource(R.string.error_reps_max)
    FormError.WEIGHT -> stringResource(R.string.error_weight)
    FormError.REST -> stringResource(R.string.error_rest)
    FormError.SETS -> stringResource(R.string.error_sets)
  }

@Composable
private fun NumberField(
  value: String,
  onChange: (String) -> Unit,
  label: String,
  isError: Boolean,
  modifier: Modifier = Modifier,
  placeholder: String? = null,
  decimal: Boolean = false,
) {
  OutlinedTextField(
    value = value,
    onValueChange = onChange,
    label = { Text(label) },
    placeholder = placeholder?.let { { Text(it) } },
    isError = isError,
    singleLine = true,
    keyboardOptions =
      KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number),
    modifier = modifier,
  )
}

/** Edits one planned set; can apply the values to every set of the exercise. */
@Composable
fun EditSetDialog(
  set: PlannedSet,
  setNumber: Int,
  unit: WeightUnit,
  onSave: (SetValues, applyToAll: Boolean) -> Unit,
  onDelete: () -> Unit,
  onDismiss: () -> Unit,
) {
  val form = remember(set.id) {
    SetForm(SetValues(set.repsMin, set.repsMax, set.weightKg, set.restSeconds), unit)
  }
  var applyToAll by remember { mutableStateOf(false) }
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(stringResource(R.string.set_edit_title, setNumber)) },
    text = {
      Column(Modifier.verticalScroll(rememberScrollState())) {
        SetFields(form, unit)
        Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier.fillMaxWidth().clickable { applyToAll = !applyToAll },
        ) {
          Checkbox(checked = applyToAll, onCheckedChange = { applyToAll = it })
          Text(stringResource(R.string.set_apply_all))
        }
      }
    },
    confirmButton = {
      TextButton(
        onClick = { form.toValues()?.let { onSave(it, applyToAll) } },
        modifier = Modifier.testTag("save_set"),
      ) {
        Text(stringResource(R.string.action_save))
      }
    },
    dismissButton = {
      Row {
        TextButton(onClick = onDelete) { Text(stringResource(R.string.set_delete)) }
        TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
      }
    },
  )
}

/** Adds an exercise to a workout with several identical sets, e.g. 4 × 8–10 at 60 kg. */
@Composable
fun AddSetsDialog(
  exerciseName: String,
  unit: WeightUnit,
  onAdd: (setCount: Int, SetValues) -> Unit,
  onDismiss: () -> Unit,
) {
  val form = remember { SetForm(SetValues(repsMin = 8, repsMax = 12), unit) }
  var sets by remember { mutableStateOf("3") }
  var setsError by remember { mutableStateOf(false) }
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(exerciseName) },
    text = {
      Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        NumberField(
          value = sets,
          onChange = {
            sets = it
            setsError = false
          },
          label = stringResource(R.string.set_count),
          isError = setsError,
          modifier = Modifier.fillMaxWidth().testTag("set_count"),
        )
        if (setsError) {
          Text(stringResource(R.string.error_sets), color = MaterialTheme.colorScheme.error)
        }
        SetFields(form, unit)
      }
    },
    confirmButton = {
      TextButton(
        onClick = {
          val count = sets.trim().toIntOrNull()
          setsError = count == null || count !in 1..WorkoutRepository.MAX_SETS
          val values = form.toValues()
          if (!setsError && values != null) onAdd(count!!, values)
        },
        modifier = Modifier.testTag("add_sets"),
      ) {
        Text(stringResource(R.string.action_add))
      }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
  )
}
