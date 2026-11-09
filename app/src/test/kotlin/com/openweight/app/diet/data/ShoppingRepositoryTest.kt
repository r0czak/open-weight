package com.openweight.app.diet.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.openweight.app.core.database.OpenWeightDatabase
import com.openweight.app.core.model.MealSlot
import com.openweight.app.core.model.QuantityUnit
import com.openweight.app.testing.FakeTimeAndIds
import com.openweight.app.testing.inMemoryDatabase
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
class ShoppingRepositoryTest {
  private lateinit var db: OpenWeightDatabase
  private lateinit var diet: DietRepository
  private lateinit var meals: MealPlanRepository
  private lateinit var shopping: ShoppingRepository
  private lateinit var oatsRecipe: String
  private lateinit var chickenRecipe: String

  private val mon = LocalDate.of(2026, 11, 9)
  private val sun = mon.plusDays(6)

  @Before
  fun setUp() {
    db = inMemoryDatabase()
    val clock = FakeTimeAndIds()
    diet = DietRepository(db, clock)
    meals = MealPlanRepository(db, diet, clock)
    shopping = ShoppingRepository(db, clock)
    runBlocking {
      val oats = diet.saveIngredient(null, IngredientValues("Oats", "Grains & pasta", QuantityUnit.G))
      val milk = diet.saveIngredient(null, IngredientValues("Milk", "Dairy & eggs", QuantityUnit.ML))
      val chicken = diet.saveIngredient(null, IngredientValues("Chicken breast", "Meat & fish", QuantityUnit.G))
      oatsRecipe = diet.createRecipe(RecipeInfo("Oats", 1, null, "", "", ""))
      diet.addItem(oatsRecipe, oats, 80.0, QuantityUnit.G)
      diet.addItem(oatsRecipe, milk, 250.0, QuantityUnit.ML)
      chickenRecipe = diet.createRecipe(RecipeInfo("Chicken", 2, null, "", "", ""))
      diet.addItem(chickenRecipe, chicken, 400.0, QuantityUnit.G)
    }
  }

  @After fun tearDown() = db.close()

  private suspend fun items() = shopping.current().first()!!.items.associateBy { it.name }

  @Test
  fun generate_buildsTheWeek_groupedByCategory() = runTest {
    (0..6L).forEach { meals.addMeal(mon.plusDays(it), MealSlot.BREAKFAST, oatsRecipe, 1.0) }
    meals.addMeal(mon, MealSlot.DINNER, chickenRecipe, 1.0)
    meals.addMeal(sun.plusDays(1), MealSlot.DINNER, chickenRecipe, 2.0) // outside the range

    shopping.generate(mon, sun)

    val list = shopping.current().first()!!
    assertEquals(mon to sun, list.from to list.to)
    val i = items()
    assertEquals(560.0 to QuantityUnit.G, i.getValue("Oats").quantity to i.getValue("Oats").unit)
    assertEquals(1.75 to QuantityUnit.L, i.getValue("Milk").quantity to i.getValue("Milk").unit)
    assertEquals(200.0 to QuantityUnit.G, i.getValue("Chicken breast").quantity to i.getValue("Chicken breast").unit)
    assertEquals(listOf("Dairy & eggs", "Grains & pasta", "Meat & fish"), list.byCategory("Other").map { it.first })
  }

  @Test
  fun regenerate_keepsManualItems_ticks_andHandEditedAmounts() = runTest {
    meals.addMeal(mon, MealSlot.BREAKFAST, oatsRecipe, 1.0)
    meals.addMeal(mon, MealSlot.DINNER, chickenRecipe, 1.0)
    shopping.generate(mon, sun)
    shopping.setChecked(items().getValue("Oats").id, true)
    shopping.editQuantity(items().getValue("Milk").id, 1.0, QuantityUnit.L) // I'll buy a whole carton
    shopping.addManual("Coffee", null, null, "Drinks")

    // The plan changes: more oats, no more chicken.
    meals.addMeal(mon.plusDays(1), MealSlot.BREAKFAST, oatsRecipe, 1.0)
    meals.days(mon, mon).first().single().meals.filter { it.recipeName == "Chicken" }.forEach { meals.removeMeal(it.id) }
    val result = shopping.generate(mon, sun)

    val i = items()
    assertEquals(GenerateResult(added = 0, updated = 1, removed = 1, kept = 1), result)
    assertEquals(160.0, i.getValue("Oats").quantity!!, 0.0)
    assertTrue(i.getValue("Oats").checked) // still needed, so the tick stays
    assertEquals(1.0 to QuantityUnit.L, i.getValue("Milk").quantity to i.getValue("Milk").unit)
    assertTrue(i.getValue("Milk").isQuantityEdited)
    assertTrue(i.getValue("Coffee").isManual)
    assertNull(i["Chicken breast"])
  }

  @Test
  fun manualItems_andEdits_areValidated() = runTest {
    shopping.addManual("Bananas", 6.0, QuantityUnit.PCS, "Produce") // works with no list yet
    assertEquals(6.0, items().getValue("Bananas").quantity!!, 0.0)

    assertThrows(IllegalArgumentException::class.java) { runBlocking { shopping.addManual(" ", null, null, "") } }
    assertThrows(IllegalArgumentException::class.java) { runBlocking { shopping.addManual("X", 0.0, QuantityUnit.G, "") } }

    meals.addMeal(mon, MealSlot.BREAKFAST, oatsRecipe, 1.0)
    shopping.generate(mon, sun)
    // A calendar item keeps its kind of unit, so later updates still match it.
    assertThrows(IllegalArgumentException::class.java) {
      runBlocking { shopping.editQuantity(items().getValue("Oats").id, 2.0, QuantityUnit.PCS) }
    }
    assertThrows(IllegalArgumentException::class.java) {
      runBlocking { shopping.editQuantity(items().getValue("Oats").id, null, null) }
    }
    shopping.editQuantity(items().getValue("Oats").id, 0.5, QuantityUnit.KG) // same kind: fine
    assertEquals(0.5 to QuantityUnit.KG, items().getValue("Oats").let { it.quantity to it.unit })
  }

  @Test
  fun uncheckAll_andDelete() = runTest {
    shopping.addManual("Coffee", null, null, "")
    shopping.addManual("Tea", null, null, "")
    items().values.forEach { shopping.setChecked(it.id, true) }

    shopping.uncheckAll()
    shopping.deleteItem(items().getValue("Tea").id)

    assertEquals(listOf("Coffee" to false), items().values.map { it.name to it.checked })
  }

  @Test
  fun generate_rejectsBackwardsAndLongRanges() {
    assertThrows(IllegalArgumentException::class.java) { runBlocking { shopping.generate(sun, mon) } }
    assertThrows(IllegalArgumentException::class.java) { runBlocking { shopping.generate(mon, mon.plusDays(100)) } }
  }
}
