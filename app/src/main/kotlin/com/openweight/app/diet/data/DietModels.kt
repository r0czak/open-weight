package com.openweight.app.diet.data

import com.openweight.app.core.model.QuantityUnit

/** Shop categories offered in the ingredient editor; any other text is allowed too. */
val SHOP_CATEGORIES =
  listOf(
    "Produce",
    "Meat & fish",
    "Dairy & eggs",
    "Bakery",
    "Grains & pasta",
    "Canned & jars",
    "Frozen",
    "Spices & sauces",
    "Oils & fats",
    "Snacks & sweets",
    "Drinks",
    "Supplements",
    "Other",
  )

/** The units an ingredient can be measured in by default. */
val INGREDIENT_UNITS = listOf(QuantityUnit.G, QuantityUnit.ML, QuantityUnit.PCS)

data class IngredientListItem(
  val id: String,
  val name: String,
  val shopCategory: String,
  val defaultUnit: QuantityUnit,
  val kcalPer100: Double?,
  /** Number of recipes that use it. */
  val usageCount: Int,
)

data class Ingredient(
  val id: String,
  val name: String,
  val shopCategory: String,
  val defaultUnit: QuantityUnit,
  val kcalPer100: Double?,
  val proteinPer100: Double?,
  val carbsPer100: Double?,
  val fatPer100: Double?,
  val gramsPerPiece: Double?,
)

/** Everything the ingredient editor saves. */
data class IngredientValues(
  val name: String,
  val shopCategory: String,
  val defaultUnit: QuantityUnit,
  val kcalPer100: Double? = null,
  val proteinPer100: Double? = null,
  val carbsPer100: Double? = null,
  val fatPer100: Double? = null,
  val gramsPerPiece: Double? = null,
) {
  init {
    listOf(kcalPer100, proteinPer100, carbsPer100, fatPer100).forEach {
      require(it == null || it in 0.0..MAX_PER_100) { "Nutrition values must be between 0 and ${MAX_PER_100.toInt()}" }
    }
    require(gramsPerPiece == null || (gramsPerPiece > 0 && gramsPerPiece <= MAX_GRAMS_PER_PIECE)) {
      "Grams per piece must be more than 0 and at most ${MAX_GRAMS_PER_PIECE.toInt()}"
    }
  }

  companion object {
    /** Nothing has more than 900 kcal or 100 g of a macro per 100 g; this only stops nonsense. */
    const val MAX_PER_100 = 10_000.0
    const val MAX_GRAMS_PER_PIECE = 100_000.0
  }
}

data class RecipeSummary(
  val id: String,
  val name: String,
  val servings: Int,
  val prepMinutes: Int?,
  val tags: String,
  val ingredientCount: Int,
  val nutrition: RecipeNutrition,
)

data class RecipeItem(
  val id: String,
  val ingredientId: String,
  val name: String,
  val quantity: Double,
  val unit: QuantityUnit,
  /** Null when this line can't be counted. */
  val macros: Macros?,
)

data class RecipeDetail(
  val id: String,
  val name: String,
  val servings: Int,
  val prepMinutes: Int?,
  val steps: String,
  val tags: String,
  val notes: String,
  val items: List<RecipeItem>,
  val nutrition: RecipeNutrition,
)

/** Name, servings and text fields of a recipe. */
data class RecipeInfo(
  val name: String,
  val servings: Int,
  val prepMinutes: Int?,
  val steps: String,
  val tags: String,
  val notes: String,
) {
  init {
    require(name.isNotBlank()) { "Name can't be empty" }
    require(servings in 1..MAX_SERVINGS) { "Servings must be between 1 and $MAX_SERVINGS" }
    require(prepMinutes == null || prepMinutes in 0..24 * 60) { "Prep time must be under 24 hours" }
  }

  companion object {
    const val MAX_SERVINGS = 100
  }
}

/** A quantity must be more than 0 and below a sane upper limit (100 kg / 100 l / 1000 pcs). */
fun validQuantity(quantity: Double, unit: QuantityUnit): Boolean =
  quantity > 0 && quantity * unit.toBase <= if (unit == QuantityUnit.PCS) 1000.0 else 100_000.0
