package com.openweight.app.diet.household

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.openweight.app.R
import com.openweight.app.diet.formatNumber
import com.openweight.app.diet.parseNumber

/** "¾", "½", "1.2": a portion as people say it. */
fun formatPortion(portion: Double): String =
  when (Math.round(portion * 100)) {
    25L -> "¼"
    50L -> "½"
    75L -> "¾"
    else -> formatNumber(portion)
  }

/** Adds ([member] null) or edits someone in the household. */
@Composable
fun MemberDialog(
  member: HouseholdMember?,
  onSave: (name: String, portion: Double, eatsByDefault: Boolean) -> Unit,
  onDelete: (() -> Unit)?,
  onDismiss: () -> Unit,
) {
  var name by remember { mutableStateOf(member?.name.orEmpty()) }
  var portion by remember { mutableStateOf(member?.portion?.let(::formatNumber) ?: "0.75") }
  var byDefault by remember { mutableStateOf(member?.eatsByDefault ?: true) }
  var nameError by remember { mutableStateOf(false) }
  var portionError by remember { mutableStateOf(false) }
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(stringResource(if (member == null) R.string.household_add else R.string.household_edit)) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
          value = name,
          onValueChange = { name = it; nameError = false },
          label = { Text(stringResource(R.string.household_name)) },
          isError = nameError,
          singleLine = true,
          keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
          modifier = Modifier.fillMaxWidth().testTag("member_name"),
        )
        OutlinedTextField(
          value = portion,
          onValueChange = { portion = it; portionError = false },
          label = { Text(stringResource(R.string.household_portion)) },
          isError = portionError,
          supportingText = {
            Text(
              if (portionError) stringResource(R.string.household_portion_error, formatNumber(HouseholdRepository.MIN_PORTION), formatNumber(HouseholdRepository.MAX_PORTION))
              else stringResource(R.string.household_portion_hint)
            )
          },
          singleLine = true,
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
          modifier = Modifier.fillMaxWidth().testTag("member_portion"),
        )
        Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier.fillMaxWidth().toggleable(value = byDefault, role = Role.Switch, onValueChange = { byDefault = it }).testTag("member_default"),
        ) {
          Text(stringResource(R.string.household_by_default), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
          Switch(checked = byDefault, onCheckedChange = null)
        }
      }
    },
    confirmButton = {
      TextButton(
        onClick = {
          val p = parseNumber(portion)
          nameError = name.isBlank()
          portionError = p == null || p !in HouseholdRepository.MIN_PORTION..HouseholdRepository.MAX_PORTION
          if (!nameError && !portionError) onSave(name, p!!, byDefault)
        },
        modifier = Modifier.testTag("member_save"),
      ) {
        Text(stringResource(R.string.action_save))
      }
    },
    dismissButton = {
      Row {
        onDelete?.let { TextButton(onClick = it, modifier = Modifier.testTag("member_delete")) { Text(stringResource(R.string.action_delete)) } }
        TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
      }
    },
  )
}
