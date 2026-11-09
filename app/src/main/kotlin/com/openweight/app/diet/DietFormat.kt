package com.openweight.app.diet

import com.openweight.app.core.model.QuantityUnit
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale
import kotlin.math.roundToInt

val QuantityUnit.label: String
  get() =
    when (this) {
      QuantityUnit.G -> "g"
      QuantityUnit.KG -> "kg"
      QuantityUnit.ML -> "ml"
      QuantityUnit.L -> "l"
      QuantityUnit.TSP -> "tsp"
      QuantityUnit.TBSP -> "tbsp"
      QuantityUnit.PCS -> "pcs"
    }

/** "200", "1.5", "0.25": at most 2 decimals, no trailing zeros. */
fun formatNumber(value: Double): String =
  BigDecimal(value).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()

/** "200 g", "2 pcs". */
fun formatQuantity(quantity: Double, unit: QuantityUnit): String = "${formatNumber(quantity)} ${unit.label}"

/** "303 kcal", "2,373 kcal" (thousands grouped the phone's way). */
fun formatKcal(kcal: Double): String = String.format(Locale.getDefault(), "%,d kcal", kcal.roundToInt())

/** Grams of a macro, one decimal under 10 g, whole numbers above: "4.5 g", "38 g". */
fun formatGrams(grams: Double): String =
  if (grams < 10) "${BigDecimal(grams).setScale(1, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()} g"
  else "${grams.roundToInt()} g"

/** Parses user input like "1,5" or " 200 ", or null when it isn't a number. */
fun parseNumber(text: String): Double? = text.trim().replace(',', '.').takeIf { it.isNotEmpty() }?.toDoubleOrNull()
