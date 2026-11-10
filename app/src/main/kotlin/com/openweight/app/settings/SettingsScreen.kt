package com.openweight.app.settings

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.TextButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.openweight.app.diet.formatKcal
import com.openweight.app.diet.formatGrams
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.openweight.app.R
import com.openweight.app.core.model.ThemeMode
import com.openweight.app.core.model.WeightUnit
import com.openweight.app.core.ui.ScreenScaffold
import com.openweight.app.core.ui.formatMedium
import com.openweight.app.transfer.ExportRequest
import com.openweight.app.transfer.ui.ExportDialog
import java.time.Instant
import java.time.ZoneId
import com.openweight.app.core.ui.theme.OpenWeightTheme

@Composable
fun SettingsRoute(onOpenExport: () -> Unit, modifier: Modifier = Modifier, viewModel: SettingsViewModel = hiltViewModel()) {
  val settings by viewModel.settings.collectAsStateWithLifecycle()
  val lastAutoBackup by viewModel.lastAutoBackup.collectAsStateWithLifecycle()
  var backingUp by remember { mutableStateOf(false) }
  var editingTargets by remember { mutableStateOf(false) }
  var confirmUndo by remember { mutableStateOf(false) }
  val pickFile =
    rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
      if (uri != null) viewModel.importFile(uri)
    }
  SettingsScreen(
    settings = settings ?: Settings(),
    onWeightUnitChange = viewModel::setWeightUnit,
    onThemeModeChange = viewModel::setThemeMode,
    onRestAlertsChange = viewModel::setRestAlerts,
    onEditTargets = { editingTargets = true },
    // Any file: some apps label .json files oddly, and the importer checks the content anyway.
    onImport = { pickFile.launch(arrayOf("*/*")) },
    onBackup = { backingUp = true },
    onExport = onOpenExport,
    lastAutoBackup = lastAutoBackup,
    onUndoRestore = { confirmUndo = true },
    modifier = modifier,
  )
  if (backingUp) ExportDialog(ExportRequest.Everything, onDismiss = { backingUp = false })
  if (editingTargets) {
    TargetsDialog(
      settings ?: Settings(),
      onSave = { kcal, protein -> editingTargets = false; viewModel.setTargets(kcal, protein) },
      onDismiss = { editingTargets = false },
    )
  }
  if (confirmUndo) {
    AlertDialog(
      onDismissRequest = { confirmUndo = false },
      title = { Text(stringResource(R.string.settings_undo_restore)) },
      text = { Text(stringResource(R.string.settings_undo_restore_confirm)) },
      confirmButton = {
        TextButton(onClick = { confirmUndo = false; viewModel.undoLastRestore() }, modifier = Modifier.testTag("undo_restore_continue")) {
          Text(stringResource(R.string.action_continue))
        }
      },
      dismissButton = { TextButton(onClick = { confirmUndo = false }) { Text(stringResource(R.string.action_cancel)) } },
    )
  }
}

@Composable
fun SettingsScreen(
  settings: Settings,
  onWeightUnitChange: (WeightUnit) -> Unit,
  onThemeModeChange: (ThemeMode) -> Unit,
  modifier: Modifier = Modifier,
  onRestAlertsChange: (Boolean) -> Unit = {},
  onEditTargets: () -> Unit = {},
  onImport: () -> Unit = {},
  onBackup: () -> Unit = {},
  onExport: () -> Unit = {},
  lastAutoBackup: Instant? = null,
  onUndoRestore: () -> Unit = {},
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

      SectionTitle(stringResource(R.string.settings_training))
      ListItem(
        modifier =
          Modifier.toggleable(value = settings.restAlerts, role = Role.Switch, onValueChange = onRestAlertsChange).testTag("rest_alerts"),
        headlineContent = { Text(stringResource(R.string.settings_rest_alerts)) },
        supportingContent = { Text(stringResource(R.string.settings_rest_alerts_hint)) },
        trailingContent = { Switch(checked = settings.restAlerts, onCheckedChange = null) },
      )

      SectionTitle(stringResource(R.string.settings_diet))
      ListItem(
        modifier = Modifier.clickable(onClick = onEditTargets).testTag("targets"),
        headlineContent = { Text(stringResource(R.string.settings_targets)) },
        supportingContent = {
          Text(
            when {
              settings.kcalTarget != null && settings.proteinTarget != null ->
                stringResource(R.string.settings_targets_value, formatKcal(settings.kcalTarget.toDouble()), formatGrams(settings.proteinTarget.toDouble()))
              settings.kcalTarget != null -> stringResource(R.string.settings_targets_kcal_only, formatKcal(settings.kcalTarget.toDouble()))
              settings.proteinTarget != null -> formatGrams(settings.proteinTarget.toDouble())
              else -> stringResource(R.string.settings_targets_none)
            }
          )
        },
      )

      HorizontalDivider(Modifier.padding(top = 8.dp))
      SectionTitle(stringResource(R.string.settings_data))
      ListItem(
        modifier = Modifier.clickable(onClick = onBackup).testTag("backup"),
        headlineContent = { Text(stringResource(R.string.settings_backup)) },
        supportingContent = { Text(stringResource(R.string.settings_backup_hint)) },
      )
      ListItem(
        modifier = Modifier.clickable(onClick = onExport).testTag("export"),
        headlineContent = { Text(stringResource(R.string.settings_export)) },
        supportingContent = { Text(stringResource(R.string.settings_export_hint)) },
      )
      ListItem(
        modifier = Modifier.clickable(onClick = onImport).testTag("import_file"),
        headlineContent = { Text(stringResource(R.string.settings_import)) },
        supportingContent = { Text(stringResource(R.string.settings_import_hint)) },
      )
      if (lastAutoBackup != null) {
        ListItem(
          modifier = Modifier.clickable(onClick = onUndoRestore).testTag("undo_restore"),
          headlineContent = { Text(stringResource(R.string.settings_undo_restore)) },
          supportingContent = {
            Text(stringResource(R.string.settings_undo_restore_hint, lastAutoBackup.atZone(ZoneId.systemDefault()).toLocalDate().formatMedium()))
          },
        )
      }

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

/** Daily calorie and protein targets; an empty field means no target. */
@Composable
private fun TargetsDialog(settings: Settings, onSave: (kcal: Int?, protein: Int?) -> Unit, onDismiss: () -> Unit) {
  var kcal by remember { mutableStateOf(settings.kcalTarget?.toString().orEmpty()) }
  var protein by remember { mutableStateOf(settings.proteinTarget?.toString().orEmpty()) }
  var error by remember { mutableStateOf(false) }
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(stringResource(R.string.settings_targets)) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
          value = kcal,
          onValueChange = { kcal = it; error = false },
          label = { Text(stringResource(R.string.targets_kcal)) },
          singleLine = true,
          isError = error,
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
          modifier = Modifier.fillMaxWidth().testTag("target_kcal"),
        )
        OutlinedTextField(
          value = protein,
          onValueChange = { protein = it; error = false },
          label = { Text(stringResource(R.string.targets_protein)) },
          singleLine = true,
          isError = error,
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
          modifier = Modifier.fillMaxWidth().testTag("target_protein"),
        )
        Text(
          if (error) {
            stringResource(
              R.string.targets_error,
              SettingsRepository.KCAL_RANGE.first,
              SettingsRepository.KCAL_RANGE.last,
              SettingsRepository.PROTEIN_RANGE.first,
              SettingsRepository.PROTEIN_RANGE.last,
            )
          } else stringResource(R.string.targets_hint),
          style = MaterialTheme.typography.bodySmall,
          color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    },
    confirmButton = {
      TextButton(
        onClick = {
          val k = kcal.trim().takeIf { it.isNotEmpty() }?.let { it.toIntOrNull() ?: -1 }
          val p = protein.trim().takeIf { it.isNotEmpty() }?.let { it.toIntOrNull() ?: -1 }
          error = (k != null && k !in SettingsRepository.KCAL_RANGE) || (p != null && p !in SettingsRepository.PROTEIN_RANGE)
          if (!error) onSave(k, p)
        },
        modifier = Modifier.testTag("targets_save"),
      ) {
        Text(stringResource(R.string.action_save))
      }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
  )
}

