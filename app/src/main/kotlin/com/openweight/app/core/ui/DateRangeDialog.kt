package com.openweight.app.core.ui

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.openweight.app.R
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Picks a first and last day (one day is fine), at most [maxDays] days long when given. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateRangeDialog(
  title: String,
  confirmLabel: String,
  initialFrom: LocalDate,
  initialTo: LocalDate,
  onDismiss: () -> Unit,
  onConfirm: (LocalDate, LocalDate) -> Unit,
  maxDays: Int? = null,
) {
  val picker =
    rememberDateRangePickerState(initialSelectedStartDateMillis = initialFrom.toPickerMillis(), initialSelectedEndDateMillis = initialTo.toPickerMillis())
  val from = picker.selectedStartDateMillis?.let(::pickerMillisToDate)
  val to = picker.selectedEndDateMillis?.let(::pickerMillisToDate) ?: from
  val tooLong = maxDays != null && from != null && to != null && ChronoUnit.DAYS.between(from, to) >= maxDays
  DatePickerDialog(
    onDismissRequest = onDismiss,
    confirmButton = {
      TextButton(onClick = { if (from != null && to != null) onConfirm(from, to) }, enabled = from != null && !tooLong, modifier = Modifier.testTag("dates_ok")) {
        Text(confirmLabel)
      }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
  ) {
    DateRangePicker(
      state = picker,
      title = { Text(title, Modifier.padding(start = 24.dp, top = 16.dp)) },
      headline = {
        Text(
          if (tooLong && maxDays != null) pluralStringResource(R.plurals.dates_too_long, maxDays, maxDays)
          else if (from != null && to != null) formatDateRange(from, to)
          else stringResource(R.string.dates_pick),
          style = MaterialTheme.typography.titleMedium,
          color = if (tooLong) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
          modifier = Modifier.padding(start = 24.dp, bottom = 8.dp),
        )
      },
      showModeToggle = false,
      modifier = Modifier.height(480.dp),
    )
  }
}
