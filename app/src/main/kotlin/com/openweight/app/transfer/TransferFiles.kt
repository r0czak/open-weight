package com.openweight.app.transfer

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Files on the phone: reading a picked or shared file, saving an export where the user chose,
 * handing one to Share, and the automatic copy taken before "Replace everything".
 */
@Singleton
class TransferFiles @Inject constructor(@param:ApplicationContext private val context: Context) {
  private val autoBackup: File
    get() = File(context.filesDir, "backups/before-restore.json")

  /** Reads at most one byte past the import limit, so an oversized file is refused cheaply. */
  suspend fun read(uri: Uri): ByteArray =
    withContext(Dispatchers.IO) {
      context.contentResolver.openInputStream(uri)?.use { it.readAtMost(ImportService.MAX_FILE_BYTES + 1) }
        ?: throw IOException("Can't open the file")
    }

  suspend fun write(uri: Uri, bytes: ByteArray) =
    withContext(Dispatchers.IO) {
      context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(bytes) } ?: throw IOException("Can't write the file")
    }

  /** A chooser that sends [file] to another app; the file lives in the cache until the next export. */
  suspend fun shareIntent(file: ExportedFile, chooserTitle: String): Intent =
    withContext(Dispatchers.IO) {
      val dir = File(context.cacheDir, "exports").apply { deleteRecursively(); mkdirs() }
      val out = File(dir, file.fileName).apply { writeBytes(file.bytes) }
      val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", out)
      val send =
        Intent(Intent.ACTION_SEND)
          .setType("application/json")
          .putExtra(Intent.EXTRA_STREAM, uri)
          .putExtra(Intent.EXTRA_SUBJECT, file.fileName)
          .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
      Intent.createChooser(send, chooserTitle)
    }

  suspend fun saveAutoBackup(bytes: ByteArray) =
    withContext(Dispatchers.IO) {
      autoBackup.parentFile?.mkdirs()
      // Write next to it, then swap, so a failed write never leaves half a backup.
      val temp = File(autoBackup.path + ".tmp").apply { writeBytes(bytes) }
      if (!temp.renameTo(autoBackup)) throw IOException("Can't save the backup")
    }

  /** When the copy taken before the last "Replace everything" was made, if there is one. */
  suspend fun autoBackupTime(): Instant? =
    withContext(Dispatchers.IO) { autoBackup.takeIf { it.exists() }?.let { Instant.ofEpochMilli(it.lastModified()) } }

  suspend fun readAutoBackup(): ByteArray = withContext(Dispatchers.IO) { autoBackup.readBytes() }
}

private fun InputStream.readAtMost(limit: Int): ByteArray {
  val out = java.io.ByteArrayOutputStream()
  val buffer = ByteArray(64 * 1024)
  while (out.size() < limit) {
    val read = read(buffer, 0, minOf(buffer.size, limit - out.size()))
    if (read < 0) break
    out.write(buffer, 0, read)
  }
  return out.toByteArray()
}
