package com.openweight.app.transfer

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.openweight.app.core.TimeAndIds
import com.openweight.app.core.database.OpenWeightDatabase
import com.openweight.app.core.model.MealSlot
import com.openweight.app.core.model.QuantityUnit
import com.openweight.app.diet.data.DietRepository
import com.openweight.app.diet.data.IngredientValues
import com.openweight.app.diet.data.MealPlanRepository
import com.openweight.app.diet.data.RecipeInfo
import com.openweight.app.diet.data.ShoppingRepository
import com.openweight.app.testing.FakeTimeAndIds
import com.openweight.app.testing.inMemoryDatabase
import com.openweight.app.workout.blocks.TrainingBlockRepository
import com.openweight.app.workout.data.SetValues
import com.openweight.app.workout.data.WorkoutRepository
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

/** Export scopes, the round trip, and the import's conflict choices. */
@RunWith(AndroidJUnit4::class)
class TransferTest {
  /** A phone: its own database, clock and services. Ids carry a prefix so two phones never share one by chance. */
  private class Phone(prefix: String) {
    val db: OpenWeightDatabase = inMemoryDatabase()
    val clock =
      object : TimeAndIds by FakeTimeAndIds() {
        private var n = 0

        override fun newId() = "$prefix-${++n}"
      }
    val workouts = WorkoutRepository(db, clock)
    val blocks = TrainingBlockRepository(db, clock)
    val diet = DietRepository(db, clock)
    val meals = MealPlanRepository(db, diet, clock)
    val shopping = ShoppingRepository(db, clock)
    val exporter = ExportService(db, clock) { "0.7.0" }
    val importer = ImportService(db, clock)

    suspend fun export(request: ExportRequest) = exporter.export(request, SettingsDto("LB", "DARK"))

    suspend fun import(file: ExportedFile, options: ImportOptions = ImportOptions()) = importer.import(importer.parse(file.bytes), options)
  }

  private lateinit var a: Phone
  private lateinit var b: Phone
  private val monday = LocalDate.of(2026, 11, 9)

  // Ids of the sample data on phone A.
  private lateinit var bench: String
  private lateinit var pushA: String
  private lateinit var pullA: String
  private lateinit var block: String
  private lateinit var oats: String
  private lateinit var milk: String
  private lateinit var porridge: String
  private lateinit var omelette: String
  private lateinit var plan: String

  @Before
  fun setUp() {
    a = Phone("a")
    b = Phone("b")
    runBlocking {
      with(a) {
        bench = workouts.saveExercise(null, "Bench press", "Chest", "Barbell", "")
        val row = workouts.saveExercise(null, "Barbell row", "Back", "Barbell", "")
        workouts.saveExercise(null, "Unused curl", "Arms", "", "")
        pushA = workouts.createWorkout("Push A", "Push")
        workouts.addExercise(pushA, bench, 3, SetValues(8, 10, 60.0, 90))
        pullA = workouts.createWorkout("Pull A", "Pull")
        workouts.addExercise(pullA, row, 2, SetValues(10, null, null, 120))
        workouts.createWorkout("Not in a block", "")
        block = blocks.createBlock("Winter", monday, monday.plusWeeks(8))
        blocks.addWorkouts(block, listOf(pushA, pullA))
        blocks.setDay(block, DayOfWeek.TUESDAY, pushA)
        blocks.setDay(block, DayOfWeek.FRIDAY, pullA)

        oats = diet.saveIngredient(null, IngredientValues("Oats", "Grains", QuantityUnit.G, 379.0, 13.0, 68.0, 6.5))
        milk = diet.saveIngredient(null, IngredientValues("Milk", "Dairy", QuantityUnit.ML, 50.0))
        val eggs = diet.saveIngredient(null, IngredientValues("Eggs", "Dairy", QuantityUnit.PCS, 143.0, gramsPerPiece = 55.0))
        diet.saveIngredient(null, IngredientValues("Unused salt", "", QuantityUnit.G))
        porridge = diet.createRecipe(RecipeInfo("Porridge", 1, 5, "Cook.", "breakfast", ""))
        diet.addItem(porridge, oats, 80.0, QuantityUnit.G)
        diet.addItem(porridge, milk, 0.25, QuantityUnit.L)
        omelette = diet.createRecipe(RecipeInfo("Omelette", 1, 10, "", "", ""))
        diet.addItem(omelette, eggs, 3.0, QuantityUnit.PCS)
        plan = meals.createPlan("Lean week")
        meals.addPlanMeal(plan, DayOfWeek.MONDAY, MealSlot.BREAKFAST, porridge, 1.0)
        meals.addMeal(monday, MealSlot.BREAKFAST, porridge, 1.0)
        meals.addMeal(monday.plusDays(1), MealSlot.DINNER, omelette, 2.0)
        meals.addMeal(monday.plusDays(10), MealSlot.BREAKFAST, omelette, 1.0)
        shopping.generate(monday, monday.plusDays(6))
        shopping.addManual("Coffee", null, null, "Drinks")
      }
    }
  }

  @After
  fun tearDown() {
    a.db.close()
    b.db.close()
  }

  @Test
  fun fullBackup_restoresEverything_onAnEmptyPhone() = runTest {
    val backup = a.export(ExportRequest.Everything)
    assertEquals("openweight-backup-2026-11-08.json", backup.fileName)

    val result = b.import(backup)

    assertEquals(SettingsDto("LB", "DARK"), result.settings)
    assertEquals(3, result[RecordKind.EXERCISE].added)
    assertEquals(4, result[RecordKind.INGREDIENT].added)
    assertEquals(3, result[RecordKind.MEAL].added)
    // Exporting the copy gives the same data back: nothing was lost or changed on the way.
    assertEquals(backup.file.data, b.export(ExportRequest.Everything).file.data)
    val coffee = b.shopping.current().first()!!.items.single { it.name == "Coffee" }
    assertTrue(coffee.isManual)
  }

  @Test
  fun pickedItems_bringWhatTheyNeed_andNothingElse() = runTest {
    val file = a.export(ExportRequest.Items(blockIds = setOf(block), dietPlanIds = setOf(plan))).file

    assertEquals(ScopeKind.ITEMS.id, file.scope?.kind)
    assertEquals(listOf("Winter"), file.data.trainingBlocks.map { it.name })
    assertEquals(setOf("Push A", "Pull A"), file.data.workouts.map { it.name }.toSet())
    assertEquals(setOf("Bench press", "Barbell row"), file.data.exercises.map { it.name }.toSet())
    assertEquals(listOf("Lean week"), file.data.dietPlans.map { it.name })
    assertEquals(listOf("Porridge"), file.data.recipes.map { it.name })
    assertEquals(setOf("Oats", "Milk"), file.data.ingredients.map { it.name }.toSet())
    assertTrue(file.data.mealEntries.isEmpty() && file.data.shoppingLists.isEmpty() && file.data.settings == null)
    // Recipe lines keep the unit the user entered.
    assertEquals(listOf(80.0 to "G", 0.25 to "L"), file.data.recipes.single().ingredients.map { it.quantity to it.unit })

    val single = a.export(ExportRequest.Items(recipeIds = setOf(porridge)))
    assertEquals("openweight-recipe-porridge.json", single.fileName)
    assertEquals("Recipe: Porridge", single.file.scope?.description)

    // It imports cleanly on an empty phone.
    b.import(single)
    assertEquals(listOf("Porridge"), b.diet.recipes().first().map { it.name })
  }

  @Test
  fun range_exportsTheMealsInIt_withTheirRecipes() = runTest {
    val file = a.export(ExportRequest.Range(monday, monday.plusDays(6))).file

    assertEquals(listOf("2026-11-09", "2026-11-10"), file.data.mealEntries.map { it.date })
    assertEquals(setOf("Porridge", "Omelette"), file.data.recipes.map { it.name }.toSet())
    assertEquals(setOf("Oats", "Milk", "Eggs"), file.data.ingredients.map { it.name }.toSet())
    assertTrue(file.data.dietPlans.isEmpty())
  }

  @Test
  fun preview_listsNewAndMatching_byIdOrName() = runTest {
    b.workouts.saveExercise(null, "BENCH PRESS", "", "", "") // same name, different id
    val file = a.export(ExportRequest.Items(workoutIds = setOf(pushA))).file
    b.importer.import(a.importer.parse(a.export(ExportRequest.Items(recipeIds = setOf(porridge))).bytes))

    val preview = b.importer.preview(file)
    assertEquals(mapOf(RecordKind.WORKOUT to KindCount(1, 0), RecordKind.EXERCISE to KindCount(1, 1)), preview.counts)
    assertEquals(listOf(true), preview.matches.map { it.byName })

    val again = b.importer.preview(a.export(ExportRequest.Items(recipeIds = setOf(porridge))).file)
    assertEquals(KindCount(1, 1), again.counts[RecordKind.RECIPE])
    assertEquals(KindCount(2, 2), again.counts[RecordKind.INGREDIENT])
  }

  @Test
  fun conflicts_skipReplaceNewerAndKeepBoth() = runTest {
    b.import(a.export(ExportRequest.WorkoutData))
    // Phone A renames a workout and an exercise later on.
    a.workouts.updateWorkoutInfo(pushA, "Push A (v2)", "Push", "")
    a.workouts.saveExercise(bench, "Bench press (flat)", "Chest", "Barbell", "")
    val file = a.export(ExportRequest.Items(blockIds = setOf(block)))

    // Skip (default): nothing changes.
    assertTrue(b.import(file).nothingChanged)
    assertTrue(b.workouts.workouts().first().any { it.name == "Push A" })

    // Newer wins: A's copies are newer, so they replace; the untouched ones stay.
    val newer = b.import(file, ImportOptions(choice = ConflictChoice.NEWER_WINS))
    assertEquals(KindResult(replaced = 1, skipped = 1), newer[RecordKind.WORKOUT])
    assertEquals(setOf("Push A (v2)", "Pull A", "Not in a block"), b.workouts.workouts().first().map { it.name }.toSet())
    assertTrue(b.workouts.exercises().first().any { it.name == "Bench press (flat)" })

    // Keep both, except the block, which is replaced: it then points at the copies.
    val both =
      b.import(file, ImportOptions(choice = ConflictChoice.KEEP_BOTH, overrides = mapOf(ImportMatch.key(RecordKind.TRAINING_BLOCK, block) to ConflictChoice.REPLACE)))
    assertEquals(KindResult(copied = 2), both[RecordKind.WORKOUT])
    assertEquals(KindResult(replaced = 1), both[RecordKind.TRAINING_BLOCK])
    val blockWorkouts = b.blocks.block(block).first()!!.workouts.map { it.name }
    assertEquals(listOf("Push A (v2) (imported)", "Pull A (imported)"), blockWorkouts)
    // Copied exercises get names that are still unique.
    b.import(file, ImportOptions(choice = ConflictChoice.KEEP_BOTH))
    assertTrue(b.workouts.exercises().first().map { it.name }.containsAll(listOf("Bench press (flat) (imported)", "Bench press (flat) (imported 2)")))
  }

  @Test
  fun sameNameExercise_isReusedByTheImportedWorkout() = runTest {
    val mine = b.workouts.saveExercise(null, "bench press", "", "", "")

    b.import(a.export(ExportRequest.Items(workoutIds = setOf(pushA))))

    val imported = b.workouts.workouts().first().single()
    assertEquals(mine, b.workouts.workout(imported.id).first()!!.entries.single().exerciseId)
    assertEquals(1, b.workouts.exercises().first().size)
  }

  @Test
  fun replaceEverything_clearsThePhoneFirst() = runTest {
    b.workouts.createWorkout("Only on B", "")
    b.import(a.export(ExportRequest.Items(recipeIds = setOf(omelette))))

    val result = b.import(a.export(ExportRequest.Everything), ImportOptions(replaceEverything = true))

    assertEquals(3, result[RecordKind.WORKOUT].added)
    assertTrue(b.workouts.workouts().first().none { it.name == "Only on B" })
    assertEquals(2, b.diet.recipes().first().size)
  }

  @Test
  fun replaceEverything_needsEveryLinkInTheFile() = runTest {
    b.import(a.export(ExportRequest.Items(recipeIds = setOf(porridge))))
    // A meal whose recipe is only on phone B: fine to merge, not to replace everything with.
    val file = a.export(ExportRequest.Range(monday, monday)).file.let { f -> f.copy(data = f.data.copy(recipes = emptyList(), ingredients = emptyList())) }

    assertThrows(ImportException::class.java) { runBlocking { b.importer.import(file, ImportOptions(replaceEverything = true)) } }
    assertEquals(1, b.diet.recipes().first().size) // nothing was cleared
    b.importer.import(file)
    assertEquals(1, b.meals.days(monday, monday).first().single().meals.size)
  }

  @Test
  fun optionalLinks_toMissingRecords_areDropped() = runTest {
    a.meals.applyPlan(plan, monday.plusWeeks(1), monday.plusWeeks(1), com.openweight.app.diet.data.ApplyMode.REPLACE)
    val range = a.export(ExportRequest.Range(monday.plusWeeks(1), monday.plusWeeks(1))).file
    assertTrue(range.data.mealEntries.single().dietPlanId != null)

    b.importer.import(range)

    b.db.openHelper.readableDatabase.query("SELECT dietPlanId FROM meal_entry").use {
      it.moveToFirst()
      assertNull(it.getString(0))
    }
  }

  @Test
  fun badDietRecords_areRefused() = runTest {
    fun file(data: String) = """{"format":"open-weight","formatVersion":1,"data":$data}""".toByteArray()
    val cases =
      mapOf(
        """{"ingredients":[{"id":"i","name":"Oats","defaultUnit":"CUP"}]}""" to "unknown unit",
        """{"ingredients":[{"id":"i","name":"Oats","kcalPer100":-1}]}""" to "can't be negative",
        """{"recipes":[{"id":"r","name":"R","ingredients":[{"ingredientId":"nope","quantity":1,"unit":"G"}]}]}""" to "isn't in the file",
        """{"recipes":[{"id":"r","name":"R","servings":0}]}""" to "Servings",
        """{"recipes":[{"id":"r","name":"R"}],"mealEntries":[{"id":"m","date":"2026-11-09","slot":"BRUNCH","recipeId":"r"}]}""" to "unknown meal",
        """{"recipes":[{"id":"r","name":"R"}],"mealEntries":[{"id":"m","date":"9/11","slot":"LUNCH","recipeId":"r"}]}""" to "invalid date",
        """{"ingredients":[{"id":"i","name":"Oats"},{"id":"j","name":"oats"}]}""" to "same ingredient twice",
      )
    for ((json, expected) in cases) {
      val e = assertThrows(ImportException::class.java) { runBlocking { b.importer.import(file(json)) } }
      assertTrue("\"${e.message}\" should mention \"$expected\"", e.message!!.contains(expected))
    }
    assertTrue(b.diet.recipes().first().isEmpty())
  }

  @Test
  fun slug_makesSafeFileNames() {
    assertEquals("push-a-intro", ExportService.slug("Push A · Intro"))
    assertEquals("zupa-pomidorowa", ExportService.slug("Zupa pomidorowa!"))
    assertEquals("", ExportService.slug("···"))
  }
}
