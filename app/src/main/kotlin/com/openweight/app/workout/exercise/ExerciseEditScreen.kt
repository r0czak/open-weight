package com.openweight.app.workout.exercise

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.openweight.app.R
import com.openweight.app.core.ui.ScreenScaffold

@Composable
fun ExerciseEditRoute(
  exerciseId: String?,
  onDone: () -> Unit,
  viewModel: ExerciseEditViewModel =
    hiltViewModel<ExerciseEditViewModel, ExerciseEditViewModel.Factory>(
      key = "exercise-edit-$exerciseId"
    ) { factory ->
      factory.create(exerciseId)
    },
) {
  val state by viewModel.state.collectAsStateWithLifecycle()
  LaunchedEffect(state.done) { if (state.done) onDone() }
  var confirmDelete by remember { mutableStateOf(false) }

  ScreenScaffold(
    title =
      stringResource(if (state.isNew) R.string.exercise_new else R.string.exercise_edit),
    onBack = onDone,
    actions = {
      if (!state.isNew) {
        IconButton(onClick = { confirmDelete = true }) {
          Icon(Icons.Filled.Delete, stringResource(R.string.action_delete))
        }
      }
      TextButton(onClick = viewModel::save, modifier = Modifier.testTag("save_exercise")) {
        Text(stringResource(R.string.action_save))
      }
    },
  ) { padding ->
    if (!state.loaded) return@ScreenScaffold
    Column(
      Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      OutlinedTextField(
        value = state.name,
        onValueChange = viewModel::onNameChange,
        label = { Text(stringResource(R.string.field_name)) },
        isError = state.nameError != null,
        supportingText = {
          when (state.nameError) {
            NameError.EMPTY -> Text(stringResource(R.string.error_name_empty))
            NameError.DUPLICATE -> Text(stringResource(R.string.error_exercise_exists))
            null -> Unit
          }
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        modifier = Modifier.fillMaxWidth().testTag("exercise_name"),
      )
      OutlinedTextField(
        value = state.muscleGroup,
        onValueChange = viewModel::onMuscleGroupChange,
        label = { Text(stringResource(R.string.field_muscle_group)) },
        placeholder = { Text(stringResource(R.string.field_muscle_group_hint)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        modifier = Modifier.fillMaxWidth(),
      )
      OutlinedTextField(
        value = state.equipment,
        onValueChange = viewModel::onEquipmentChange,
        label = { Text(stringResource(R.string.field_equipment)) },
        placeholder = { Text(stringResource(R.string.field_equipment_hint)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        modifier = Modifier.fillMaxWidth(),
      )
      OutlinedTextField(
        value = state.notes,
        onValueChange = viewModel::onNotesChange,
        label = { Text(stringResource(R.string.field_notes)) },
        minLines = 3,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        modifier = Modifier.fillMaxWidth(),
      )
    }
  }

  if (confirmDelete) {
    AlertDialog(
      onDismissRequest = { confirmDelete = false },
      title = { Text(stringResource(R.string.exercise_delete_title)) },
      text = { Text(stringResource(R.string.exercise_delete_text, state.name)) },
      confirmButton = {
        TextButton(
          onClick = {
            confirmDelete = false
            viewModel.delete()
          }
        ) {
          Text(stringResource(R.string.action_delete))
        }
      },
      dismissButton = {
        TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.action_cancel)) }
      },
    )
  }

  state.inUseCount?.let { count ->
    AlertDialog(
      onDismissRequest = viewModel::dismissInUse,
      title = { Text(stringResource(R.string.exercise_in_use_title)) },
      text = { Text(pluralStringResource(R.plurals.exercise_in_use_text, count, count)) },
      confirmButton = {
        TextButton(onClick = viewModel::dismissInUse) { Text(stringResource(R.string.action_ok)) }
      },
    )
  }
}
