package com.openweight.app.workout.blocks

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.openweight.app.core.database.OpenWeightDatabase
import com.openweight.app.testing.FakeTimeAndIds
import com.openweight.app.testing.inMemoryDatabase
import com.openweight.app.transfer.ImportService
import com.openweight.app.workout.data.DeleteResult
import com.openweight.app.workout.data.WorkoutRepository
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TrainingBlockRepositoryTest {
  private lateinit var db: OpenWeightDatabase
  private lateinit var blocks: TrainingBlockRepository
  private lateinit var workouts: WorkoutRepository
  private val nov3 = LocalDate.of(2026, 11, 3)
  private val nov30 = LocalDate.of(2026, 11, 30)

  @Before
  fun setUp() {
    db = inMemoryDatabase()
    val clock = FakeTimeAndIds()
    blocks = TrainingBlockRepository(db, clock)
    workouts = WorkoutRepository(db, clock)
  }

  @After fun tearDown() = db.close()

  private suspend fun detail(id: String) = blocks.block(id).first()!!

  @Test
  fun newBlock_isAllRest() = runTest {
    val id = blocks.createBlock("Intro", nov3, nov30)

    val d = detail(id)
    assertEquals(DayOfWeek.entries.toList(), d.schedule.keys.toList())
    assertTrue(d.schedule.values.all { it == null })
    assertEquals(4, d.totalWeeks)
  }

  @Test
  fun blockMustEndOnOrAfterItsStart() {
    assertThrows(IllegalArgumentException::class.java) {
      runBlocking { blocks.createBlock("Backwards", nov30, nov3) }
    }
    assertThrows(IllegalArgumentException::class.java) { runBlocking { blocks.createBlock(" ", nov3, nov30) } }
  }

  @Test
  fun atMostTenWorkouts_andDuplicatesAreSkipped() = runTest {
    val id = blocks.createBlock("Big", nov3, nov30)
    val ids = (1..11).map { workouts.createWorkout("W$it", "") }

    blocks.addWorkouts(id, ids.take(9))
    blocks.addWorkouts(id, listOf(ids[0], ids[9])) // W1 already in: only W10 is added
    assertEquals(10, detail(id).workouts.size)

    assertThrows(TooManyWorkoutsException::class.java) { runBlocking { blocks.addWorkouts(id, listOf(ids[10])) } }
    assertEquals((1..10).map { "W$it" }, detail(id).workouts.map { it.name })
  }

  @Test
  fun addingSeveralAtOnce_isAllOrNothing() = runTest {
    val id = blocks.createBlock("Big", nov3, nov30)
    val ids = (1..12).map { workouts.createWorkout("W$it", "") }
    blocks.addWorkouts(id, ids.take(8))

    assertThrows(TooManyWorkoutsException::class.java) { runBlocking { blocks.addWorkouts(id, ids.drop(8)) } }
    assertEquals(8, detail(id).workouts.size)
  }

  @Test
  fun onlyWorkoutsInTheBlockCanBeScheduled() = runTest {
    val id = blocks.createBlock("Intro", nov3, nov30)
    val inBlock = workouts.createWorkout("Push A", "")
    val outside = workouts.createWorkout("Pull A", "")
    blocks.addWorkouts(id, listOf(inBlock))

    blocks.setDay(id, DayOfWeek.TUESDAY, inBlock)
    assertThrows(IllegalArgumentException::class.java) { runBlocking { blocks.setDay(id, DayOfWeek.WEDNESDAY, outside) } }

    assertEquals(inBlock, detail(id).schedule[DayOfWeek.TUESDAY])
    assertNull(detail(id).schedule[DayOfWeek.WEDNESDAY])
    blocks.setDay(id, DayOfWeek.TUESDAY, null)
    assertNull(detail(id).schedule[DayOfWeek.TUESDAY])
  }

  @Test
  fun removingAWorkout_turnsItsDaysIntoRest() = runTest {
    val id = blocks.createBlock("Intro", nov3, nov30)
    val push = workouts.createWorkout("Push A", "")
    val pull = workouts.createWorkout("Pull A", "")
    blocks.addWorkouts(id, listOf(push, pull))
    blocks.setDay(id, DayOfWeek.TUESDAY, push)
    blocks.setDay(id, DayOfWeek.FRIDAY, push)
    blocks.setDay(id, DayOfWeek.WEDNESDAY, pull)

    assertEquals(2, blocks.scheduledDayCount(id, push))
    blocks.removeWorkout(id, push)

    val d = detail(id)
    assertEquals(listOf("Pull A"), d.workouts.map { it.name })
    assertEquals(mapOf(DayOfWeek.WEDNESDAY to pull), d.schedule.filterValues { it != null })
    // And the workout can now be deleted from the library.
    assertEquals(DeleteResult.Deleted, workouts.deleteWorkout(push))
  }

  @Test
  fun overlapsAreReported_bothWays() = runTest {
    val a = blocks.createBlock("A", nov3, nov30)
    blocks.createBlock("B", nov30, nov30.plusWeeks(4))
    blocks.createBlock("C", nov30.plusDays(1), nov30.plusWeeks(2))

    assertEquals(listOf("B"), detail(a).overlapsWith)
    assertEquals(listOf("A", "B", "C"), blocks.overlapsFor(null, nov30, nov30.plusDays(1)).sorted())
    assertEquals(listOf("B"), blocks.overlapsFor(null, nov30.plusWeeks(3), nov30.plusWeeks(5)))
  }

  @Test
  fun editingDates_keepsWorkoutsAndSchedule() = runTest {
    val id = blocks.createBlock("Intro", nov3, nov30)
    val push = workouts.createWorkout("Push A", "")
    blocks.addWorkouts(id, listOf(push))
    blocks.setDay(id, DayOfWeek.TUESDAY, push)

    blocks.updateBlockInfo(id, "Intro (longer)", nov3, nov30.plusWeeks(2), "Ramp up")

    val d = detail(id)
    assertEquals("Intro (longer)", d.name)
    assertEquals(6, d.totalWeeks)
    assertEquals("Ramp up", d.notes)
    assertEquals(push, d.schedule[DayOfWeek.TUESDAY])
  }

  @Test
  fun deletingABlock_keepsItsWorkouts() = runTest {
    val id = blocks.createBlock("Intro", nov3, nov30)
    val push = workouts.createWorkout("Push A", "")
    blocks.addWorkouts(id, listOf(push))

    blocks.deleteBlock(id)

    assertNull(blocks.block(id).first())
    assertEquals(listOf("Push A"), workouts.workouts().first().map { it.name })
  }

  @Test
  fun winterArcPlan_resolvesToTheRightWorkouts() = runTest {
    ImportService(db, FakeTimeAndIds()).import(File("../plans/winter-arc.openweight.json").readBytes())
    val scheduled = blocks.scheduledBlocks().first()
    val names = workouts.workouts().first().associate { it.id to it.name }

    fun on(date: String): String =
      when (val d = ScheduleResolver.plannedDay(scheduled, LocalDate.parse(date))) {
        is PlannedDay.NoBlock -> "no block"
        is PlannedDay.InBlock -> "${d.blockName} w${d.week}/${d.totalWeeks}: ${d.workoutId?.let(names::get) ?: "rest"}"
      }

    assertEquals("no block", on("2026-11-02"))
    assertEquals("Winter Arc 1 · Intro w1/4: Push A · Intro", on("2026-11-03"))
    assertEquals("Winter Arc 1 · Intro w2/4: rest", on("2026-11-12"))
    assertEquals("Winter Arc 2 · Core 1 w4/12: Pull A · Core 1 (heavy)", on("2026-12-23"))
    assertEquals("Winter Arc 3 · Core 2 · M2 w1/6: Push B · Core 2 · Option 2", on("2027-04-09"))
    assertEquals("Winter Arc 3 · Core 2 · M3 w1/6: Pull B · Core 2 · Option 1", on("2027-05-22"))
    assertEquals("Winter Arc 3 · Core 2 · M4 + test w8/8: rest", on("2027-08-23"))
    assertEquals("no block", on("2027-08-24"))
  }
}
