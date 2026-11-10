package com.openweight.app.workout.blocks

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.openweight.app.R
import com.openweight.app.core.database.entity.MAX_WORKOUTS_PER_BLOCK
import com.openweight.app.core.ui.ScreenScaffold
import com.openweight.app.core.ui.formatDateRange
import com.openweight.app.workout.data.WorkoutRepository
import com.openweight.app.workout.data.WorkoutSummary
import com.openweight.app.transfer.ExportRequest
import com.openweight.app.transfer.ui.ExportDialog
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface BlockEditState {
  data object Loading : BlockEditState

  data object Gone : BlockEditState

  data class Loaded(
    val block: BlockDetail,
    /** Library workouts not yet in the block, for the "Add workouts" dialog. */
    val available: List<WorkoutSummary>,
    /** A workout waiting for confirmation before removal, with the days it is scheduled on. */
    val confirmRemove: Pair<BlockWorkout, Int>? = null,
  ) : BlockEditState
}

@HiltViewModel(assistedFactory = BlockEditViewModel.Factory::class)
class BlockEditViewModel
@AssistedInject
constructor(
  private val repository: TrainingBlockRepository,
  workouts: WorkoutRepository,
  @Assisted private val blockId: String,
) : ViewModel() {
  @AssistedFactory
  interface Factory {
    fun create(blockId: String): BlockEditViewModel
  }

  private val confirmRemove = MutableStateFlow<Pair<BlockWorkout, Int>?>(null)

  val state: StateFlow<BlockEditState> =
    combine(repository.block(blockId), workouts.workouts(), confirmRemove) { block, all, pending ->
        if (block == null) BlockEditState.Gone
        else
          BlockEditState.Loaded(
            block = block,
            available = all.filter { w -> block.workouts.none { it.id == w.id } },
            confirmRemove = pending,
          )
      }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BlockEditState.Loading)

  fun updateInfo(name: String, start: LocalDate, end: LocalDate, notes: String) =
    launch { repository.updateBlockInfo(blockId, name, start, end, notes) }

  fun addWorkouts(ids: List<String>) = launch { repository.addWorkouts(blockId, ids) }

  /** Asks for confirmation first when the workout is on the schedule. */
  fun requestRemove(workout: BlockWorkout) = launch {
    val days = repository.scheduledDayCount(blockId, workout.id)
    if (days == 0) repository.removeWorkout(blockId, workout.id) else confirmRemove.value = workout to days
  }

  fun confirmRemove() = launch {
    confirmRemove.value?.let { (w, _) -> repository.removeWorkout(blockId, w.id) }
    confirmRemove.value = null
  }

  fun cancelRemove() {
    confirmRemove.value = null
  }

  fun setDay(day: DayOfWeek, workoutId: String?) = launch { repository.setDay(blockId, day, workoutId) }

  fun delete() = launch { repository.deleteBlock(blockId) }

  private fun launch(block: suspend () -> Unit) {
    viewModelScope.launch { block() }
  }
}

@Composable
fun BlockEditRoute(
  blockId: String,
  onBack: () -> Unit,
  onOpenWorkout: (String) -> Unit,
  viewModel: BlockEditViewModel =
    hiltViewModel<BlockEditViewModel, BlockEditViewModel.Factory>(key = "block-$blockId") { it.create(blockId) },
) {
  val state by viewModel.state.collectAsStateWithLifecycle()
  LaunchedEffect(state) { if (state is BlockEditState.Gone) onBack() }
  val loaded = state as? BlockEditState.Loaded
  var menu by remember { mutableStateOf(false) }
  var exporting by remember { mutableStateOf(false) }
  var editingInfo by remember { mutableStateOf(false) }
  var confirmDelete by remember { mutableStateOf(false) }
  var adding by remember { mutableStateOf(false) }

  ScreenScaffold(
    title = loaded?.block?.name.orEmpty(),
    onBack = onBack,
    actions = {
      if (loaded != null) {
        Box {
          IconButton(onClick = { menu = true }, modifier = Modifier.testTag("block_menu")) {
            Icon(Icons.Filled.MoreVert, stringResource(R.string.action_more))
          }
          DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(
              text = { Text(stringResource(R.string.block_edit_details)) },
              onClick = {
                menu = false
                editingInfo = true
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
          if (exporting) ExportDialog(ExportRequest.Items(blockIds = setOf(blockId)), onDismiss = { exporting = false })
        }
      }
    },
  ) { padding ->
    if (loaded != null) {
      BlockContent(
        block = loaded.block,
        padding = padding,
        onEditInfo = { editingInfo = true },
        onAddWorkouts = { adding = true },
        onRemoveWorkout = viewModel::requestRemove,
        onOpenWorkout = onOpenWorkout,
        onSetDay = viewModel::setDay,
      )
    }
  }

  if (loaded == null) return
  val block = loaded.block

  if (editingInfo) {
    BlockInfoDialog(
      title = stringResource(R.string.block_edit_details),
      initialName = block.name,
      initialStart = block.startDate,
      initialEnd = block.endDate,
      initialNotes = block.notes,
      onDismiss = { editingInfo = false },
      onConfirm = { name, s, e, notes ->
        editingInfo = false
        viewModel.updateInfo(name, s, e, notes)
      },
    )
  }
  if (adding) {
    AddWorkoutsDialog(
      available = loaded.available,
      capacity = MAX_WORKOUTS_PER_BLOCK - block.workouts.size,
      onDismiss = { adding = false },
      onAdd = {
        adding = false
        viewModel.addWorkouts(it)
      },
    )
  }
  loaded.confirmRemove?.let { (workout, days) ->
    AlertDialog(
      onDismissRequest = viewModel::cancelRemove,
      title = { Text(stringResource(R.string.block_remove_workout_title)) },
      text = { Text(pluralStringResource(R.plurals.block_remove_workout_text, days, workout.name, days)) },
      confirmButton = {
        TextButton(onClick = viewModel::confirmRemove, modifier = Modifier.testTag("confirm_remove")) {
          Text(stringResource(R.string.entry_remove))
        }
      },
      dismissButton = { TextButton(onClick = viewModel::cancelRemove) { Text(stringResource(R.string.action_cancel)) } },
    )
  }
  if (confirmDelete) {
    AlertDialog(
      onDismissRequest = { confirmDelete = false },
      title = { Text(stringResource(R.string.block_delete_title)) },
      text = { Text(stringResource(R.string.block_delete_text, block.name)) },
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
      dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.action_cancel)) } },
    )
  }
}

@Composable
private fun BlockContent(
  block: BlockDetail,
  padding: PaddingValues,
  onEditInfo: () -> Unit,
  onAddWorkouts: () -> Unit,
  onRemoveWorkout: (BlockWorkout) -> Unit,
  onOpenWorkout: (String) -> Unit,
  onSetDay: (DayOfWeek, String?) -> Unit,
) {
  LazyColumn(
    modifier = Modifier.fillMaxSize().padding(padding).testTag("block_content"),
    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    item {
      var notesExpanded by rememberSaveable { mutableStateOf(false) }
      var notesCutOff by remember { mutableStateOf(false) }
      Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
          formatDateRange(block.startDate, block.endDate) + " · " +
            pluralStringResource(R.plurals.block_weeks, block.totalWeeks, block.totalWeeks),
          style = MaterialTheme.typography.titleMedium,
          modifier = Modifier.clickable(onClick = onEditInfo).testTag("block_dates"),
        )
        if (block.notes.isNotBlank()) {
          // Tap to read the full notes (RIR targets, deloads, cardio…).
          Text(
            block.notes,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = if (notesExpanded) Int.MAX_VALUE else 4,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { if (!notesExpanded) notesCutOff = it.hasVisualOverflow },
            modifier = Modifier.clickable { notesExpanded = !notesExpanded }.testTag("block_notes"),
          )
          if (!notesExpanded && notesCutOff) {
            Text(
              stringResource(R.string.block_notes_more),
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.primary,
              modifier = Modifier.clickable { notesExpanded = true },
            )
          }
        }
      }
    }
    if (block.overlapsWith.isNotEmpty()) {
      item {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
          Text(
            stringResource(R.string.block_overlap_warning, block.overlapsWith.joinToString(", ")),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.padding(12.dp).testTag("overlap_warning"),
          )
        }
      }
    }

    item {
      Row(verticalAlignment = Alignment.CenterVertically) {
        SectionTitle(
          stringResource(R.string.block_workouts_count, block.workouts.size, MAX_WORKOUTS_PER_BLOCK),
          Modifier.weight(1f),
        )
        TextButton(
          onClick = onAddWorkouts,
          enabled = block.workouts.size < MAX_WORKOUTS_PER_BLOCK,
          modifier = Modifier.testTag("add_block_workouts"),
        ) {
          Icon(Icons.Filled.Add, contentDescription = null)
          Text(stringResource(R.string.action_add))
        }
      }
    }
    if (block.workouts.isEmpty()) {
      item {
        Text(
          stringResource(R.string.block_no_workouts),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
    items(block.workouts, key = { "w-${it.id}" }) { w ->
      ListItem(
        modifier = Modifier.clickable { onOpenWorkout(w.id) },
        headlineContent = { Text(w.name) },
        supportingContent = { if (w.type.isNotBlank()) Text(w.type) },
        trailingContent = {
          IconButton(onClick = { onRemoveWorkout(w) }, modifier = Modifier.testTag("remove_${w.name}")) {
            Icon(Icons.Filled.Close, stringResource(R.string.entry_remove))
          }
        },
      )
      HorizontalDivider()
    }

    item {
      SectionTitle(stringResource(R.string.block_schedule))
      Text(
        stringResource(R.string.block_schedule_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    items(DayOfWeek.entries, key = { "day-${it.name}" }) { day ->
      DayRow(day, block.schedule[day], block.workouts, onPick = { onSetDay(day, it) })
    }
  }
}

@Composable
private fun SectionTitle(text: String, modifier: Modifier = Modifier) {
  Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier)
}

@Composable
private fun DayRow(day: DayOfWeek, workoutId: String?, workouts: List<BlockWorkout>, onPick: (String?) -> Unit) {
  var open by remember { mutableStateOf(false) }
  val current = workouts.firstOrNull { it.id == workoutId }
  Row(verticalAlignment = Alignment.CenterVertically) {
    Text(
      day.getDisplayName(TextStyle.SHORT, LocalConfiguration.current.locales[0]),
      style = MaterialTheme.typography.bodyLarge,
      modifier = Modifier.width(56.dp),
    )
    Box(Modifier.weight(1f)) {
      OutlinedButton(
        onClick = { open = true },
        modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp).testTag("day_${day.name}"),
      ) {
        Text(
          current?.name ?: stringResource(R.string.workout_rest),
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.weight(1f),
          color = if (current == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
        )
        Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
      }
      DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
        DropdownMenuItem(
          text = { Text(stringResource(R.string.workout_rest)) },
          onClick = {
            open = false
            onPick(null)
          },
        )
        workouts.forEach { w ->
          DropdownMenuItem(
            text = { Text(w.name) },
            onClick = {
              open = false
              onPick(w.id)
            },
            modifier = Modifier.testTag("choose_${w.name}"),
          )
        }
        if (workouts.isEmpty()) {
          DropdownMenuItem(text = { Text(stringResource(R.string.block_add_workouts_first)) }, onClick = {}, enabled = false)
        }
      }
    }
  }
}

@Composable
private fun AddWorkoutsDialog(
  available: List<WorkoutSummary>,
  capacity: Int,
  onDismiss: () -> Unit,
  onAdd: (List<String>) -> Unit,
) {
  val chosen = remember { mutableStateListOf<String>() }
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(pluralStringResource(R.plurals.block_add_workouts_title, capacity, capacity)) },
    text = {
      if (available.isEmpty()) {
        Text(stringResource(R.string.block_no_more_workouts))
      } else {
        LazyColumn {
          items(available, key = { it.id }) { w ->
            val checked = w.id in chosen
            val enabled = checked || chosen.size < capacity
            Row(
              verticalAlignment = Alignment.CenterVertically,
              modifier =
                Modifier.fillMaxWidth()
                  .clickable(enabled = enabled) { if (checked) chosen.remove(w.id) else chosen.add(w.id) }
                  .testTag("pick_${w.name}"),
            ) {
              Checkbox(checked = checked, onCheckedChange = null, enabled = enabled)
              Column(Modifier.padding(start = 8.dp, top = 6.dp, bottom = 6.dp)) {
                Text(w.name)
                if (w.type.isNotBlank()) {
                  Text(w.type, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
              }
            }
          }
        }
      }
    },
    confirmButton = {
      TextButton(
        onClick = { onAdd(chosen.toList()) },
        enabled = chosen.isNotEmpty(),
        modifier = Modifier.testTag("confirm_add_workouts"),
      ) {
        Text(stringResource(R.string.action_add))
      }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
  )
}
