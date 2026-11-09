package com.openweight.app.diet.data

import com.openweight.app.core.model.QuantityUnit
import com.openweight.app.core.model.QuantityUnit.Dimension

/** Calories (kcal) and macros (grams). */
data class Macros(val kcal: Double, val protein: Double, val carbs: Double, val fat: Double) {
  operator fun plus(o: Macros) = Macros(kcal + o.kcal, protein + o.protein, carbs + o.carbs, fat + o.fat)

  operator fun times(f: Double) = Macros(kcal * f, protein * f, carbs * f, fat * f)

  operator fun div(d: Double) = times(1 / d)

  companion object {
    val ZERO = Macros(0.0, 0.0, 0.0, 0.0)
  }
}

/** What the nutrition rules need from an ingredient. */
data class IngredientNutrition(
  /** Per 100 g, or per 100 ml when [defaultUnit] is a volume; null = no values entered. */
  val per100: Macros?,
  val defaultUnit: QuantityUnit,
  val gramsPerPiece: Double?,
)

data class RecipeNutrition(
  val total: Macros,
  val perServing: Macros,
  /** Names of ingredients that could not be counted (no values, or pieces without a weight). */
  val missing: List<String>,
) {
  val complete: Boolean
    get() = missing.isEmpty()
}

/**
 * Calories and macros for amounts of ingredients. Values are per 100 g, or per 100 ml for
 * ingredients measured by volume. Mass and volume are treated as equal (1 ml ≈ 1 g) when a recipe
 * measures an ingredient differently from its values; pieces need the ingredient's grams per piece.
 */
object NutritionCalculator {
  /** The amount in the ingredient's basis unit (g or ml), or null when it can't be known. */
  fun amountInBasis(quantity: Double, unit: QuantityUnit, ingredient: IngredientNutrition): Double? =
    when (unit.dimension) {
      Dimension.MASS, Dimension.VOLUME -> quantity * unit.toBase
      Dimension.COUNT -> ingredient.gramsPerPiece?.let { quantity * it }
    }

  fun forAmount(quantity: Double, unit: QuantityUnit, ingredient: IngredientNutrition): Macros? {
    val per100 = ingredient.per100 ?: return null
    val amount = amountInBasis(quantity, unit, ingredient) ?: return null
    return per100 * (amount / 100.0)
  }

  /** [items]: (name, quantity, unit, ingredient) per recipe line. */
  fun forRecipe(
    items: List<Pair<String, Triple<Double, QuantityUnit, IngredientNutrition>>>,
    servings: Int,
  ): RecipeNutrition {
    var total = Macros.ZERO
    val missing = mutableListOf<String>()
    for ((name, line) in items) {
      val (quantity, unit, ingredient) = line
      val macros = forAmount(quantity, unit, ingredient)
      if (macros == null) missing += name else total += macros
    }
    return RecipeNutrition(total, total / servings.coerceAtLeast(1).toDouble(), missing.distinct())
  }
}
