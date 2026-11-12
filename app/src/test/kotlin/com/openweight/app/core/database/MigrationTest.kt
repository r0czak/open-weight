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

  @Test
  fun v2ToV3_keepsIngredients_withNoPieceWeight() {
    helper.createDatabase(DB3, 2).use { db ->
      db.execSQL(
        "INSERT INTO ingredient (id, name, defaultUnit, shopCategory, kcalPer100, proteinPer100, carbsPer100, fatPer100, createdAt, updatedAt) " +
          "VALUES ('i1', 'Oats', 'G', 'Grains & pasta', 379.0, 13.2, 67.7, 6.5, 0, 0)"
      )
    }

    helper.runMigrationsAndValidate(DB3, 3, true).use { db ->
      db.query("SELECT name, kcalPer100, gramsPerPiece FROM ingredient WHERE id = 'i1'").use {
        assertTrue(it.moveToFirst())
        assertEquals("Oats", it.getString(0))
        assertEquals(379.0, it.getDouble(1), 0.0)
        assertTrue(it.isNull(2))
      }
    }
  }

  @Test
  fun v3ToV4_keepsWorkouts_andAddsEmptySessionTables() {
    helper.createDatabase(DB4, 3).use { db ->
      db.execSQL("INSERT INTO workout VALUES ('w1', 'Push A', 'Push', '', 0, 0)")
    }

    helper.runMigrationsAndValidate(DB4, 4, true).use { db ->
      db.query("SELECT name FROM workout WHERE id = 'w1'").use {
        assertTrue(it.moveToFirst())
        assertEquals("Push A", it.getString(0))
      }
      db.query("SELECT (SELECT COUNT(*) FROM workout_session) + (SELECT COUNT(*) FROM session_set)").use {
        assertTrue(it.moveToFirst())
        assertEquals(0, it.getInt(0))
      }
    }
  }

  private companion object {
    const val DB4 = "migration-test-4.db"
    const val DB3 = "migration-test-3.db"
    const val DB = "migration-test.db"
  }
}
