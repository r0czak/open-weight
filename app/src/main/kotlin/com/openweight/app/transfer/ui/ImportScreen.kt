package com.openweight.app.transfer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
import com.openweight.app.core.ui.EmptyState
import com.openweight.app.core.ui.ScreenScaffold
import com.openweight.app.core.ui.formatDayShort
import com.openweight.app.core.ui.formatMedium
import com.openweight.app.transfer.ConflictChoice
import com.openweight.app.transfer.ImportMatch
import com.openweight.app.transfer.ImportOptions
import com.openweight.app.transfer.ImportPreview
import com.openweight.app.transfer.ImportResult
import com.openweight.app.transfer.ImportSession
import com.openweight.app.transfer.ImportStep
import com.openweight.app.transfer.RecordKind
import com.openweight.app.transfer.ScopeKind
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.launch

@HiltViewModel
class ImportViewModel @Inject constructor(private val session: ImportSession, private val saved: SavedStateHandle) : ViewModel() {
  val step = session.step
  val choice = saved.getStateFlow(KEY_CHOICE, ConflictChoice.SKIP.name)
  val replaceEverything = saved.getStateFlow(KEY_REPLACE, false)
  /** "kind:id=CHOICE" per overridden match. */
  val overrides = saved.getStateFlow(KEY_OVERRIDES, emptyList<String>())

  fun choose(c: ConflictChoice) {
    saved[KEY_CHOICE] = c.name
  }

  fun setReplaceEverything(value: Boolean) {
    saved[KEY_REPLACE] = value
  }

  /** [c] null goes back to the choice for all matches. */
  fun override(match: ImportMatch, c: ConflictChoice?) {
    val rest = overrides.value.filterNot { it.startsWith(match.key + "=") }
    saved[KEY_OVERRIDES] = if (c == null) rest else rest + "${match.key}=${c.name}"
  }

  fun run(copySuffix: String) {
    val ready = step.value as? ImportStep.Ready ?: return
    val options =
      ImportOptions(
        choice = ConflictChoice.valueOf(choice.value),
        overrides = overrides.value.associate { it.substringBeforeLast('=') to ConflictChoice.valueOf(it.substringAfterLast('=')) },
        replaceEverything = ready.isUndo || (ready.preview.isFull && replaceEverything.value),
        copySuffix = copySuffix,
      )
    viewModelScope.launch { session.run(options) }
  }

  fun finish() = session.clear()

  private companion object {
    const val KEY_CHOICE = "import_choice"
    const val KEY_REPLACE = "import_replace"
    const val KEY_OVERRIDES = "import_overrides"
  }
}

/** Shows what a file holds and what is already in the app, then imports it the way the user picks. */
@Composable
fun ImportRoute(onBack: () -> Unit, viewModel: ImportViewModel = hiltViewModel()) {
  val step by viewModel.step.collectAsStateWithLifecycle()
  val done: () -> Unit = {
    viewModel.finish()
    onBack()
  }
  ScreenScaffold(title = stringResource(R.string.import_title), onBack = done) { padding ->
    Box(Modifier.fillMaxSize().padding(padding)) {
      when (val s = step) {
        null -> EmptyState(stringResource(R.string.import_nothing_open))
        ImportStep.Reading, ImportStep.Running ->
          Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(if (s == ImportStep.Reading) R.string.import_reading else R.string.import_running))
            LinearProgressIndicator(Modifier.fillMaxWidth())
          }
        is ImportStep.Failed -> Unit
        is ImportStep.Ready -> PreviewContent(s, viewModel)
        is ImportStep.Done -> ResultContent(s.result, s.replacedEverything, onDone = done)
      }
    }
  }
  (step as? ImportStep.Failed)?.let { s ->
    AlertDialog(
      onDismissRequest = done,
      title = { Text(stringResource(R.string.import_failed_title)) },
      text = { Text((s.message ?: stringResource(R.string.import_failed_read)) + "\n\n" + stringResource(R.string.import_nothing_changed)) },
      confirmButton = { TextButton(onClick = done) { Text(stringResource(R.string.action_ok)) } },
    )
  }
}

@Composable
private fun PreviewContent(ready: ImportStep.Ready, viewModel: ImportViewModel) {
  val preview = ready.preview
  val choice = ConflictChoice.valueOf(viewModel.choice.collectAsStateWithLifecycle().value)
  val replaceChosen by viewModel.replaceEverything.collectAsStateWithLifecycle()
  val overrides by viewModel.overrides.collectAsStateWithLifecycle()
  val replaceEverything = ready.isUndo || (preview.isFull && replaceChosen)
  var confirmReplace by remember { mutableStateOf(false) }
  val resources = LocalResources.current

  Column(Modifier.fillMaxSize()) {
    LazyColumn(Modifier.weight(1f).testTag("import_preview"), contentPadding = PaddingValues(bottom = 16.dp)) {
      item { FileHeader(preview, ready.isUndo) }
      item { SectionTitle(stringResource(R.string.import_in_file)) }
      items(preview.counts.entries.toList(), key = { it.key.name }) { (kind, count) ->
        ListItem(
          modifier = Modifier.testTag("import_count_${kind.name}"),
          headlineContent = { Text(kindLabel(kind)) },
          supportingContent = {
            Text(
              if (count.alreadyHere == 0) stringResource(R.string.import_count_all_new, count.inFile)
              else stringResource(R.string.import_count, count.new, count.alreadyHere)
            )
          },
        )
      }
      if (preview.hasSettings) {
        item { ListItem(headlineContent = { Text(stringResource(R.string.import_settings)) }, supportingContent = { Text(stringResource(R.string.import_settings_hint)) }) }
      }
      if (preview.isFull && !ready.isUndo) {
        item {
          HorizontalDivider(Modifier.padding(top = 8.dp))
          SectionTitle(stringResource(R.string.import_how))
          Radio(!replaceChosen, R.string.import_merge, R.string.import_merge_hint, "import_merge") { viewModel.setReplaceEverything(false) }
          Radio(replaceChosen, R.string.import_replace_all, R.string.import_replace_all_hint, "import_replace_all") { viewModel.setReplaceEverything(true) }
        }
      }
      if (preview.matches.isNotEmpty() && !replaceEverything) {
        item {
          HorizontalDivider(Modifier.padding(top = 8.dp))
          SectionTitle(stringResource(R.string.import_already_here, preview.matches.size))
          ConflictChoice.entries.forEach { c ->
            Radio(choice == c, choiceLabel(c), choiceHint(c), "import_choice_${c.name}") { viewModel.choose(c) }
          }
          Text(
            stringResource(R.string.import_per_item_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
          )
        }
        items(preview.matches, key = { it.key }) { match ->
          val override = overrides.firstOrNull { it.startsWith(match.key + "=") }?.substringAfterLast('=')?.let(ConflictChoice::valueOf)
          MatchRow(match, override, onOverride = { viewModel.override(match, it) })
        }
      }
    }
    Surface(tonalElevation = 3.dp) {
      Button(
        onClick = { if (replaceEverything) confirmReplace = true else viewModel.run(resources.getString(R.string.import_copy_suffix)) },
        colors = if (replaceEverything) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error) else ButtonDefaults.buttonColors(),
        enabled = !preview.isEmpty,
        modifier = Modifier.fillMaxWidth().padding(16.dp).testTag("import_go"),
      ) {
        Text(stringResource(if (ready.isUndo) R.string.import_undo_go else if (replaceEverything) R.string.import_replace_all else R.string.import_go))
      }
    }
  }

  if (confirmReplace) {
    AlertDialog(
      onDismissRequest = { confirmReplace = false },
      title = { Text(stringResource(R.string.import_replace_confirm_title)) },
      text = { Text(stringResource(R.string.import_replace_confirm)) },
      confirmButton = {
        TextButton(
          onClick = {
            confirmReplace = false
            viewModel.run(resources.getString(R.string.import_copy_suffix))
          },
          modifier = Modifier.testTag("import_replace_confirm"),
        ) {
          Text(stringResource(R.string.import_replace_all))
        }
      },
      dismissButton = { TextButton(onClick = { confirmReplace = false }) { Text(stringResource(R.string.action_cancel)) } },
    )
  }
}

@Composable
private fun FileHeader(preview: ImportPreview, isUndo: Boolean) {
  Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
    Text(
      stringResource(
        when {
          isUndo -> R.string.import_scope_undo
          else ->
            when (preview.scope) {
              ScopeKind.FULL -> R.string.import_scope_full
              ScopeKind.WORKOUT -> R.string.import_scope_workout
              ScopeKind.DIET -> R.string.import_scope_diet
              ScopeKind.RANGE -> R.string.import_scope_range
              ScopeKind.PROGRESS -> R.string.import_scope_progress
              ScopeKind.ITEMS, null -> R.string.import_scope_items
            }
        }
      ),
      style = MaterialTheme.typography.titleLarge,
      modifier = Modifier.testTag("import_scope"),
    )
    if (!isUndo && preview.scope in setOf(ScopeKind.ITEMS, ScopeKind.RANGE, ScopeKind.PROGRESS, null)) {
      preview.description?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
    }
    preview.exportedAt?.let {
      Text(
        stringResource(if (isUndo) R.string.import_saved_on else R.string.import_exported_on, it.atZone(ZoneId.systemDefault()).toLocalDate().formatMedium()),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    if (isUndo) Text(stringResource(R.string.import_undo_hint), style = MaterialTheme.typography.bodyMedium)
  }
}

@Composable
private fun MatchRow(match: ImportMatch, override: ConflictChoice?, onOverride: (ConflictChoice?) -> Unit) {
  var menu by remember { mutableStateOf(false) }
  val notes =
    listOfNotNull(
      kindLabel(match.kind),
      match.date?.formatDayShort(),
      if (match.byName) stringResource(R.string.import_same_name) else null,
      when (match.fileIsNewer) {
        true -> stringResource(R.string.import_newer_in_file)
        false -> stringResource(R.string.import_newer_in_app)
        null -> null
      },
    )
  ListItem(
    modifier = Modifier.testTag("import_match_${match.name}"),
    headlineContent = { Text(match.name.ifBlank { kindLabel(match.kind) }) },
    supportingContent = { Text(notes.joinToString(" · ")) },
    trailingContent = {
      Box {
        TextButton(onClick = { menu = true }, modifier = Modifier.testTag("import_override_${match.name}")) {
          Text(override?.let { choiceLabel(it) }?.let { stringResource(it) } ?: stringResource(R.string.import_as_above))
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
          DropdownMenuItem(text = { Text(stringResource(R.string.import_as_above)) }, onClick = { menu = false; onOverride(null) })
          ConflictChoice.entries.forEach { c ->
            DropdownMenuItem(
              text = { Text(stringResource(choiceLabel(c))) },
              onClick = { menu = false; onOverride(c) },
              modifier = Modifier.testTag("import_override_option_${c.name}"),
            )
          }
        }
      }
    },
  )
}

@Composable
private fun ResultContent(result: ImportResult, replacedEverything: Boolean, onDone: () -> Unit) {
  val lines =
    RecordKind.entries.mapNotNull { kind ->
      val r = result[kind]
      val parts =
        listOfNotNull(
          r.added.takeIf { it > 0 }?.let { stringResource(R.string.import_result_added, it) },
          r.replaced.takeIf { it > 0 }?.let { stringResource(R.string.import_result_replaced, it) },
          r.copied.takeIf { it > 0 }?.let { stringResource(R.string.import_result_copied, it) },
          r.skipped.takeIf { it > 0 }?.let { stringResource(R.string.import_result_skipped, it) },
        )
      if (parts.isEmpty()) null else kindLabel(kind) + " · " + parts.joinToString(" · ")
    }
  Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Text(
      stringResource(if (result.nothingChanged) R.string.import_nothing_new_title else R.string.import_done_title),
      style = MaterialTheme.typography.titleLarge,
      modifier = Modifier.testTag("import_done"),
    )
    Text(lines.joinToString("\n"), modifier = Modifier.testTag("import_summary"))
    if (result.settings != null) Text(stringResource(R.string.import_settings_restored))
    if (replacedEverything) {
      Text(stringResource(R.string.import_undo_available), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Button(onClick = onDone, modifier = Modifier.padding(top = 8.dp).testTag("import_finish")) { Text(stringResource(R.string.action_done)) }
  }
}

@Composable
private fun SectionTitle(text: String) {
  Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp))
}

@Composable
private fun Radio(selected: Boolean, title: Int, hint: Int, tag: String, onClick: () -> Unit) {
  ListItem(
    modifier = Modifier.selectable(selected = selected, role = Role.RadioButton, onClick = onClick).testTag(tag),
    leadingContent = { RadioButton(selected = selected, onClick = null) },
    headlineContent = { Text(stringResource(title)) },
    supportingContent = { Text(stringResource(hint)) },
  )
}

@Composable
fun kindLabel(kind: RecordKind): String =
  stringResource(
    when (kind) {
      RecordKind.TRAINING_BLOCK -> R.string.kind_blocks
      RecordKind.WORKOUT -> R.string.kind_workouts
      RecordKind.EXERCISE -> R.string.kind_exercises
      RecordKind.DIET_PLAN -> R.string.kind_plans
      RecordKind.RECIPE -> R.string.kind_recipes
      RecordKind.INGREDIENT -> R.string.kind_ingredients
      RecordKind.MEAL -> R.string.kind_meals
      RecordKind.SHOPPING_LIST -> R.string.kind_shopping_lists
      RecordKind.SESSION -> R.string.kind_sessions
      RecordKind.BODY_WEIGHT -> R.string.kind_weights
      RecordKind.HOUSEHOLD_MEMBER -> R.string.kind_household
    }
  )

private fun choiceLabel(c: ConflictChoice) =
  when (c) {
    ConflictChoice.SKIP -> R.string.import_choice_skip
    ConflictChoice.REPLACE -> R.string.import_choice_replace
    ConflictChoice.NEWER_WINS -> R.string.import_choice_newer
    ConflictChoice.KEEP_BOTH -> R.string.import_choice_both
  }

private fun choiceHint(c: ConflictChoice) =
  when (c) {
    ConflictChoice.SKIP -> R.string.import_choice_skip_hint
    ConflictChoice.REPLACE -> R.string.import_choice_replace_hint
    ConflictChoice.NEWER_WINS -> R.string.import_choice_newer_hint
    ConflictChoice.KEEP_BOTH -> R.string.import_choice_both_hint
  }
