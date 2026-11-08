package com.openweight.app.settings

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.TextButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.openweight.app.R
import com.openweight.app.core.model.ThemeMode
import com.openweight.app.core.model.WeightUnit
import com.openweight.app.core.ui.ScreenScaffold
import com.openweight.app.core.ui.theme.OpenWeightTheme

@Composable
fun SettingsRoute(modifier: Modifier = Modifier, viewModel: SettingsViewModel = hiltViewModel()) {
  val settings by viewModel.settings.collectAsStateWithLifecycle()
  val importState by viewModel.importState.collectAsStateWithLifecycle()
  val pickFile =
    rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
      if (uri != null) viewModel.importFile(uri)
    }
  SettingsScreen(
    settings = settings ?: Settings(),
    onWeightUnitChange = viewModel::setWeightUnit,
    onThemeModeChange = viewModel::setThemeMode,
    // Any file: some apps label .json files oddly, and the importer checks the content anyway.
    onImport = { pickFile.launch(arrayOf("*/*")) },
    modifier = modifier,
  )
  ImportResultDialog(importState, onDismiss = viewModel::dismissImport)
}

@Composable
private fun ImportResultDialog(state: ImportState, onDismiss: () -> Unit) {
  when (state) {
    ImportState.Idle -> Unit
    ImportState.Running ->
      AlertDialog(
        onDismissRequest = {},
        title = { Text(stringResource(R.string.import_running)) },
        text = { LinearProgressIndicator(Modifier.fillMaxWidth()) },
        confirmButton = {},
      )
    is ImportState.Failed ->
      AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.import_failed_title)) },
        text = {
          Text(
            (state.message ?: stringResource(R.string.import_failed_read)) + "\n\n" +
              stringResource(R.string.import_nothing_changed)
          )
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_ok)) } },
      )
    is ImportState.Done -> {
      val s = state.summary
      AlertDialog(
        onDismissRequest = onDismiss,
        title = {
          Text(
            stringResource(
              if (s.nothingNew) R.string.import_nothing_new_title else R.string.import_done_title
            )
          )
        },
        text = {
          val lines = buildList {
            if (s.blocksAdded > 0) add(pluralStringResource(R.plurals.import_blocks_added, s.blocksAdded, s.blocksAdded))
            if (s.workoutsAdded > 0) add(pluralStringResource(R.plurals.import_workouts_added, s.workoutsAdded, s.workoutsAdded))
            if (s.exercisesAdded > 0) add(pluralStringResource(R.plurals.import_exercises_added, s.exercisesAdded, s.exercisesAdded))
            if (s.exercisesMatched > 0) add(pluralStringResource(R.plurals.import_exercises_matched, s.exercisesMatched, s.exercisesMatched))
            val skipped = s.workoutsSkipped + s.blocksSkipped
            if (skipped > 0) add(pluralStringResource(R.plurals.import_skipped, skipped, skipped))
          }
          Text(lines.joinToString("\n"), modifier = Modifier.testTag("import_summary"))
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_ok)) } },
      )
    }
  }
}

@Composable
fun SettingsScreen(
  settings: Settings,
  onWeightUnitChange: (WeightUnit) -> Unit,
  onThemeModeChange: (ThemeMode) -> Unit,
  modifier: Modifier = Modifier,
  onImport: () -> Unit = {},
) {
  val context = LocalContext.current
  val version = remember(context) { context.versionName() }
  ScreenScaffold(title = stringResource(R.string.tab_settings), modifier = modifier) { padding ->
    Column(
      modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      SectionTitle(stringResource(R.string.settings_units))
      ChoiceRow(
        options = WeightUnit.entries,
        selected = settings.weightUnit,
        label = {
          when (it) {
            WeightUnit.KG -> stringResource(R.string.settings_unit_kg)
            WeightUnit.LB -> stringResource(R.string.settings_unit_lb)
          }
        },
        onSelect = onWeightUnitChange,
      )

      SectionTitle(stringResource(R.string.settings_theme))
      ChoiceRow(
        options = ThemeMode.entries,
        selected = settings.themeMode,
        label = {
          when (it) {
            ThemeMode.SYSTEM -> stringResource(R.string.settings_theme_system)
            ThemeMode.LIGHT -> stringResource(R.string.settings_theme_light)
            ThemeMode.DARK -> stringResource(R.string.settings_theme_dark)
          }
        },
        onSelect = onThemeModeChange,
      )

      HorizontalDivider(Modifier.padding(top = 8.dp))
      SectionTitle(stringResource(R.string.settings_data))
      ListItem(
        headlineContent = { Text(stringResource(R.string.settings_backup)) },
        supportingContent = { Text(stringResource(R.string.coming_soon)) },
      )
      ListItem(
        modifier = Modifier.clickable(onClick = onImport).testTag("import_file"),
        headlineContent = { Text(stringResource(R.string.settings_import)) },
        supportingContent = { Text(stringResource(R.string.settings_import_hint)) },
      )

      HorizontalDivider()
      SectionTitle(stringResource(R.string.settings_about))
      ListItem(
        headlineContent = { Text(stringResource(R.string.settings_version, version)) },
        supportingContent = { Text(stringResource(R.string.settings_offline)) },
      )
    }
  }
}

@Composable
private fun SectionTitle(text: String) {
  Text(
    text = text,
    style = MaterialTheme.typography.titleSmall,
    color = MaterialTheme.colorScheme.primary,
    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp),
  )
}

@Composable
private fun <T> ChoiceRow(
  options: List<T>,
  selected: T,
  label: @Composable (T) -> String,
  onSelect: (T) -> Unit,
) {
  SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
    options.forEachIndexed { index, option ->
      SegmentedButton(
        selected = option == selected,
        onClick = { onSelect(option) },
        shape = SegmentedButtonDefaults.itemShape(index, options.size),
        label = { Text(label(option)) },
      )
    }
  }
}

private fun Context.versionName(): String =
  runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull() ?: "?"

@Preview(showBackground = true)
@Composable
private fun SettingsScreenPreview() {
  OpenWeightTheme { SettingsScreen(Settings(), {}, {}) }
}
