package com.openweight.app.a11y

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The checker itself: it flags what it should, and passes a proper button. */
@RunWith(AndroidJUnit4::class)
class AccessibilityChecksTest {
  @get:Rule val compose = createComposeRule()

  @Test
  fun flagsAnUnlabelledButton_passesAnIconButtonWithADescription() {
    compose.setContent {
      Box {
        Box(Modifier.size(20.dp).clickable {})
        IconButton(onClick = {}) { Icon(Icons.Filled.Add, contentDescription = "Add") }
      }
    }
    val problems = compose.accessibilityProblems()
    assertEquals(1, problems.size)
    assert(problems.single().startsWith("No label")) { problems.toString() }
  }
}
