package com.openweight.app.workout.blocks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.openweight.app.R
import com.openweight.app.core.ui.formatMedium
import com.openweight.app.core.ui.pickerMillisToDate
import com.openweight.app.core.ui.toPickerMillis
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Quick lengths: the block ends the day before the same weekday N weeks (or N months) later. */
private enum class BlockLength(val weeks: Long = 0, val months: Long = 0) {
  W4(weeks = 4),
  W6(weeks = 6),
  W8(weeks = 8),
  W12(weeks = 12),
  M3(months = 3),
  M4(months = 4),
  M6(months = 6);

  fun endFor(start: LocalDate): LocalDate =
    (if (months > 0) start.plusMonths(months) else start.plusWeeks(weeks)).minusDays(1)
}

/** Name, dates, length shortcuts and (when [initialNotes] is not null) notes of a block. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun BlockInfoDialog(
  title: String,
  initialName: String,
  initialStart: LocalDate,
  initialEnd: LocalDate,
  initialNotes: String?,
  onDismiss: () -> Unit,
  onConfirm: (name: String, start: LocalDate, end: LocalDate, notes: String) -> Unit,
) {
  var name by remember { mutableStateOf(initialName) }
  var start by remember { mutableStateOf(initialStart) }
  var end by remember { mutableStateOf(initialEnd) }
  var notes by remember { mutableStateOf(initialNotes.orEmpty()) }
  var nameError by remember { mutableStateOf(false) }
  var picking by remember { mutableStateOf<String?>(null) } // "start" or "end"

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(title) },
    text = {
      Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
          value = name,
          onValueChange = {
            name = it
            nameError = false
          },
          label = { Text(stringResource(R.string.field_name)) },
          placeholder = { Text(stringResource(R.string.block_name_hint)) },
          isError = nameError,
          supportingText = { if (nameError) Text(stringResource(R.string.error_name_empty)) },
          singleLine = true,
          keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
          modifier = Modifier.fillMaxWidth().testTag("block_name"),
        )
        Text(stringResource(R.string.block_starts), style = MaterialTheme.typography.labelLarge)
        OutlinedButton(onClick = { picking = "start" }, modifier = Modifier.fillMaxWidth().testTag("block_start")) {
          Text(start.formatMedium())
        }
        Text(stringResource(R.string.block_ends), style = MaterialTheme.typography.labelLarge)
        OutlinedButton(onClick = { picking = "end" }, modifier = Modifier.fillMaxWidth().testTag("block_end")) {
          Text(end.formatMedium())
        }
        Text(stringResource(R.string.block_length), style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          BlockLength.entries.forEach { length ->
            FilterChip(
              selected = length.endFor(start) == end,
              onClick = { end = length.endFor(start) },
              label = {
                Text(
                  if (length.months > 0) {
                    pluralStringResource(R.plurals.block_length_months, length.months.toInt(), length.months.toInt())
                  } else {
                    pluralStringResource(R.plurals.block_length_weeks, length.weeks.toInt(), length.weeks.toInt())
                  }
                )
              },
              modifier = Modifier.testTag("length_${length.name}"),
            )
          }
        }
        val days = ChronoUnit.DAYS.between(start, end) + 1
        Text(
          if (end.isBefore(start)) stringResource(R.string.error_block_dates)
          else pluralStringResource(R.plurals.block_weeks, ((days + 6) / 7).toInt(), ((days + 6) / 7).toInt()),
          color =
            if (end.isBefore(start)) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.onSurfaceVariant,
          style = MaterialTheme.typography.bodySmall,
        )
        if (initialNotes != null) {
          OutlinedTextField(
            value = notes,
            onValueChange = { notes = it },
            label = { Text(stringResource(R.string.field_notes)) },
            minLines = 3,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth(),
          )
        }
      }
    },
    confirmButton = {
      TextButton(
        onClick = {
          nameError = name.isBlank()
          if (!nameError && !end.isBefore(start)) onConfirm(name, start, end, notes)
        },
        modifier = Modifier.testTag("confirm_block"),
      ) {
        Text(stringResource(R.string.action_save))
      }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
  )

  picking?.let { which ->
    val initial = if (which == "start") start else end
    val pickerState = rememberDatePickerState(initialSelectedDateMillis = initial.toPickerMillis())
    DatePickerDialog(
      onDismissRequest = { picking = null },
      confirmButton = {
        TextButton(
          onClick = {
            pickerState.selectedDateMillis?.let { millis ->
              val date = pickerMillisToDate(millis)
              if (which == "start") {
                // Keep the block's length when its start moves.
                val length = ChronoUnit.DAYS.between(start, end)
                start = date
                end = if (length >= 0) date.plusDays(length) else date
              } else {
                end = date
              }
            }
            picking = null
          }
        ) {
          Text(stringResource(R.string.action_ok))
        }
      },
      dismissButton = { TextButton(onClick = { picking = null }) { Text(stringResource(R.string.action_cancel)) } },
    ) {
      DatePicker(state = pickerState)
    }
  }
}
