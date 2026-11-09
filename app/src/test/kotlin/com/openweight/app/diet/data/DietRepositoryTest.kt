package com.openweight.app.diet.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.openweight.app.core.database.OpenWeightDatabase
import com.openweight.app.core.model.QuantityUnit
import com.openweight.app.testing.FakeTimeAndIds
import com.openweight.app.testing.inMemoryDatabase
import com.openweight.app.workout.data.DeleteResult
import com.openweight.app.workout.data.DuplicateNameException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DietRepositoryTest {
  private lateinit var db: OpenWeightDatabase
  private lateinit var repo: DietRepository

  @Before
  fun setUp() {
    db = inMemoryDatabase()
    repo = DietRepository(db, FakeTimeAndIds())
  }

  @After fun tearDown() = db.close()

  private suspend fun oats() =
    repo.saveIngredient(null, IngredientValues("Oats", "Grains & pasta", QuantityUnit.G, 379.0, 13.2, 67.7, 6.5))

  private suspend fun milk() = repo.saveIngredient(null, IngredientValues("Milk", "Dairy & eggs", QuantityUnit.ML, 47.0, 3.4, 4.8, 1.5))

  private fun info(name: String = "Overnight oats", servings: Int = 1) = RecipeInfo(name, servings, 5, "Mix.\nRest overnight.", "breakfast", "")

  @Test
  fun ingredientNames_areUniqueIgnoringCase() = runTest {
    oats()
    assertThrows(DuplicateNameException::class.java) {
      runBlocking { repo.saveIngredient(null, IngredientValues(" OATS ", "", QuantityUnit.G)) }
    }
  }

  @Test
  fun ingredientValues_rejectNegativesAndZeroPieceWeight() {
    assertThrows(IllegalArgumentException::class.java) { IngredientValues("X", "", QuantityUnit.G, kcalPer100 = -1.0) }
    assertThrows(IllegalArgumentException::class.java) { IngredientValues("Egg", "", QuantityUnit.PCS, gramsPerPiece = 0.0) }
  }

  @Test
  fun recipe_computesMacrosPerServing_andUpdatesLive() = runTest {
    val r = repo.createRecipe(info(servings = 2))
    repo.addItem(r, oats(), 80.0, QuantityUnit.G)
    repo.addItem(r, milk(), 200.0, QuantityUnit.ML)

    val d = repo.recipe(r).first()!!
    assertEquals(listOf("Oats", "Milk"), d.items.map { it.name })
    assertEquals(397.2 / 2, d.nutrition.perServing.kcal, 0.01)
    assertTrue(d.nutrition.complete)

    repo.updateRecipeInfo(r, info(servings = 1))
    assertEquals(397.2, repo.recipe(r).first()!!.nutrition.perServing.kcal, 0.01)
    assertEquals(397.2, repo.recipes().first().single().nutrition.perServing.kcal, 0.01)
  }

  @Test
  fun itemsMoveAndRemove_keepingOrder() = runTest {
    val r = repo.createRecipe(info())
    val a = repo.addItem(r, oats(), 80.0, QuantityUnit.G)
    repo.addItem(r, milk(), 200.0, QuantityUnit.ML)
    val salt = repo.addItem(r, repo.saveIngredient(null, IngredientValues("Salt", "", QuantityUnit.G)), 1.0, QuantityUnit.G)

    repo.moveItem(salt, -2)
    assertEquals(listOf("Salt", "Oats", "Milk"), repo.recipe(r).first()!!.items.map { it.name })
    repo.removeItem(a)
    repo.updateItem(salt, 2.0, QuantityUnit.G)

    val items = repo.recipe(r).first()!!.items
    assertEquals(listOf("Salt" to 2.0, "Milk" to 200.0), items.map { it.name to it.quantity })
    assertEquals(listOf("Salt"), repo.recipe(r).first()!!.nutrition.missing)
  }

  @Test
  fun quantitiesAndInfo_areValidated() = runTest {
    val r = repo.createRecipe(info())
    val oats = oats()
    assertThrows(IllegalArgumentException::class.java) { runBlocking { repo.addItem(r, oats, 0.0, QuantityUnit.G) } }
    assertThrows(IllegalArgumentException::class.java) { runBlocking { repo.addItem(r, oats, 5000.0, QuantityUnit.PCS) } }
    assertThrows(IllegalArgumentException::class.java) { info(servings = 0) }
    assertThrows(IllegalArgumentException::class.java) { info(name = " ") }
  }

  @Test
  fun ingredientInARecipe_cannotBeDeleted() = runTest {
    val r = repo.createRecipe(info())
    val oats = oats()
    val item = repo.addItem(r, oats, 80.0, QuantityUnit.G)

    assertEquals(DeleteResult.InUse(1), repo.deleteIngredient(oats))
    repo.removeItem(item)
    assertEquals(DeleteResult.Deleted, repo.deleteIngredient(oats))
  }

  @Test
  fun duplicateRecipe_copiesItems() = runTest {
    val r = repo.createRecipe(info())
    repo.addItem(r, oats(), 80.0, QuantityUnit.G)

    val copy = repo.duplicateRecipe(r)

    val d = repo.recipe(copy).first()!!
    assertEquals("Overnight oats (copy)", d.name)
    assertEquals(listOf("Oats" to 80.0), d.items.map { it.name to it.quantity })
    assertNotEquals(repo.recipe(r).first()!!.items.single().id, d.items.single().id)
  }

  @Test
  fun recipeOnTheCalendar_cannotBeDeleted() = runTest {
    val r = repo.createRecipe(info())
    db.openHelper.writableDatabase.execSQL(
      "INSERT INTO meal_entry (id, date, slot, recipeId, servings, dietPlanId, createdAt, updatedAt) " +
        "VALUES ('m1', '2026-11-10', 'BREAKFAST', '$r', 1.0, NULL, 0, 0)"
    )

    assertEquals(DeleteResult.InUse(1), repo.deleteRecipe(r))
    db.openHelper.writableDatabase.execSQL("DELETE FROM meal_entry")
    assertEquals(DeleteResult.Deleted, repo.deleteRecipe(r))
    assertNull(repo.recipe(r).first())
  }

  @Test
  fun shopCategories_putUsedOnesFirst_withoutDuplicates() = runTest {
    repo.saveIngredient(null, IngredientValues("Tofu", "Asian shop", QuantityUnit.G))
    repo.saveIngredient(null, IngredientValues("Milk", "dairy & eggs", QuantityUnit.ML))

    val categories = repo.shopCategories().first()
    assertEquals(listOf("Asian shop", "dairy & eggs"), categories.take(2))
    assertEquals(1, categories.count { it.equals("Dairy & eggs", ignoreCase = true) })
  }
}
