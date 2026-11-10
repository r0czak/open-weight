package com.openweight.app.workout.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.openweight.app.core.database.OpenWeightDatabase
import com.openweight.app.core.database.entity.BlockWorkoutEntity
import com.openweight.app.core.database.entity.TrainingBlockEntity
import com.openweight.app.testing.FakeTimeAndIds
import com.openweight.app.testing.inMemoryDatabase
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorkoutRepositoryTest {
  private lateinit var db: OpenWeightDatabase
  private lateinit var repo: WorkoutRepository

  @Before
  fun setUp() {
    db = inMemoryDatabase()
    repo = WorkoutRepository(db, FakeTimeAndIds())
  }

  @After fun tearDown() = db.close()

  private suspend fun detail(id: String) = repo.workout(id).first()!!

  @Test
  fun exerciseNames_areUniqueIgnoringCase() = runTest {
    repo.saveExercise(null, "Bench press", "Chest", "Barbell", "")

    assertThrows(DuplicateNameException::class.java) {
      kotlinx.coroutines.runBlocking { repo.saveExercise(null, "  bench PRESS ", "", "", "") }
    }
  }

  @Test
  fun editingExercise_keepsItsIdAndTrimsFields() = runTest {
    val id = repo.saveExercise(null, "Squat", "", "", "")
    repo.saveExercise(id, "  Back squat ", " Quads ", "Barbell", "")

    val e = repo.getExercise(id)!!
    assertEquals("Back squat", e.name)
    assertEquals("Quads", e.muscleGroup)
  }

  @Test
  fun exerciseInUse_cannotBeDeleted_untilRemovedFromWorkout() = runTest {
    val ex = repo.saveExercise(null, "Bench press", "", "", "")
    val w = repo.createWorkout("Push A", "Push")
    val entry = repo.addExercise(w, ex, 3, SetValues(8, 10))

    assertEquals(DeleteResult.InUse(1), repo.deleteExercise(ex))
    assertEquals(1, repo.exercises().first().single().usageCount)

    repo.removeEntry(entry)
    assertEquals(DeleteResult.Deleted, repo.deleteExercise(ex))
    assertNull(repo.getExercise(ex))
  }

  @Test
  fun addExercise_createsIdenticalSets_inOrder() = runTest {
    val bench = repo.saveExercise(null, "Bench press", "", "", "")
    val row = repo.saveExercise(null, "Row", "", "", "")
    val w = repo.createWorkout("Push A", "Push")
    repo.addExercise(w, bench, 4, SetValues(5, 7, 80.0, 150))
    repo.addExercise(w, row, 2, SetValues(10))

    val d = detail(w)
    assertEquals(listOf("Bench press", "Row"), d.entries.map { it.exerciseName })
    assertEquals(4, d.entries[0].sets.size)
    assertEquals(PlannedSet(d.entries[0].sets[0].id, 5, 7, 80.0, 150), d.entries[0].sets[0])
    assertEquals(SetValues.DEFAULT_REST_SECONDS, d.entries[1].sets[0].restSeconds)
    assertEquals(6, repo.workouts().first().single().setCount)
  }

  @Test
  fun moveEntry_reordersAndStopsAtTheEnds() = runTest {
    val w = repo.createWorkout("Pull", "")
    val ids = listOf("A", "B", "C").map { name ->
      repo.addExercise(w, repo.saveExercise(null, name, "", "", ""), 1, SetValues(8))
    }

    repo.moveEntry(ids[2], -1) // A C B
    repo.moveEntry(ids[0], -5) // already first: no change
    repo.moveEntry(ids[0], 1) // C A B

    assertEquals(listOf("C", "A", "B"), detail(w).entries.map { it.exerciseName })
  }

  @Test
  fun removingEntry_keepsPositionsContiguous() = runTest {
    val w = repo.createWorkout("Pull", "")
    val ids = listOf("A", "B", "C").map { name ->
      repo.addExercise(w, repo.saveExercise(null, name, "", "", ""), 1, SetValues(8))
    }
    repo.removeEntry(ids[0])
    repo.moveEntry(ids[2], -1)

    assertEquals(listOf("C", "B"), detail(w).entries.map { it.exerciseName })
  }

  @Test
  fun addSet_copiesTheLastSet() = runTest {
    val w = repo.createWorkout("Push", "")
    val entry = repo.addExercise(w, repo.saveExercise(null, "Dips", "", "", ""), 1, SetValues(8, 10, 20.0, 120))
    repo.addSet(entry)

    val sets = detail(w).entries.single().sets
    assertEquals(2, sets.size)
    assertEquals(sets[0].copy(id = sets[1].id), sets[1])
  }

  @Test
  fun addSet_stopsAtTheLimit_withoutFailing() = runTest {
    val w = repo.createWorkout("Push", "")
    val entry = repo.addExercise(w, repo.saveExercise(null, "Dips", "", "", ""), WorkoutRepository.MAX_SETS, SetValues(8))

    repo.addSet(entry)

    assertEquals(WorkoutRepository.MAX_SETS, detail(w).entries.single().sets.size)
  }

  @Test
  fun updateSet_changesOneOrAllSets() = runTest {
    val w = repo.createWorkout("Push", "")
    repo.addExercise(w, repo.saveExercise(null, "Bench", "", "", ""), 3, SetValues(8))
    val sets = detail(w).entries.single().sets

    repo.updateSet(sets[1].id, SetValues(6, 8, 70.0, 180), applyToAll = false)
    assertEquals(listOf(8, 6, 8), detail(w).entries.single().sets.map { it.repsMin })

    repo.updateSet(sets[0].id, SetValues(5, null, 75.0, 150), applyToAll = true)
    assertEquals(
      List(3) { Triple(5, null, 75.0) },
      detail(w).entries.single().sets.map { Triple(it.repsMin, it.repsMax, it.weightKg) },
    )
  }

  @Test
  fun deleteSet_keepsTheOthersInOrder() = runTest {
    val w = repo.createWorkout("Push", "")
    repo.addExercise(w, repo.saveExercise(null, "Bench", "", "", ""), 3, SetValues(8))
    val sets = detail(w).entries.single().sets
    repo.updateSet(sets[2].id, SetValues(3), applyToAll = false)

    repo.deleteSet(sets[0].id)
    repo.addSet(detail(w).entries.single().id)

    assertEquals(listOf(8, 3, 3), detail(w).entries.single().sets.map { it.repsMin })
  }

  @Test
  fun duplicateWorkout_copiesEverythingWithNewIds() = runTest {
    val w = repo.createWorkout("Push A", "Push")
    repo.updateWorkoutInfo(w, "Push A", "Push", "Heavy day")
    repo.addExercise(w, repo.saveExercise(null, "Bench", "", "", ""), 2, SetValues(5, 7, 80.0))

    val copy = repo.duplicateWorkout(w)

    val original = detail(w)
    val duplicate = detail(copy)
    assertEquals("Push A (copy)", duplicate.name)
    assertEquals("Heavy day", duplicate.notes)
    assertEquals(original.entries.single().sets.map { it.repsMin to it.weightKg }, duplicate.entries.single().sets.map { it.repsMin to it.weightKg })
    assertNotEquals(original.entries.single().id, duplicate.entries.single().id)

    // Editing the copy leaves the original alone.
    repo.addSet(duplicate.entries.single().id)
    assertEquals(2, detail(w).entries.single().sets.size)
  }

  @Test
  fun workoutInABlock_cannotBeDeleted() = runTest {
    val w = repo.createWorkout("Push A", "Push")
    val now = Instant.EPOCH
    db.trainingBlockDao().insertBlockOrIgnore(
      TrainingBlockEntity("b1", "Winter", LocalDate.of(2026, 11, 3), LocalDate.of(2026, 11, 30), "", now, now)
    )
    db.trainingBlockDao().insertBlockWorkouts(listOf(BlockWorkoutEntity("bw1", "b1", w, 0, now, now)))

    assertEquals(DeleteResult.InUse(1), repo.deleteWorkout(w))
    assertEquals(listOf("Winter"), detail(w).blockNames)
  }

  @Test
  fun deletingWorkout_removesItsExercisesButNotTheLibrary() = runTest {
    val ex = repo.saveExercise(null, "Bench", "", "", "")
    val w = repo.createWorkout("Push A", "Push")
    repo.addExercise(w, ex, 3, SetValues(8))

    assertEquals(DeleteResult.Deleted, repo.deleteWorkout(w))
    assertNull(repo.workout(w).first())
    assertEquals(0, repo.exercises().first().single().usageCount)
  }

  @Test
  fun setValues_rejectBadInput() {
    assertThrows(IllegalArgumentException::class.java) { SetValues(0) }
    assertThrows(IllegalArgumentException::class.java) { SetValues(10, 8) }
    assertThrows(IllegalArgumentException::class.java) { SetValues(8, weightKg = -1.0) }
    assertThrows(IllegalArgumentException::class.java) { SetValues(8, restSeconds = 4000) }
    assertThrows(IllegalArgumentException::class.java) { SetValues(8, weightKg = Double.NaN) }
    assertThrows(IllegalArgumentException::class.java) { SetValues(8, weightKg = Double.POSITIVE_INFINITY) }
    assertThrows(IllegalArgumentException::class.java) { SetValues(8, weightKg = SetValues.MAX_WEIGHT_KG + 1) }
  }
}
