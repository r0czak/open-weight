package com.openweight.app.diet

import com.openweight.app.core.model.QuantityUnit
import java.util.Locale
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class DietFormatTest {
  private val saved = Locale.getDefault()

  @Before fun english() = Locale.setDefault(Locale.UK)

  @After fun restore() = Locale.setDefault(saved)

  @Test
  fun formatsCaloriesQuantitiesAndGrams() {
    assertEquals("303 kcal", formatKcal(303.2))
    assertEquals("2,373 kcal", formatKcal(2372.6))
    assertEquals("200 g", formatQuantity(200.0, QuantityUnit.G))
    assertEquals("1.5 pcs", formatQuantity(1.5, QuantityUnit.PCS))
    assertEquals("4.5 g", formatGrams(4.53))
    assertEquals("38 g", formatGrams(37.6))
  }

  @Test
  fun parsesCommaOrDotDecimals() {
    assertEquals(1.5, parseNumber(" 1,5 ")!!, 0.0)
    assertEquals(200.0, parseNumber("200")!!, 0.0)
    assertNull(parseNumber("abc"))
    assertNull(parseNumber(""))
  }

  @Test
  fun textThatJavaReadsAsANumberButIsNot_isRefused() {
    // Double.parseDouble accepts all of these; stored, they would break every screen showing them.
    listOf("NaN", "Infinity", "-Infinity", "1e999").forEach { assertNull(it, parseNumber(it)) }
  }
}
