package com.openweight.app.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class WeightsTest {
  @Test
  fun formatsKilogramsWithoutTrailingZeros() {
    assertEquals("60 kg", WeightUnit.KG.format(60.0))
    assertEquals("62.5 kg", WeightUnit.KG.format(62.5))
    assertEquals("—", WeightUnit.KG.format(null))
  }

  @Test
  fun convertsToAndFromPounds() {
    assertEquals("132.3 lb", WeightUnit.LB.format(60.0))
    assertEquals(60.0, WeightUnit.LB.toKg(WeightUnit.LB.fromKg(60.0)), 1e-9)
    assertEquals(45.359237, WeightUnit.LB.toKg(100.0), 1e-9)
  }

  @Test
  fun formatsRepsAndRest() {
    assertEquals("8", formatReps(8, null))
    assertEquals("8", formatReps(8, 8))
    assertEquals("8–10", formatReps(8, 10))
    assertEquals("45 s", formatRest(45))
    assertEquals("2 min", formatRest(120))
    assertEquals("2 min 30 s", formatRest(150))
  }
}
