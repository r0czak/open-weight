package com.openweight.app.core.database

import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OpenWeightDatabaseTest {
  private lateinit var database: OpenWeightDatabase
  private lateinit var sql: SupportSQLiteDatabase

  @Before
  fun setUp() {
    database =
      Room.inMemoryDatabaseBuilder(
          ApplicationProvider.getApplicationContext(),
          OpenWeightDatabase::class.java,
        )
        .allowMainThreadQueries()
        .build()
    sql = database.openHelper.writableDatabase
  }

  @After
  fun tearDown() {
    database.close()
  }

  @Test
  fun createsAllTables() {
    val tables = mutableListOf<String>()
    sql
      .query(
        "SELECT name FROM sqlite_master WHERE type = 'table' " +
          "AND name NOT LIKE 'sqlite_%' AND name NOT IN ('android_metadata', 'room_master_table')"
      )
      .use { cursor -> while (cursor.moveToNext()) tables += cursor.getString(0) }

    assertEquals(
      setOf(
        "exercise", "workout", "workout_exercise", "set_plan", "training_block",
        "block_workout", "block_schedule_day", "ingredient", "recipe", "recipe_ingredient",
        "diet_plan", "diet_plan_meal", "meal_entry", "shopping_list", "shopping_item",
        "workout_session", "session_set", "body_weight",
      ),
      tables.toSet(),
    )
  }

  @Test
  fun deletingWorkout_removesItsExercisesAndSets() {
    insertWorkoutWithOneSet()

    sql.execSQL("DELETE FROM workout WHERE id = 'w1'")

    assertEquals(0, count("workout_exercise"))
    assertEquals(0, count("set_plan"))
    assertEquals(1, count("exercise")) // the library exercise itself stays
  }

  @Test
  fun deletingExerciseThatIsInUse_isRefused() {
    insertWorkoutWithOneSet()

    assertThrows(SQLiteConstraintException::class.java) {
      sql.execSQL("DELETE FROM exercise WHERE id = 'e1'")
    }
  }

  @Test
  fun deletingWorkout_turnsItsScheduleDaysIntoRest() {
    sql.execSQL("INSERT INTO workout (id, name, type, notes, createdAt, updatedAt) VALUES ('w1', 'Push', '', '', 0, 0)")
    sql.execSQL(
      "INSERT INTO training_block (id, name, startDate, endDate, notes, createdAt, updatedAt) " +
        "VALUES ('b1', 'Winter', '2026-11-02', '2027-02-28', '', 0, 0)"
    )
    sql.execSQL(
      "INSERT INTO block_schedule_day (id, blockId, dayOfWeek, workoutId, createdAt, updatedAt) " +
        "VALUES ('d1', 'b1', 'MONDAY', 'w1', 0, 0)"
    )

    sql.execSQL("DELETE FROM workout WHERE id = 'w1'")

    sql.query("SELECT workoutId FROM block_schedule_day WHERE id = 'd1'").use {
      it.moveToFirst()
      assertEquals(true, it.isNull(0))
    }
  }

  private fun insertWorkoutWithOneSet() {
    sql.execSQL(
      "INSERT INTO exercise (id, name, muscleGroup, equipment, notes, createdAt, updatedAt) " +
        "VALUES ('e1', 'Bench press', 'Chest', 'Barbell', '', 0, 0)"
    )
    sql.execSQL("INSERT INTO workout (id, name, type, notes, createdAt, updatedAt) VALUES ('w1', 'Push A', 'Push', '', 0, 0)")
    sql.execSQL(
      "INSERT INTO workout_exercise (id, workoutId, exerciseId, position, notes, createdAt, updatedAt) " +
        "VALUES ('we1', 'w1', 'e1', 0, '', 0, 0)"
    )
    sql.execSQL(
      "INSERT INTO set_plan (id, workoutExerciseId, position, repsMin, weightKg, restSeconds, createdAt, updatedAt) " +
        "VALUES ('s1', 'we1', 0, 8, 60.0, 90, 0, 0)"
    )
  }

  private fun count(table: String): Int =
    sql.query("SELECT COUNT(*) FROM $table").use {
      it.moveToFirst()
      it.getInt(0)
    }
}
