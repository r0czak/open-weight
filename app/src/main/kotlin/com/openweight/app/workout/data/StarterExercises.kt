package com.openweight.app.workout.data

import android.content.Context
import com.openweight.app.transfer.ImportService
import com.openweight.app.transfer.KindResult
import com.openweight.app.transfer.RecordKind
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * About 40 common exercises, offered when the library is empty and from its menu. They ship as an
 * Open Weight file (`assets/starter-exercises.openweight.json`, fixed ids) and go through the
 * normal import: exercises the user already has, by id or by name, are left as they are.
 */
@Singleton
class StarterExercises @Inject constructor(@param:ApplicationContext private val context: Context, private val importer: ImportService) {
  suspend fun add(): KindResult {
    val bytes = withContext(Dispatchers.IO) { context.assets.open(ASSET).use { it.readBytes() } }
    return importer.import(bytes)[RecordKind.EXERCISE]
  }

  companion object {
    const val ASSET = "starter-exercises.openweight.json"
    const val COUNT = 40
  }
}
