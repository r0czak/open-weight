package com.openweight.app.transfer

import android.database.SQLException
import android.net.Uri
import com.openweight.app.core.model.ThemeMode
import com.openweight.app.core.model.WeightUnit
import com.openweight.app.settings.SettingsRepository
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

sealed interface ImportStep {
  data object Reading : ImportStep

  /** [message] says why; null means the file couldn't be read at all. */
  data class Failed(val message: String?) : ImportStep

  /** [isUndo]: the copy taken before the last "Replace everything", put back the same way. */
  data class Ready(val file: OpenWeightFile, val preview: ImportPreview, val isUndo: Boolean = false) : ImportStep

  data object Running : ImportStep

  data class Done(val result: ImportResult, val replacedEverything: Boolean) : ImportStep
}

/**
 * The import in progress, shared by whoever opens a file (Settings, or another app sharing one)
 * and the preview screen. Each [open] bumps [openCount], which the app follows to the preview.
 */
@Singleton
class ImportSession
@Inject
constructor(
  private val importer: ImportService,
  private val exporter: ExportService,
  private val files: TransferFiles,
  private val settings: SettingsRepository,
) {
  private val _step = MutableStateFlow<ImportStep?>(null)
  val step: StateFlow<ImportStep?> = _step.asStateFlow()

  private val _openCount = MutableStateFlow(0)
  val openCount: StateFlow<Int> = _openCount.asStateFlow()

  suspend fun open(uri: Uri) = load { files.read(uri) }

  suspend fun openBytes(bytes: ByteArray) = load { bytes }

  /** Opens the copy taken before the last "Replace everything". */
  suspend fun openUndo() = load(isUndo = true) { files.readAutoBackup() }

  /**
   * Runs to the end even when the caller goes away (the screen rotates, or the user leaves the
   * preview): otherwise the step would stay on "Reading" for good.
   */
  private suspend fun load(isUndo: Boolean = false, read: suspend () -> ByteArray) =
    withContext(NonCancellable) {
      _step.value = ImportStep.Reading
      _openCount.update { it + 1 }
      _step.value =
        try {
          val file = importer.parse(read())
          ImportStep.Ready(file, importer.preview(file), isUndo)
        } catch (e: ImportException) {
          ImportStep.Failed(e.message)
        } catch (e: IOException) {
          ImportStep.Failed(null)
        } catch (e: SecurityException) {
          ImportStep.Failed(null)
        } catch (e: OutOfMemoryError) {
          ImportStep.Failed(null)
        }
    }

  /**
   * Imports the opened file. "Replace everything" first saves the current data (and settings) to
   * the phone, so it can be put back from Settings.
   */
  suspend fun run(options: ImportOptions) {
    val ready = _step.value as? ImportStep.Ready ?: return
    // Not cancellable: leaving the screen mid-import must not stop between the data and its settings.
    withContext(NonCancellable) {
      _step.value = ImportStep.Running
      _step.value =
        try {
          if (options.replaceEverything) {
            files.saveAutoBackup(exporter.export(ExportRequest.Everything, currentSettings()).bytes)
          }
          val result = importer.import(ready.file, options)
          result.settings?.let { apply(it) }
          ImportStep.Done(result, options.replaceEverything)
        } catch (e: ImportException) {
          ImportStep.Failed(e.message)
        } catch (e: IOException) {
          ImportStep.Failed(null)
        } catch (e: SQLException) {
          // The database refused a record the checks let through; the transaction undid the rest.
          ImportStep.Failed(null)
        }
    }
  }

  fun clear() {
    _step.value = null
  }

  suspend fun currentSettings(): SettingsDto = settings.settings.first().let { SettingsDto(it.weightUnit.name, it.themeMode.name) }

  private suspend fun apply(s: SettingsDto) {
    WeightUnit.entries.firstOrNull { it.name == s.weightUnit }?.let { settings.setWeightUnit(it) }
    ThemeMode.entries.firstOrNull { it.name == s.themeMode }?.let { settings.setThemeMode(it) }
  }
}
