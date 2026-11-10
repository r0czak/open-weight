package com.openweight.app.transfer

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.openweight.app.core.database.OpenWeightDatabase
import com.openweight.app.testing.FakeTimeAndIds
import com.openweight.app.testing.inMemoryDatabase
import com.openweight.app.workout.data.WorkoutRepository
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ImportServiceTest {
  private lateinit var db: OpenWeightDatabase
  private lateinit var importer: ImportService
  private lateinit var workouts: WorkoutRepository

  @Before
  fun setUp() {
    db = inMemoryDatabase()
    val clock = FakeTimeAndIds()
    importer = ImportService(db, clock)
    workouts = WorkoutRepository(db, clock)
  }

  @After fun tearDown() = db.close()

  /** The real plan file shipped in the repository (`plans/`), read from the module directory. */
  private val winterArc: ByteArray
    get() = File("../plans/winter-arc.openweight.json").readBytes()

  @Test
  fun winterArcPlan_importsCompletely() = runTest {
    val result = importer.import(winterArc)

    assertEquals(KindResult(added = 69), result[RecordKind.EXERCISE])
    assertEquals(KindResult(added = 16), result[RecordKind.WORKOUT])
    assertEquals(KindResult(added = 6), result[RecordKind.TRAINING_BLOCK])
    val all = workouts.workouts().first()
    assertEquals(16, all.size)
    assertEquals(367, all.sumOf { it.setCount })

    val pushA = all.single { it.name == "Push A · Intro" }
    val detail = workouts.workout(pushA.id).first()!!
    assertEquals("Leg press", detail.entries.first().exerciseName)
    assertEquals(listOf(10, 10, 10), detail.entries.first().sets.map { it.repsMin })
    assertEquals(listOf(12, 12, 12), detail.entries.first().sets.map { it.repsMax })
    assertEquals(listOf("Winter Arc 1 · Intro"), detail.blockNames)

    // Option 1 workouts are shared by mesocycles M1 and M3.
    val optionOne = all.single { it.name == "Push A · Core 2 · Option 1" }
    assertEquals(2, optionOne.blockCount)
  }

  @Test
  fun winterArcPlan_scheduleRunsTueWedFriSat() = runTest {
    importer.import(winterArc)

    val rows = mutableListOf<String>()
    db.openHelper.readableDatabase
      .query(
        """
        SELECT d.dayOfWeek, w.name FROM block_schedule_day d
        JOIN training_block b ON b.id = d.blockId LEFT JOIN workout w ON w.id = d.workoutId
        WHERE b.name = 'Winter Arc 2 · Core 1' AND d.workoutId IS NOT NULL
        ORDER BY CASE d.dayOfWeek WHEN 'TUESDAY' THEN 2 WHEN 'WEDNESDAY' THEN 3
          WHEN 'FRIDAY' THEN 5 WHEN 'SATURDAY' THEN 6 END
        """
      )
      .use { while (it.moveToNext()) rows += "${it.getString(0)} ${it.getString(1)}" }

    assertEquals(
      listOf(
        "TUESDAY Push A · Core 1 (heavy)",
        "WEDNESDAY Pull A · Core 1 (heavy)",
        "FRIDAY Push B · Core 1 (volume)",
        "SATURDAY Pull B · Core 1 (volume)",
      ),
      rows,
    )
    // The other three days of each of the 6 blocks are rest days.
    db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM block_schedule_day WHERE workoutId IS NULL").use {
      it.moveToFirst()
      assertEquals(18, it.getInt(0))
    }
  }

  @Test
  fun workoutList_followsThePlanOrder() = runTest {
    importer.import(winterArc)
    workouts.createWorkout("Arms extra", "")

    val names = workouts.workouts().first().map { it.name }
    assertEquals(
      listOf("Push A · Intro", "Pull A · Intro", "Push B · Intro", "Pull B · Intro", "Push A · Core 1 (heavy)"),
      names.take(5),
    )
    assertEquals("Arms extra", names.last()) // not in any block yet
  }

  @Test
  fun importingTwice_changesNothing() = runTest {
    importer.import(winterArc)
    val again = importer.import(winterArc)

    assertTrue(again.nothingChanged)
    assertEquals(69, again[RecordKind.EXERCISE].skipped)
    assertEquals(16, again[RecordKind.WORKOUT].skipped)
    assertEquals(6, again[RecordKind.TRAINING_BLOCK].skipped)
    assertEquals(16, workouts.workouts().first().size)
  }

  @Test
  fun exerciseWithSameName_isReusedNotDuplicated() = runTest {
    val mine = workouts.saveExercise(null, "LEG PRESS", "Legs", "Machine", "")

    val result = importer.import(winterArc)

    assertEquals(KindResult(added = 68, skipped = 1), result[RecordKind.EXERCISE])
    val legPress = workouts.exercises().first().filter { it.name.equals("leg press", ignoreCase = true) }
    assertEquals(listOf(mine), legPress.map { it.id })
    assertEquals(1, legPress.single().usageCount) // used by Push A · Intro
  }

  @Test
  fun badFiles_areRefused_andNothingIsWritten() = runTest {
    val cases =
      mapOf(
        "not json" to "This isn't an Open Weight file",
        """{"format":"something-else","formatVersion":1}""" to "This isn't an Open Weight file",
        """{"format":"open-weight","formatVersion":99}""" to "newer version",
        file(workouts = """[{"id":"w","name":"W","exercises":[{"exerciseId":"missing","sets":[{"repsMin":8}]}]}]""") to
          "isn't in the file",
        file(
          exercises = """[{"id":"e","name":"E"}]""",
          workouts = """[{"id":"w","name":"W","exercises":[{"exerciseId":"e","sets":[{"repsMin":10,"repsMax":8}]}]}]""",
        ) to "invalid set",
        file(
          exercises = """[{"id":"e","name":"E"}]""",
          workouts = """[{"id":"w","name":"W","exercises":[{"exerciseId":"e","sets":[{"repsMin":8,"weightKg":1e9}]}]}]""",
        ) to "invalid set",
        file(
          exercises = """[{"id":"e","name":"E"}]""",
          workouts = """[{"id":"w","name":"W","exercises":[{"exerciseId":"e","sets":${List(21) { """{"repsMin":8}""" }}}]}]""",
        ) to "more than 20 sets",
        file(blocks = block(workoutIds = (1..11).map { "w$it" })) to "more than 10 workouts",
        file(
          workouts = """[{"id":"w1","name":"A"},{"id":"w2","name":"B"}]""",
          blocks = block(workoutIds = listOf("w1"), schedule = """[{"dayOfWeek":"MONDAY","workoutId":"w2"}]"""),
        ) to "isn't part of the block",
        file(workouts = """[{"id":"w1","name":"A"}]""", blocks = block(workoutIds = listOf("w1"), end = "2026-10-01")) to
          "ends before it starts",
      )
    for ((json, expected) in cases) {
      try {
        importer.import(json.toByteArray())
        fail("Expected refusal for: $json")
      } catch (e: ImportException) {
        assertTrue("\"${e.message}\" should mention \"$expected\"", e.message!!.contains(expected))
      }
    }
    assertEquals(0, workouts.workouts().first().size)
    assertEquals(0, workouts.exercises().first().size)
  }

  @Test
  fun fileSavedWithAByteOrderMark_imports() = runTest {
    val json = "\uFEFF" + file(exercises = """[{"id":"e","name":"Curl"}]""")

    assertEquals(1, importer.import(json.toByteArray())[RecordKind.EXERCISE].added)
  }

  @Test
  fun unknownFields_areIgnored() = runTest {
    val json =
      """{"format":"open-weight","formatVersion":1,"futureField":true,
         "data":{"bodyWeights":[{"date":"2026-11-08"}],"exercises":[{"id":"e","name":"Curl","colour":"red"}]}}"""

    assertEquals(1, importer.import(json.toByteArray())[RecordKind.EXERCISE].added)
  }

  private fun file(exercises: String = "[]", workouts: String = "[]", blocks: String = "[]") =
    """{"format":"open-weight","formatVersion":1,"data":{"exercises":$exercises,"workouts":$workouts,"trainingBlocks":$blocks}}"""

  private fun block(workoutIds: List<String>, schedule: String = "[]", end: String = "2026-11-30") =
    """[{"id":"b","name":"Block","startDate":"2026-11-03","endDate":"$end",
       "workoutIds":${workoutIds.joinToString(",", "[", "]") { "\"$it\"" }},"schedule":$schedule}]"""
}
