package com.openweight.app.transfer

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.openweight.app.core.database.OpenWeightDatabase
import com.openweight.app.settings.SettingsRepository
import com.openweight.app.testing.FakeTimeAndIds
import com.openweight.app.testing.inMemoryDatabase
import com.openweight.app.workout.data.WorkoutRepository
import java.io.File
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith

/** An import must finish (or fail) even when the screen that started it goes away. */
@RunWith(AndroidJUnit4::class)
class ImportSessionTest {
  @get:Rule val tmp = TemporaryFolder()

  private lateinit var db: OpenWeightDatabase
  private lateinit var workouts: WorkoutRepository

  private val file =
    """{"format":"open-weight","formatVersion":1,"scope":{"kind":"full"},
       "data":{"exercises":[{"id":"e","name":"Curl"}],"settings":{"weightUnit":"LB"}}}"""
      .toByteArray()

  @Before
  fun setUp() {
    db = inMemoryDatabase()
    workouts = WorkoutRepository(db, FakeTimeAndIds())
  }

  @After fun tearDown() = db.close()

  private fun TestScope.session(): Pair<ImportSession, SettingsRepository> {
    val clock = FakeTimeAndIds()
    val settings = SettingsRepository(PreferenceDataStoreFactory.create(scope = backgroundScope) { File(tmp.root, "settings.preferences_pb") })
    val importer = ImportService(db, clock)
    val exporter = ExportService(db, clock) { "test" }
    return ImportSession(importer, exporter, TransferFiles(ApplicationProvider.getApplicationContext()), settings) to settings
  }

  @Test
  fun openingAFile_finishes_whenTheCallerIsCancelled() = runTest {
    val (session, _) = session()

    // As when the screen rotates while a shared file is being read.
    launch(start = CoroutineStart.UNDISPATCHED) { session.openBytes(file) }.cancelAndJoin()

    assertTrue("${session.step.value}", session.step.value is ImportStep.Ready)
    assertEquals(1, session.openCount.value)
  }

  @Test
  fun runningAnImport_finishesWithItsSettings_whenTheCallerIsCancelled() = runTest {
    val (session, settings) = session()
    workouts.saveExercise(null, "Old exercise", "", "", "")
    session.openBytes(file)

    // As when the user leaves the preview right after tapping Import.
    launch(start = CoroutineStart.UNDISPATCHED) { session.run(ImportOptions(replaceEverything = true)) }.cancelAndJoin()

    val done = session.step.value as ImportStep.Done
    assertEquals(1, done.result[RecordKind.EXERCISE].added)
    assertEquals(listOf("Curl"), workouts.exercises().first().map { it.name })
    assertEquals("LB", settings.settings.first().weightUnit.name)
    // The data replaced is kept for "Undo last restore".
    val (again, _) = session()
    again.openUndo()
    val undo = again.step.value as ImportStep.Ready
    assertTrue(undo.isUndo)
    assertEquals(1, undo.preview.counts.getValue(RecordKind.EXERCISE).inFile)
  }

  @Test
  fun unreadableFile_fails_andCanBeDismissed() = runTest {
    val (session, _) = session()

    session.openBytes("not json".toByteArray())

    assertTrue(session.step.value is ImportStep.Failed)
    session.clear()
    assertEquals(null, session.step.value)
  }
}
