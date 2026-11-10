package com.openweight.app.workout.session

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.openweight.app.core.database.OpenWeightDatabase
import com.openweight.app.testing.FakeTimeAndIds
import com.openweight.app.testing.inMemoryDatabase
import com.openweight.app.workout.data.SetValues
import com.openweight.app.workout.data.WorkoutRepository
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SessionRepositoryTest {
  private lateinit var db: OpenWeightDatabase
  private lateinit var workouts: WorkoutRepository
  private lateinit var sessions: SessionRepository
  private lateinit var pushA: String
  private lateinit var bench: String
  private val tuesday = LocalDate.of(2026, 11, 10)

  @Before
  fun setUp() {
    db = inMemoryDatabase()
    val clock = FakeTimeAndIds() // every now() is one second later
    workouts = WorkoutRepository(db, clock)
    sessions = SessionRepository(db, clock)
    runBlocking {
      bench = workouts.saveExercise(null, "Bench press", "Chest", "Barbell", "")
      val raise = workouts.saveExercise(null, "Lateral raise", "Shoulders", "Dumbbells", "")
      pushA = workouts.createWorkout("Push A", "Push")
      workouts.addExercise(pushA, bench, 3, SetValues(8, 10, 60.0, 120))
      workouts.addExercise(pushA, raise, 2, SetValues(12, 15, null, 60))
    }
  }

  @After fun tearDown() = db.close()

  private suspend fun open() = sessions.open().first()!!

  @Test
  fun start_copiesThePlan_andSuggestsItsNumbers() = runTest {
    val id = sessions.start(pushA, tuesday)

    val s = open()
    assertEquals(id, s.id)
    assertEquals("Push A" to tuesday, s.workoutName to s.plannedDate)
    assertEquals(listOf("Bench press" to 3, "Lateral raise" to 2), s.exercises.map { it.name to it.sets.size })
    val first = s.exercises[0].sets[0]
    assertEquals(listOf(60.0, 8, 8, 10, 120, false), listOf(first.weightKg, first.reps, first.targetRepsMin, first.targetRepsMax, first.restSeconds, first.done))
    assertNull(s.exercises[1].sets[0].weightKg)
    assertEquals(first.id, s.nextSetId)
    assertNull(s.restEndsAt)
  }

  @Test
  fun oneSessionAtATime_startAgainReturnsTheOpenOne() = runTest {
    val other = workouts.createWorkout("Pull A", "").also { workouts.addExercise(it, bench, 1, SetValues(5)) }

    val first = sessions.start(pushA, null)

    assertEquals(first, sessions.start(other, null))
    assertEquals("Push A", open().workoutName)
  }

  @Test
  fun confirm_logsTheSet_startsTheRest_andMovesOn() = runTest {
    sessions.start(pushA, tuesday)
    val set = open().exercises[0].sets[0]

    sessions.confirmSet(set.id, 62.5, 9)

    val s = open()
    val done = s.exercises[0].sets[0]
    assertEquals(listOf(62.5, 9, true), listOf(done.weightKg, done.reps, done.done))
    assertEquals(done.completedAt!!.plusSeconds(120), s.restEndsAt)
    assertEquals(s.exercises[0].sets[1].id, s.nextSetId)
    assertEquals(1 to 5, s.doneCount to s.setCount)
    assertEquals(562.5, s.volumeKg, 0.0)
    // The sets to come follow the new weight.
    assertEquals(listOf(62.5, 62.5), s.exercises[0].sets.drop(1).map { it.weightKg })

    // Correcting the numbers later doesn't restart the rest; undo clears it.
    sessions.confirmSet(set.id, 62.5, 10)
    assertEquals(done.completedAt, open().exercises[0].sets[0].completedAt)
    sessions.undoSet(set.id)
    assertNull(open().restEndsAt)
    assertEquals(10, open().exercises[0].sets[0].reps) // kept as the suggestion
  }

  @Test
  fun addAndRemoveSets_changeTheSessionOnly() = runTest {
    val id = sessions.start(pushA, tuesday)

    sessions.addSet(id, 0)
    assertEquals(4, open().exercises[0].sets.size)
    sessions.removeSet(open().exercises[1].sets[1].id)
    assertEquals(1, open().exercises[1].sets.size)
    sessions.removeSet(open().exercises[1].sets[0].id) // the last set of an exercise stays
    assertEquals(1, open().exercises[1].sets.size)
    repeat(30) { sessions.addSet(id, 0) } // stops at the limit, no crash
    assertEquals(SessionRepository.MAX_SETS_PER_EXERCISE, open().exercises[0].sets.size)

    assertEquals(5, workouts.workout(pushA).first()!!.entries.sumOf { it.sets.size }) // the plan is untouched
  }

  @Test
  fun finish_needsADoneSet_thenCountsForItsDay_andFeedsNextTime() = runTest {
    val id = sessions.start(pushA, tuesday)
    assertThrows(IllegalArgumentException::class.java) { runBlocking { sessions.finish(id) } }
    val bench = open().exercises[0].sets
    sessions.confirmSet(bench[0].id, 65.0, 10)
    sessions.confirmSet(bench[1].id, 65.0, 8)

    sessions.finish(id)

    assertNull(sessions.open().first())
    assertNotNull(sessions.session(id).first()!!.finishedAt)
    assertEquals(mapOf(tuesday to listOf(DoneWorkout(id, pushA, "Push A"))), sessions.done(tuesday.minusDays(1), tuesday.plusDays(5)).first())
    // A finished session is not edited from the live screen.
    sessions.undoSet(bench[0].id)
    assertTrue(sessions.session(id).first()!!.exercises[0].sets[0].done)

    // Next time: last time's numbers per set; the third set, not done then, takes the last done one.
    sessions.start(pushA, tuesday.plusDays(3))
    assertEquals(listOf(65.0 to 10, 65.0 to 8, 65.0 to 8), open().exercises[0].sets.map { it.weightKg to it.reps })
    assertEquals(12, open().exercises[1].sets[0].reps) // never done: the plan's
  }

  @Test
  fun discard_deletesTheOpenSession_andHistorySurvivesDeletingTheWorkout() = runTest {
    val first = sessions.start(pushA, tuesday)
    sessions.confirmSet(open().exercises[0].sets[0].id, 60.0, 8)
    sessions.finish(first)
    val second = sessions.start(pushA, tuesday.plusDays(1))

    sessions.discard(second)
    assertNull(sessions.open().first())
    assertNull(sessions.session(second).first())

    workouts.deleteWorkout(pushA)
    val kept = sessions.session(first).first()!!
    assertEquals("Push A" to null, kept.workoutName to kept.workoutId)
  }

  @Test
  fun aWorkoutWithoutSets_cantBeStarted() = runTest {
    val empty = workouts.createWorkout("Empty", "")
    assertThrows(IllegalArgumentException::class.java) { runBlocking { sessions.start(empty, null) } }
  }

  @Test
  fun history_lastTime_andStepUpWeight() = runTest {
    val first = sessions.start(pushA, tuesday)
    open().exercises[0].sets.forEach { sessions.confirmSet(it.id, 60.0, 10) }
    sessions.finish(first)

    val h = sessions.history().first().single()
    assertEquals(listOf(3, 5), listOf(h.doneCount, h.setCount))
    assertEquals(1800.0, h.volumeKg, 0.0)
    val exercise = sessions.exerciseHistory(bench).first().single()
    assertEquals(first to tuesday, exercise.sessionId to exercise.date)
    assertEquals(listOf(10, 10, 10), exercise.sets.map { it.reps })

    val second = sessions.start(pushA, tuesday.plusDays(2))
    val last = sessions.lastTime(listOf(bench))
    assertEquals(62.5, Progress.lastTime(last.getValue(bench), com.openweight.app.core.model.WeightUnit.KG)!!.stepUpKg!!, 0.0)
    // "Use": the sets not done yet take the new weight; a done one keeps its own.
    sessions.confirmSet(open().exercises[0].sets[0].id, 60.0, 10)
    sessions.setWeightForRemaining(second, 0, 62.5)
    assertEquals(listOf(60.0, 62.5, 62.5), open().exercises[0].sets.map { it.weightKg })

    sessions.discard(second)
    sessions.deleteFinished(first)
    assertTrue(sessions.history().first().isEmpty())
  }
}

