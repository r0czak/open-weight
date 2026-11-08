package com.openweight.app.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openweight.app.core.model.ThemeMode
import com.openweight.app.core.model.WeightUnit
import com.openweight.app.transfer.ImportException
import com.openweight.app.transfer.ImportService
import com.openweight.app.transfer.ImportSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface ImportState {
  data object Idle : ImportState

  data object Running : ImportState

  data class Done(val summary: ImportSummary) : ImportState

  data class Failed(val message: String?) : ImportState
}

@HiltViewModel
class SettingsViewModel
@Inject
constructor(
  private val repository: SettingsRepository,
  private val importService: ImportService,
  @param:ApplicationContext private val context: Context,
) : ViewModel() {

  /** Null until the stored settings have been read, so the UI never flashes the wrong theme. */
  val settings: StateFlow<Settings?> =
    repository.settings.stateIn(viewModelScope, SharingStarted.Eagerly, null)

  private val _importState = MutableStateFlow<ImportState>(ImportState.Idle)
  val importState: StateFlow<ImportState> = _importState.asStateFlow()

  fun setWeightUnit(unit: WeightUnit) {
    viewModelScope.launch { repository.setWeightUnit(unit) }
  }

  fun setThemeMode(mode: ThemeMode) {
    viewModelScope.launch { repository.setThemeMode(mode) }
  }

  fun importFile(uri: Uri) {
    _importState.value = ImportState.Running
    viewModelScope.launch {
      _importState.value =
        try {
          val bytes =
            withContext(Dispatchers.IO) {
              context.contentResolver.openInputStream(uri)?.use { input ->
                // Read at most one byte past the limit, so an oversized file is refused cheaply.
                input.readNBytesCompat(ImportService.MAX_FILE_BYTES + 1)
              } ?: throw IOException("Can't open the file")
            }
          ImportState.Done(importService.import(bytes))
        } catch (e: ImportException) {
          ImportState.Failed(e.message)
        } catch (e: IOException) {
          ImportState.Failed(null)
        } catch (e: SecurityException) {
          ImportState.Failed(null)
        }
    }
  }

  fun dismissImport() {
    _importState.value = ImportState.Idle
  }
}

private fun java.io.InputStream.readNBytesCompat(limit: Int): ByteArray {
  val out = java.io.ByteArrayOutputStream()
  val buffer = ByteArray(64 * 1024)
  while (out.size() < limit) {
    val read = read(buffer, 0, minOf(buffer.size, limit - out.size()))
    if (read < 0) break
    out.write(buffer, 0, read)
  }
  return out.toByteArray()
}
