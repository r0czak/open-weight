package com.openweight.app.diet.data

import com.openweight.app.core.model.QuantityUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NutritionCalculatorTest {
  // Real-world label values, per 100 g / 100 ml.
  private val oats = IngredientNutrition(Macros(379.0, 13.2, 67.7, 6.5), QuantityUnit.G, null)
  private val milk = IngredientNutrition(Macros(47.0, 3.4, 4.8, 1.5), QuantityUnit.ML, null)
  private val egg = IngredientNutrition(Macros(143.0, 12.6, 0.7, 9.5), QuantityUnit.PCS, gramsPerPiece = 60.0)
  private val eggNoWeight = egg.copy(gramsPerPiece = null)
  private val salt = IngredientNutrition(null, QuantityUnit.G, null)

  private fun close(expected: Macros, actual: Macros?) {
    requireNotNull(actual)
    assertEquals(expected.kcal, actual.kcal, 0.01)
    assertEquals(expected.protein, actual.protein, 0.01)
    assertEquals(expected.carbs, actual.carbs, 0.01)
    assertEquals(expected.fat, actual.fat, 0.01)
  }

  @Test
  fun massAndVolume_scaleFrom100() {
    close(Macros(303.2, 10.56, 54.16, 5.2), NutritionCalculator.forAmount(80.0, QuantityUnit.G, oats))
    close(Macros(379.0, 13.2, 67.7, 6.5), NutritionCalculator.forAmount(0.1, QuantityUnit.KG, oats))
    close(Macros(94.0, 6.8, 9.6, 3.0), NutritionCalculator.forAmount(200.0, QuantityUnit.ML, milk))
    close(Macros(117.5, 8.5, 12.0, 3.75), NutritionCalculator.forAmount(0.25, QuantityUnit.L, milk))
    // 2 tbsp = 30 ml
    close(Macros(14.1, 1.02, 1.44, 0.45), NutritionCalculator.forAmount(2.0, QuantityUnit.TBSP, milk))
  }

  @Test
  fun pieces_needGramsPerPiece() {
    close(Macros(171.6, 15.12, 0.84, 11.4), NutritionCalculator.forAmount(2.0, QuantityUnit.PCS, egg))
    assertNull(NutritionCalculator.forAmount(2.0, QuantityUnit.PCS, eggNoWeight))
  }

  @Test
  fun noValues_meansNotCounted() {
    assertNull(NutritionCalculator.forAmount(5.0, QuantityUnit.G, salt))
  }

  @Test
  fun recipe_totalsPerServing_andListsMissing() {
    val n =
      NutritionCalculator.forRecipe(
        listOf(
          "Oats" to Triple(80.0, QuantityUnit.G, oats),
          "Milk" to Triple(200.0, QuantityUnit.ML, milk),
          "Egg" to Triple(2.0, QuantityUnit.PCS, egg),
          "Salt" to Triple(1.0, QuantityUnit.G, salt),
        ),
        servings = 2,
      )

    close(Macros(568.8, 32.48, 64.6, 19.6), n.total)
    close(Macros(284.4, 16.24, 32.3, 9.8), n.perServing)
    assertEquals(listOf("Salt"), n.missing)
  }
}
