package com.openweight.app.transfer.ui

import android.content.ActivityNotFoundException
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.openweight.app.R
import com.openweight.app.core.TimeAndIds
import com.openweight.app.core.ui.DateRangeDialog
import com.openweight.app.core.ui.ScreenScaffold
import com.openweight.app.core.ui.formatDateRange
import com.openweight.app.diet.data.DietRepository
import com.openweight.app.diet.data.MealPlanRepository
import com.openweight.app.home.WeekPlan
import com.openweight.app.transfer.ExportRequest
import com.openweight.app.transfer.ExportService
import com.openweight.app.transfer.ExportedFile
import com.openweight.app.transfer.ImportSession
import com.openweight.app.transfer.TransferFiles
import com.openweight.app.workout.blocks.TrainingBlockRepository
import com.openweight.app.workout.data.WorkoutRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.IOException
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface ExportStep {
  data object Building : ExportStep

  data class Ready(val file: ExportedFile) : ExportStep

  data class Saved(val fileName: String) : ExportStep

  data object Failed : ExportStep
}

@HiltViewModel
class ExportViewModel
@Inject
constructor(private val exporter: ExportService, private val files: TransferFiles, private val session: ImportSession) : ViewModel() {
  private val _step = MutableStateFlow<ExportStep>(ExportStep.Building)
  val step: StateFlow<ExportStep> = _step.asStateFlow()

  fun prepare(request: ExportRequest) {
    _step.value = ExportStep.Building
    viewModelScope.launch {
      _step.value =
        try {
          val settings = if (request == ExportRequest.Everything) session.currentSettings() else null
          ExportStep.Ready(exporter.export(request, settings))
        } catch (e: IllegalArgumentException) {
          ExportStep.Failed
        }
    }
  }

  fun save(uri: Uri) {
    val ready = _step.value as? ExportStep.Ready ?: return
    viewModelScope.launch {
      _step.value =
        try {
          files.write(uri, ready.file.bytes)
          ExportStep.Saved(ready.file.fileName)
        } catch (e: IOException) {
          ExportStep.Failed
        } catch (e: SecurityException) {
          ExportStep.Failed
        }
    }
  }

  suspend fun shareIntent(chooserTitle: String) = (_step.value as? ExportStep.Ready)?.let { files.shareIntent(it.file, chooserTitle) }
}

/**
 * Builds the export for [request], then offers "Save to a file" (the Android file picker) and
 * "Share" (any app). Used by Settings and by the Export action on blocks, workouts, recipes and
 * diet plans.
 */
@Composable
fun ExportDialog(request: ExportRequest, onDismiss: () -> Unit, viewModel: ExportViewModel = hiltViewModel(key = "export")) {
  val step by viewModel.step.collectAsStateWithLifecycle()
  val context = LocalContext.current
  val resources = LocalResources.current
  val scope = rememberCoroutineScope()
  LaunchedEffect(request) { viewModel.prepare(request) }
  val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> uri?.let(viewModel::save) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(stringResource(if (step is ExportStep.Saved) R.string.export_saved_title else R.string.export_title)) },
    text = {
      when (val s = step) {
        ExportStep.Building -> LinearProgressIndicator(Modifier.fillMaxWidth())
        is ExportStep.Ready -> Text(stringResource(R.string.export_ready, s.file.fileName), modifier = Modifier.testTag("export_file_name"))
        is ExportStep.Saved -> Text(stringResource(R.string.export_saved, s.fileName))
        ExportStep.Failed -> Text(stringResource(R.string.export_failed))
      }
    },
    confirmButton = {
      when (val s = step) {
        is ExportStep.Ready ->
          Row {
            TextButton(
              onClick = {
                scope.launch {
                  val intent = viewModel.shareIntent(resources.getString(R.string.export_share_title)) ?: return@launch
                  try {
                    context.startActivity(intent)
                    onDismiss()
                  } catch (e: ActivityNotFoundException) {
                    // No app can receive it; saving still works.
                  }
                }
              },
              modifier = Modifier.testTag("export_share"),
            ) {
              Text(stringResource(R.string.export_share))
            }
            TextButton(onClick = { save.launch(s.file.fileName) }, modifier = Modifier.testTag("export_save")) { Text(stringResource(R.string.export_save)) }
          }
        is ExportStep.Saved, ExportStep.Failed -> TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_ok)) }
        ExportStep.Building -> Unit
      }
    },
    dismissButton = {
      if (step is ExportStep.Ready || step is ExportStep.Building) TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
    },
  )
}

/** What the Export screen offers. */
enum class ExportChoice {
  WORKOUT,
  DIET,
  PROGRESS,
  RANGE,
  ITEMS,
}

data class PickableItem(val id: String, val name: String)

data class ExportPickerState(
  val blocks: List<PickableItem> = emptyList(),
  val workouts: List<PickableItem> = emptyList(),
  val recipes: List<PickableItem> = emptyList(),
  val plans: List<PickableItem> = emptyList(),
)

@HiltViewModel
class ExportPickerViewModel
@Inject
constructor(
  workouts: WorkoutRepository,
  blocks: TrainingBlockRepository,
  diet: DietRepository,
  meals: MealPlanRepository,
  clock: TimeAndIds,
  private val saved: SavedStateHandle,
) : ViewModel() {
  val thisMonday: LocalDate = WeekPlan.mondayOf(clock.today())

  val state: StateFlow<ExportPickerState> =
    combine(blocks.blocks(), workouts.workouts(), diet.recipes(), meals.plans()) { b, w, r, p ->
        ExportPickerState(
          b.map { PickableItem(it.id, it.name) },
          w.map { PickableItem(it.id, it.name) },
          r.map { PickableItem(it.id, it.name) },
          p.map { PickableItem(it.id, it.name) },
        )
      }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExportPickerState())

  val choice = saved.getStateFlow(KEY_CHOICE, ExportChoice.WORKOUT.name)
  val picked = saved.getStateFlow(KEY_PICKED, emptyList<String>())
  val range = saved.getStateFlow(KEY_RANGE, listOf(thisMonday.toEpochDay(), thisMonday.plusDays(6).toEpochDay()))

  fun choose(c: ExportChoice) {
    saved[KEY_CHOICE] = c.name
  }

  /** Picked items are "kind:id", so one id can't stand for two kinds. */
  fun toggle(key: String) {
    saved[KEY_PICKED] = picked.value.let { if (key in it) it - key else it + key }
  }

  fun setRange(from: LocalDate, to: LocalDate) {
    saved[KEY_RANGE] = listOf(from.toEpochDay(), to.toEpochDay())
  }

  companion object {
    /** What [choice] exports; null when it needs picked items and there are none. */
    fun request(choice: ExportChoice, picked: List<String>, from: LocalDate, to: LocalDate): ExportRequest? {
      fun ids(kind: String) = picked.filter { it.startsWith("$kind:") }.map { it.substringAfter(':') }.toSet()
      return when (choice) {
        ExportChoice.WORKOUT -> ExportRequest.WorkoutData
        ExportChoice.DIET -> ExportRequest.DietData
        ExportChoice.PROGRESS -> ExportRequest.Progress
        ExportChoice.RANGE -> ExportRequest.Range(from, to)
        ExportChoice.ITEMS -> ExportRequest.Items(ids(BLOCK), ids(WORKOUT), ids(RECIPE), ids(PLAN)).takeUnless { it.isEmpty }
      }
    }

    const val BLOCK = "block"
    const val WORKOUT = "workout"
    const val RECIPE = "recipe"
    const val PLAN = "plan"
    private const val KEY_CHOICE = "export_choice"
    private const val KEY_PICKED = "export_picked"
    private const val KEY_RANGE = "export_range"
  }
}

/** Settings → Export: all workout data, all diet data, calendar dates, or picked items. */
@Composable
fun ExportRoute(onBack: () -> Unit, viewModel: ExportPickerViewModel = hiltViewModel()) {
  val state by viewModel.state.collectAsStateWithLifecycle()
  val choiceName by viewModel.choice.collectAsStateWithLifecycle()
  val picked by viewModel.picked.collectAsStateWithLifecycle()
  val range by viewModel.range.collectAsStateWithLifecycle()
  val choice = ExportChoice.valueOf(choiceName)
  var exporting by remember { mutableStateOf<ExportRequest?>(null) }
  var choosingDates by rememberSaveable { mutableStateOf(false) }
  val from = LocalDate.ofEpochDay(range[0])
  val to = LocalDate.ofEpochDay(range[1])
  val request = ExportPickerViewModel.request(choice, picked, from, to)

  ScreenScaffold(title = stringResource(R.string.export_screen_title), onBack = onBack) { padding ->
    Column(Modifier.fillMaxSize().padding(padding)) {
      LazyColumn(Modifier.weight(1f).testTag("export_list"), contentPadding = PaddingValues(bottom = 16.dp)) {
        item { Choice(ExportChoice.WORKOUT, choice, R.string.export_workout, R.string.export_workout_hint, viewModel::choose) }
        item { Choice(ExportChoice.DIET, choice, R.string.export_diet, R.string.export_diet_hint, viewModel::choose) }
        item { Choice(ExportChoice.PROGRESS, choice, R.string.export_progress, R.string.export_progress_hint, viewModel::choose) }
        item { Choice(ExportChoice.RANGE, choice, R.string.export_range, R.string.export_range_hint, viewModel::choose) }
        if (choice == ExportChoice.RANGE) {
          item {
            OutlinedButton(onClick = { choosingDates = true }, modifier = Modifier.padding(start = 72.dp, bottom = 8.dp).testTag("export_dates")) {
              Text(formatDateRange(from, to))
            }
          }
        }
        item { Choice(ExportChoice.ITEMS, choice, R.string.export_items, R.string.export_items_hint, viewModel::choose) }
        if (choice == ExportChoice.ITEMS) {
          section(R.string.export_section_blocks, ExportPickerViewModel.BLOCK, state.blocks, picked, viewModel::toggle)
          section(R.string.export_section_workouts, ExportPickerViewModel.WORKOUT, state.workouts, picked, viewModel::toggle)
          section(R.string.export_section_recipes, ExportPickerViewModel.RECIPE, state.recipes, picked, viewModel::toggle)
          section(R.string.export_section_plans, ExportPickerViewModel.PLAN, state.plans, picked, viewModel::toggle)
        }
      }
      Surface(tonalElevation = 3.dp) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
          if (choice == ExportChoice.ITEMS && request == null) {
            Text(stringResource(R.string.export_pick_something), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
          } else if (choice == ExportChoice.ITEMS) {
            Text(stringResource(R.string.export_includes_needed), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
          Button(onClick = { exporting = request }, enabled = request != null, modifier = Modifier.fillMaxWidth().testTag("export_go")) {
            Text(stringResource(R.string.export_go))
          }
        }
      }
    }
  }

  exporting?.let { ExportDialog(it, onDismiss = { exporting = null }) }
  if (choosingDates) {
    DateRangeDialog(
      title = stringResource(R.string.export_range),
      confirmLabel = stringResource(R.string.action_ok),
      initialFrom = from,
      initialTo = to,
      onDismiss = { choosingDates = false },
      onConfirm = { f, t ->
        choosingDates = false
        viewModel.setRange(f, t)
      },
    )
  }
}

@Composable
private fun Choice(value: ExportChoice, selected: ExportChoice, title: Int, hint: Int, onChoose: (ExportChoice) -> Unit) {
  ListItem(
    modifier = Modifier.selectable(selected = value == selected, role = Role.RadioButton, onClick = { onChoose(value) }).testTag("export_option_${value.name}"),
    leadingContent = { RadioButton(selected = value == selected, onClick = null) },
    headlineContent = { Text(stringResource(title)) },
    supportingContent = { Text(stringResource(hint)) },
  )
}

private fun androidx.compose.foundation.lazy.LazyListScope.section(
  title: Int,
  kind: String,
  items: List<PickableItem>,
  picked: List<String>,
  onToggle: (String) -> Unit,
) {
  if (items.isEmpty()) return
  item(key = "title-$kind") {
    HorizontalDivider()
    Text(
      stringResource(title),
      style = MaterialTheme.typography.titleSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp),
    )
  }
  items(items, key = { "$kind:${it.id}" }) { item ->
    val key = "$kind:${item.id}"
    Row(
      Modifier.fillMaxWidth()
        .toggleable(value = key in picked, role = Role.Checkbox, onValueChange = { onToggle(key) })
        .padding(horizontal = 16.dp, vertical = 4.dp)
        .testTag("export_item_${item.name}"),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Checkbox(checked = key in picked, onCheckedChange = null)
      Text(item.name, Modifier.padding(start = 16.dp))
    }
  }
}
