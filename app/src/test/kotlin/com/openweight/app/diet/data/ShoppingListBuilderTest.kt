package com.openweight.app.diet.data

import com.openweight.app.core.model.QuantityUnit
import com.openweight.app.core.model.QuantityUnit.Dimension
import org.junit.Assert.assertEquals
import org.junit.Test

class ShoppingListBuilderTest {
  private fun line(id: String, q: Double, u: QuantityUnit, category: String = "") = RecipeLine(id, id.replaceFirstChar { it.uppercase() }, category, q, u)

  @Test
  fun sumsAcrossMeals_scaledByServings() {
    // Oats recipe makes 1 serving with 80 g; eaten 7 times. Chicken & rice makes 2 servings; eaten 1.5 each, 4 times.
    val oats = MealForShopping(1, 1.0, listOf(line("oats", 80.0, QuantityUnit.G), line("milk", 250.0, QuantityUnit.ML)))
    val chicken = MealForShopping(2, 1.5, listOf(line("chicken", 400.0, QuantityUnit.G), line("rice", 200.0, QuantityUnit.G)))

    val items = ShoppingListBuilder.build(List(7) { oats } + List(4) { chicken })

    assertEquals(
      listOf("oats" to (560.0 to QuantityUnit.G), "milk" to (1.75 to QuantityUnit.L), "chicken" to (1.2 to QuantityUnit.KG), "rice" to (600.0 to QuantityUnit.G)),
      items.map { it.ingredientId to (it.quantity to it.unit) },
    )
  }

  @Test
  fun unitsOfOneKind_addUp_otherKindsStaySeparate() {
    val meal =
      MealForShopping(
        1,
        1.0,
        listOf(
          line("oil", 2.0, QuantityUnit.TBSP), // 30 ml
          line("oil", 1.0, QuantityUnit.TSP), // 5 ml
          line("oil", 0.1, QuantityUnit.L), // 100 ml
          line("flour", 0.5, QuantityUnit.KG),
          line("flour", 250.0, QuantityUnit.G),
          line("egg", 2.0, QuantityUnit.PCS),
          line("egg", 50.0, QuantityUnit.G), // weighed egg: a separate line
        ),
      )

    val items = ShoppingListBuilder.build(listOf(meal)).associate { (it.ingredientId to it.dimension) to (it.quantity to it.unit) }

    assertEquals(135.0 to QuantityUnit.ML, items["oil" to Dimension.VOLUME])
    assertEquals(750.0 to QuantityUnit.G, items["flour" to Dimension.MASS])
    assertEquals(2.0 to QuantityUnit.PCS, items["egg" to Dimension.COUNT])
    assertEquals(50.0 to QuantityUnit.G, items["egg" to Dimension.MASS])
  }

  @Test
  fun roundsUp_forTheShop_withoutFloatNoise() {
    assertEquals(3.0 to QuantityUnit.PCS, ShoppingListBuilder.displayAmount(2.25, Dimension.COUNT))
    assertEquals(201.0 to QuantityUnit.G, ShoppingListBuilder.displayAmount(200.2, Dimension.MASS))
    assertEquals(200.0 to QuantityUnit.G, ShoppingListBuilder.displayAmount(199.99999999, Dimension.MASS))
    assertEquals(1.01 to QuantityUnit.KG, ShoppingListBuilder.displayAmount(1003.0, Dimension.MASS))
    assertEquals(1.0 to QuantityUnit.L, ShoppingListBuilder.displayAmount(1000.0, Dimension.VOLUME))
  }
}
