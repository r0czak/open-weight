package com.openweight.app.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openweight.app.core.model.ThemeMode
import com.openweight.app.core.model.WeightUnit
import com.openweight.app.transfer.ImportSession
import com.openweight.app.transfer.TransferFiles
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel
@Inject
constructor(
  private val repository: SettingsRepository,
  private val importSession: ImportSession,
  private val files: TransferFiles,
) : ViewModel() {

  /** Null until the stored settings have been read, so the UI never flashes the wrong theme. */
  val settings: StateFlow<Settings?> =
    repository.settings.stateIn(viewModelScope, SharingStarted.Eagerly, null)

  /** When the copy taken before the last "Replace everything" was saved; re-read after each import. */
  val lastAutoBackup: StateFlow<Instant?> =
    importSession.step
      .map { files.autoBackupTime() }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

  fun setWeightUnit(unit: WeightUnit) {
    viewModelScope.launch { repository.setWeightUnit(unit) }
  }

  fun setRestAlerts(on: Boolean) {
    viewModelScope.launch { repository.setRestAlerts(on) }
  }

  fun setThemeMode(mode: ThemeMode) {
    viewModelScope.launch { repository.setThemeMode(mode) }
  }

  /** Opens the file in the import preview (the app navigates there). */
  fun importFile(uri: Uri) {
    viewModelScope.launch { importSession.open(uri) }
  }

  fun undoLastRestore() {
    viewModelScope.launch { importSession.openUndo() }
  }
}
