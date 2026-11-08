package com.openweight.app.core.model

import java.math.BigDecimal
import java.math.RoundingMode

private const val KG_PER_LB = 0.45359237

/** Weights are stored in kilograms; these convert for display and input in the user's unit. */
fun WeightUnit.fromKg(kg: Double): Double = if (this == WeightUnit.KG) kg else kg / KG_PER_LB

fun WeightUnit.toKg(value: Double): Double = if (this == WeightUnit.KG) value else value * KG_PER_LB

val WeightUnit.label: String
  get() = if (this == WeightUnit.KG) "kg" else "lb"

/** "60", "62.5", "132.3": at most one decimal for lb, two for kg, no trailing zeros. */
fun WeightUnit.formatNumber(kg: Double): String {
  val decimals = if (this == WeightUnit.KG) 2 else 1
  return BigDecimal(fromKg(kg)).setScale(decimals, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
}

fun WeightUnit.format(kg: Double?): String = if (kg == null) "—" else "${formatNumber(kg)} $label"

/** "8", "8–10". */
fun formatReps(repsMin: Int, repsMax: Int?): String =
  if (repsMax == null || repsMax == repsMin) "$repsMin" else "$repsMin–$repsMax"

/** "90 s", "2 min", "2 min 30 s". */
fun formatRest(seconds: Int): String {
  if (seconds < 60) return "$seconds s"
  val min = seconds / 60
  val sec = seconds % 60
  return if (sec == 0) "$min min" else "$min min $sec s"
}
