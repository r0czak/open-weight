package com.openweight.app.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Every schema version must upgrade to the latest without losing data. */
@RunWith(AndroidJUnit4::class)
class MigrationTest {
  @get:Rule
  val helper =
    MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), OpenWeightDatabase::class.java)

  @Test
  fun v1ToV2_keepsSetsAndTurnsRepsIntoRepsMin() {
    helper.createDatabase(DB, 1).use { db ->
      db.execSQL("INSERT INTO exercise VALUES ('e1', 'Bench press', 'Chest', 'Barbell', '', 0, 0)")
      db.execSQL("INSERT INTO workout VALUES ('w1', 'Push A', 'Push', '', 0, 0)")
      db.execSQL("INSERT INTO workout_exercise VALUES ('we1', 'w1', 'e1', 0, '', 0, 0)")
      db.execSQL(
        "INSERT INTO set_plan (id, workoutExerciseId, position, reps, weightKg, restSeconds, createdAt, updatedAt) " +
          "VALUES ('s1', 'we1', 0, 8, 60.0, 90, 0, 0)"
      )
    }

    helper.runMigrationsAndValidate(DB, 2, true).use { db ->
      db.query("SELECT repsMin, repsMax, weightKg, restSeconds FROM set_plan WHERE id = 's1'").use {
        assertTrue(it.moveToFirst())
        assertEquals(8, it.getInt(0))
        assertTrue(it.isNull(1))
        assertEquals(60.0, it.getDouble(2), 0.0)
        assertEquals(90, it.getInt(3))
      }
    }
  }

  private companion object {
    const val DB = "migration-test.db"
  }
}
