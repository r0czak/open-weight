package com.openweight.app.workout.workouts

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.openweight.app.R
import com.openweight.app.core.model.WeightUnit
import com.openweight.app.core.model.format
import com.openweight.app.core.model.formatReps
import com.openweight.app.core.model.formatRest
import com.openweight.app.core.ui.EmptyState
import com.openweight.app.core.ui.ScreenScaffold
import com.openweight.app.transfer.ExportRequest
import com.openweight.app.transfer.ui.ExportDialog
import com.openweight.app.workout.data.PlannedSet
import com.openweight.app.workout.data.WorkoutDetail
import com.openweight.app.workout.data.WorkoutEntry

@Composable
fun WorkoutEditRoute(
  workoutId: String,
  onBack: () -> Unit,
  onOpenWorkout: (String) -> Unit,
  onAddExercise: () -> Unit,
  viewModel: WorkoutEditViewModel =
    hiltViewModel<WorkoutEditViewModel, WorkoutEditViewModel.Factory>(key = "workout-$workoutId") {
      it.create(workoutId)
    },
) {
  val state by viewModel.state.collectAsStateWithLifecycle()
  val snackbar = remember { SnackbarHostState() }
  val resources = LocalResources.current

  LaunchedEffect(viewModel) {
    viewModel.events.collect { event ->
      when (event) {
        is WorkoutEditEvent.Opened -> onOpenWorkout(event.workoutId)
        WorkoutEditEvent.Deleted -> onBack()
        is WorkoutEditEvent.InUse ->
          snackbar.showSnackbar(
            resources.getQuantityString(R.plurals.workout_in_use, event.blockCount, event.blockCount)
          )
      }
    }
  }
  LaunchedEffect(state) { if (state is WorkoutEditState.Gone) onBack() }

  val loaded = state as? WorkoutEditState.Loaded
  var editingInfo by remember { mutableStateOf(false) }
  var confirmDelete by remember { mutableStateOf(false) }
  var menu by remember { mutableStateOf(false) }
  var exporting by remember { mutableStateOf(false) }

  ScreenScaffold(
    title = loaded?.workout?.name.orEmpty(),
    onBack = onBack,
    snackbarHostState = snackbar,
    actions = {
      if (loaded != null) {
        Box {
          IconButton(onClick = { menu = true }, modifier = Modifier.testTag("workout_menu")) {
            Icon(Icons.Filled.MoreVert, stringResource(R.string.action_more))
          }
          DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(
              text = { Text(stringResource(R.string.workout_edit_details)) },
              onClick = {
                menu = false
                editingInfo = true
              },
            )
            DropdownMenuItem(
              text = { Text(stringResource(R.string.action_duplicate)) },
              onClick = {
                menu = false
                viewModel.duplicate()
              },
            )
            DropdownMenuItem(text = { Text(stringResource(R.string.action_export)) }, onClick = { menu = false; exporting = true }, modifier = Modifier.testTag("export_item"))
            DropdownMenuItem(
              text = { Text(stringResource(R.string.action_delete)) },
              onClick = {
                menu = false
                confirmDelete = true
              },
            )
          }
          if (exporting) ExportDialog(ExportRequest.Items(workoutIds = setOf(workoutId)), onDismiss = { exporting = false })
        }
      }
    },
    floatingActionButton = {
      if (loaded != null) {
        val label = stringResource(R.string.workout_add_exercise)
        ExtendedFloatingActionButton(
          onClick = onAddExercise,
          icon = { Icon(Icons.Filled.Add, contentDescription = null) },
          text = { Text(label) },
          // The text sits in an expand animation that can keep it out of the accessibility tree.
          modifier = Modifier.semantics { contentDescription = label }.testTag("add_exercise_to_workout"),
        )
      }
    },
  ) { padding ->
    if (loaded != null) {
      WorkoutContent(
        workout = loaded.workout,
        unit = loaded.unit,
        padding = padding,
        viewModel = viewModel,
      )
    }
  }

  if (editingInfo && loaded != null) {
    WorkoutInfoDialog(
      title = stringResource(R.string.workout_edit_details),
      initialName = loaded.workout.name,
      initialType = loaded.workout.type,
      initialNotes = loaded.workout.notes,
      onDismiss = { editingInfo = false },
      onConfirm = { name, type, notes ->
        editingInfo = false
        viewModel.updateInfo(name, type, notes)
      },
    )
  }
  if (confirmDelete && loaded != null) {
    ConfirmDeleteWorkout(
      name = loaded.workout.name,
      onConfirm = {
        confirmDelete = false
        viewModel.delete()
      },
      onDismiss = { confirmDelete = false },
    )
  }
}

@Composable
private fun WorkoutContent(
  workout: WorkoutDetail,
  unit: WeightUnit,
  padding: PaddingValues,
  viewModel: WorkoutEditViewModel,
) {
  var editingSet by remember { mutableStateOf<Pair<WorkoutEntry, PlannedSet>?>(null) }
  var editingNotes by remember { mutableStateOf<WorkoutEntry?>(null) }
  var removing by remember { mutableStateOf<WorkoutEntry?>(null) }

  LazyColumn(
    modifier = Modifier.fillMaxSize().padding(padding),
    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    item {
      Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (workout.type.isNotBlank()) {
          Text(workout.type, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
        if (workout.blockNames.isNotEmpty()) {
          Text(
            stringResource(R.string.workout_used_in_blocks, workout.blockNames.joinToString(", ")),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        if (workout.notes.isNotBlank()) {
          Text(workout.notes, style = MaterialTheme.typography.bodyMedium)
        }
      }
    }
    if (workout.entries.isEmpty()) {
      item { EmptyState(stringResource(R.string.workout_no_exercises), hint = stringResource(R.string.workout_no_exercises_hint)) }
    }
    items(workout.entries, key = { it.id }) { entry ->
      EntryCard(
        entry = entry,
        index = workout.entries.indexOf(entry),
        count = workout.entries.size,
        unit = unit,
        onSetClick = { editingSet = entry to it },
        onAddSet = { viewModel.addSet(entry.id) },
        onMove = { delta -> viewModel.moveEntry(entry.id, delta) },
        onNotes = { editingNotes = entry },
        onRemove = { removing = entry },
      )
    }
  }

  removing?.let { entry ->
    AlertDialog(
      onDismissRequest = { removing = null },
      title = { Text(stringResource(R.string.entry_remove_title, entry.exerciseName)) },
      text = { Text(pluralStringResource(R.plurals.entry_remove_text, entry.sets.size, entry.sets.size)) },
      confirmButton = {
        TextButton(
          onClick = {
            removing = null
            viewModel.removeEntry(entry.id)
          },
          modifier = Modifier.testTag("confirm_remove_entry"),
        ) {
          Text(stringResource(R.string.entry_remove_confirm))
        }
      },
      dismissButton = { TextButton(onClick = { removing = null }) { Text(stringResource(R.string.action_cancel)) } },
    )
  }
  editingSet?.let { (entry, set) ->
    EditSetDialog(
      set = set,
      setNumber = entry.sets.indexOfFirst { it.id == set.id } + 1,
      unit = unit,
      onSave = { values, all ->
        editingSet = null
        viewModel.updateSet(set.id, values, all)
      },
      onDelete = {
        editingSet = null
        viewModel.deleteSet(set.id)
      },
      onDismiss = { editingSet = null },
    )
  }
  editingNotes?.let { entry ->
    var notes by remember(entry.id) { mutableStateOf(entry.notes) }
    AlertDialog(
      onDismissRequest = { editingNotes = null },
      title = { Text(entry.exerciseName) },
      text = {
        OutlinedTextField(
          value = notes,
          onValueChange = { notes = it },
          label = { Text(stringResource(R.string.field_notes)) },
          placeholder = { Text(stringResource(R.string.entry_notes_hint)) },
          minLines = 2,
          keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
          modifier = Modifier.fillMaxWidth(),
        )
      },
      confirmButton = {
        TextButton(
          onClick = {
            editingNotes = null
            viewModel.updateEntryNotes(entry.id, notes)
          }
        ) {
          Text(stringResource(R.string.action_save))
        }
      },
      dismissButton = {
        TextButton(onClick = { editingNotes = null }) { Text(stringResource(R.string.action_cancel)) }
      },
    )
  }
}

@Composable
private fun EntryCard(
  entry: WorkoutEntry,
  index: Int,
  count: Int,
  unit: WeightUnit,
  onSetClick: (PlannedSet) -> Unit,
  onAddSet: () -> Unit,
  onMove: (Int) -> Unit,
  onNotes: () -> Unit,
  onRemove: () -> Unit,
) {
  var menu by remember { mutableStateOf(false) }
  Card(
    modifier = Modifier.fillMaxWidth().testTag("entry_${entry.exerciseName}"),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
  ) {
    Column(Modifier.padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 8.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
          "${index + 1}. ${entry.exerciseName}",
          style = MaterialTheme.typography.titleMedium,
          modifier = Modifier.weight(1f),
        )
        Box {
          IconButton(onClick = { menu = true }, modifier = Modifier.testTag("entry_menu_$index")) {
            Icon(Icons.Filled.MoreVert, stringResource(R.string.entry_options, entry.exerciseName))
          }
          DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            if (index > 0) {
              DropdownMenuItem(text = { Text(stringResource(R.string.action_move_up)) }, onClick = { menu = false; onMove(-1) })
            }
            if (index < count - 1) {
              DropdownMenuItem(text = { Text(stringResource(R.string.action_move_down)) }, onClick = { menu = false; onMove(1) })
            }
            DropdownMenuItem(text = { Text(stringResource(R.string.entry_edit_notes)) }, onClick = { menu = false; onNotes() })
            DropdownMenuItem(text = { Text(stringResource(R.string.entry_remove)) }, onClick = { menu = false; onRemove() })
          }
        }
      }
      if (entry.notes.isNotBlank()) {
        Text(
          entry.notes,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(bottom = 4.dp),
        )
      }
      SetHeader()
      entry.sets.forEachIndexed { i, set ->
        HorizontalDivider(Modifier.padding(end = 12.dp))
        SetRow(i + 1, set, unit, onClick = { onSetClick(set) })
      }
      TextButton(onClick = onAddSet) {
        Icon(Icons.Filled.Add, contentDescription = null)
        Text(stringResource(R.string.set_add))
      }
    }
  }
}

@Composable
internal fun SetHeader() {
  Row(Modifier.fillMaxWidth().padding(end = 12.dp, top = 4.dp, bottom = 4.dp)) {
    val style = MaterialTheme.typography.labelMedium
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Text(stringResource(R.string.set_col_set), style = style, color = color, modifier = Modifier.width(40.dp))
    Text(stringResource(R.string.set_col_reps), style = style, color = color, modifier = Modifier.weight(1f))
    Text(stringResource(R.string.set_col_weight), style = style, color = color, modifier = Modifier.weight(1f))
    Text(stringResource(R.string.set_col_rest), style = style, color = color, modifier = Modifier.weight(1f))
  }
}

@Composable
internal fun SetRow(number: Int, set: PlannedSet, unit: WeightUnit, onClick: (() -> Unit)?) {
  Row(
    Modifier.fillMaxWidth()
      .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
      .padding(end = 12.dp, top = 10.dp, bottom = 10.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text("$number", fontWeight = FontWeight.SemiBold, modifier = Modifier.width(40.dp))
    Text(formatReps(set.repsMin, set.repsMax), modifier = Modifier.weight(1f))
    Text(unit.format(set.weightKg), modifier = Modifier.weight(1f))
    Text(formatRest(set.restSeconds), modifier = Modifier.weight(1f))
  }
}
