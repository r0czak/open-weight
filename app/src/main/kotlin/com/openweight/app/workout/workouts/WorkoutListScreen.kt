package com.openweight.app.workout.workouts

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.openweight.app.R
import com.openweight.app.workout.exercise.SearchField
import com.openweight.app.core.ui.CardListItem
import com.openweight.app.core.ui.EmptyState
import com.openweight.app.core.ui.ScreenScaffold
import com.openweight.app.workout.data.WorkoutSummary

@Composable
fun WorkoutListRoute(
  onBack: () -> Unit,
  onOpen: (String) -> Unit,
  viewModel: WorkoutListViewModel = hiltViewModel(),
) {
  val state by viewModel.state.collectAsStateWithLifecycle()
  val snackbar = remember { SnackbarHostState() }
  val resources = LocalResources.current
  var creating by remember { mutableStateOf(false) }

  LaunchedEffect(viewModel) {
    viewModel.events.collect { event ->
      when (event) {
        is WorkoutListEvent.Open -> onOpen(event.workoutId)
        is WorkoutListEvent.InUse ->
          snackbar.showSnackbar(
            resources.getQuantityString(
              R.plurals.workout_in_use,
              event.blockCount,
              event.blockCount,
            )
          )
      }
    }
  }

  ScreenScaffold(
    title = stringResource(R.string.workout_workouts),
    onBack = onBack,
    snackbarHostState = snackbar,
    floatingActionButton = {
      FloatingActionButton(onClick = { creating = true }, modifier = Modifier.testTag("add_workout")) {
        Icon(Icons.Filled.Add, stringResource(R.string.workout_new))
      }
    },
  ) { padding ->
    Column(Modifier.fillMaxSize().padding(padding)) {
      if (state.totalCount > 0) SearchField(state.query, viewModel::onQueryChange, placeholder = stringResource(R.string.workout_search))
      when {
        state.loading -> Unit
        state.totalCount == 0 ->
          EmptyState(
            stringResource(R.string.workout_empty),
            hint = stringResource(R.string.workout_empty_hint),
          )
        state.items.isEmpty() -> EmptyState(stringResource(R.string.search_no_results, state.query))
        else ->
          LazyColumn(contentPadding = PaddingValues(bottom = 88.dp)) {
            items(state.items, key = { it.id }) { item ->
              CardListItem(state.items.indexOf(item), state.items.size) {
                WorkoutRow(
                  item = item,
                  onOpen = { onOpen(item.id) },
                  onDuplicate = { viewModel.duplicate(item.id) },
                  onDelete = { viewModel.delete(item.id) },
                )
              }
            }
          }
      }
    }
  }

  if (creating) {
    WorkoutInfoDialog(
      title = stringResource(R.string.workout_new),
      initialName = "",
      initialType = "",
      initialNotes = null,
      onDismiss = { creating = false },
      onConfirm = { name, type, _ ->
        creating = false
        viewModel.create(name, type)
      },
    )
  }
}

@Composable
private fun WorkoutRow(
  item: WorkoutSummary,
  onOpen: () -> Unit,
  onDuplicate: () -> Unit,
  onDelete: () -> Unit,
) {
  var menu by remember { mutableStateOf(false) }
  var confirmDelete by remember { mutableStateOf(false) }
  ListItem(
    modifier = Modifier.clickable(onClick = onOpen),
    headlineContent = { Text(item.name) },
    supportingContent = {
      val parts = buildList {
        if (item.type.isNotBlank()) add(item.type)
        add(pluralStringResource(R.plurals.exercise_count, item.exerciseCount, item.exerciseCount))
        add(pluralStringResource(R.plurals.set_count, item.setCount, item.setCount))
      }
      Text(parts.joinToString(" · "))
    },
    trailingContent = {
      Box {
        IconButton(onClick = { menu = true }) {
          Icon(Icons.Filled.MoreVert, stringResource(R.string.action_more))
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
          DropdownMenuItem(
            text = { Text(stringResource(R.string.action_duplicate)) },
            onClick = {
              menu = false
              onDuplicate()
            },
          )
          DropdownMenuItem(
            text = { Text(stringResource(R.string.action_delete)) },
            onClick = {
              menu = false
              confirmDelete = true
            },
          )
        }
      }
    },
  )
  if (confirmDelete) {
    ConfirmDeleteWorkout(
      name = item.name,
      onConfirm = {
        confirmDelete = false
        onDelete()
      },
      onDismiss = { confirmDelete = false },
    )
  }
}

@Composable
internal fun ConfirmDeleteWorkout(name: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(stringResource(R.string.workout_delete_title)) },
    text = { Text(stringResource(R.string.workout_delete_text, name)) },
    confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.action_delete)) } },
    dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
  )
}

/** Name, type and (when [initialNotes] is not null) notes of a workout. */
@Composable
internal fun WorkoutInfoDialog(
  title: String,
  initialName: String,
  initialType: String,
  initialNotes: String?,
  onDismiss: () -> Unit,
  onConfirm: (name: String, type: String, notes: String) -> Unit,
) {
  var name by remember { mutableStateOf(initialName) }
  var type by remember { mutableStateOf(initialType) }
  var notes by remember { mutableStateOf(initialNotes.orEmpty()) }
  var nameError by remember { mutableStateOf(false) }
  val caps = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(title) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
          value = name,
          onValueChange = {
            name = it
            nameError = false
          },
          label = { Text(stringResource(R.string.field_name)) },
          placeholder = { Text(stringResource(R.string.workout_name_hint)) },
          isError = nameError,
          supportingText = { if (nameError) Text(stringResource(R.string.error_name_empty)) },
          singleLine = true,
          keyboardOptions = caps,
          modifier = Modifier.fillMaxWidth().testTag("workout_name"),
        )
        OutlinedTextField(
          value = type,
          onValueChange = { type = it },
          label = { Text(stringResource(R.string.field_type)) },
          placeholder = { Text(stringResource(R.string.workout_type_hint)) },
          singleLine = true,
          keyboardOptions = caps,
          modifier = Modifier.fillMaxWidth().testTag("workout_type"),
        )
        if (initialNotes != null) {
          OutlinedTextField(
            value = notes,
            onValueChange = { notes = it },
            label = { Text(stringResource(R.string.field_notes)) },
            minLines = 3,
            keyboardOptions = caps,
            modifier = Modifier.fillMaxWidth(),
          )
        }
      }
    },
    confirmButton = {
      TextButton(
        onClick = { if (name.isBlank()) nameError = true else onConfirm(name, type, notes) },
        modifier = Modifier.testTag("confirm_workout"),
      ) {
        Text(stringResource(R.string.action_save))
      }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
  )
}
