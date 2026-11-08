package com.openweight.app.core.model

/** Units a quantity can be stored in. Quantities are only summed within one [Dimension]. */
enum class QuantityUnit(val dimension: Dimension, val toBase: Double) {
  G(Dimension.MASS, 1.0),
  KG(Dimension.MASS, 1000.0),
  ML(Dimension.VOLUME, 1.0),
  L(Dimension.VOLUME, 1000.0),
  TSP(Dimension.VOLUME, 5.0),
  TBSP(Dimension.VOLUME, 15.0),
  PCS(Dimension.COUNT, 1.0);

  enum class Dimension { MASS, VOLUME, COUNT }
}

/** Meal slots of a day in the diet calendar, in display order. */
enum class MealSlot { BREAKFAST, LUNCH, DINNER, SNACK }

/** Unit used to display weights; storage is always kilograms. */
enum class WeightUnit { KG, LB }

/** App colour theme choice. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }
