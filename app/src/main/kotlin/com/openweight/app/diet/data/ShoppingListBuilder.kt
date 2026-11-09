package com.openweight.app.diet.data

import com.openweight.app.core.model.QuantityUnit
import com.openweight.app.core.model.QuantityUnit.Dimension
import kotlin.math.ceil

/** One ingredient line of a recipe, as the shopping rules need it. */
data class RecipeLine(
  val ingredientId: String,
  val name: String,
  val category: String,
  val quantity: Double,
  val unit: QuantityUnit,
)

/** A planned meal: a recipe's lines, how many servings the recipe makes, and how many are eaten. */
data class MealForShopping(val recipeServings: Int, val servings: Double, val lines: List<RecipeLine>)

/** What to buy of one ingredient, in one dimension (mass, volume or pieces). */
data class NeededItem(
  val ingredientId: String,
  val name: String,
  val category: String,
  val quantity: Double,
  val unit: QuantityUnit,
) {
  val dimension: Dimension
    get() = unit.dimension
}

/**
 * The shopping rule: every meal scales its recipe's lines by servings eaten ÷ servings the recipe
 * makes; amounts are summed per ingredient within one dimension (kg + g, l + ml + tsp + tbsp,
 * pieces), then shown in a shop-friendly unit: whole grams or millilitres rounded up, kg or l from
 * 1,000, whole pieces rounded up.
 */
object ShoppingListBuilder {
  fun build(meals: List<MealForShopping>): List<NeededItem> {
    data class Key(val ingredientId: String, val dimension: Dimension)
    val totals = linkedMapOf<Key, Pair<RecipeLine, Double>>()
    for (meal in meals) {
      val factor = meal.servings / meal.recipeServings.coerceAtLeast(1)
      for (line in meal.lines) {
        val key = Key(line.ingredientId, line.unit.dimension)
        val base = line.quantity * line.unit.toBase * factor
        totals[key] = line to ((totals[key]?.second ?: 0.0) + base)
      }
    }
    return totals.map { (key, value) ->
      val (line, base) = value
      val (quantity, unit) = displayAmount(base, key.dimension)
      NeededItem(line.ingredientId, line.name, line.category, quantity, unit)
    }
  }

  /** [base] is grams, millilitres or pieces. */
  fun displayAmount(base: Double, dimension: Dimension): Pair<Double, QuantityUnit> {
    // Tiny float noise (e.g. 199.99999) must not round up to an extra gram.
    val clean = Math.round(base * 1000) / 1000.0
    return when (dimension) {
      Dimension.MASS -> if (clean >= 1000) roundUp(clean / 1000, 2) to QuantityUnit.KG else ceil(clean) to QuantityUnit.G
      Dimension.VOLUME -> if (clean >= 1000) roundUp(clean / 1000, 2) to QuantityUnit.L else ceil(clean) to QuantityUnit.ML
      Dimension.COUNT -> ceil(clean) to QuantityUnit.PCS
    }
  }

  private fun roundUp(value: Double, decimals: Int): Double {
    val f = Math.pow(10.0, decimals.toDouble())
    return ceil(value * f - 1e-9) / f
  }
}
