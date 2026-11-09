package com.openweight.app.a11y

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.onRoot

/**
 * The accessibility rule every screen keeps, checked on Compose's semantics tree (what TalkBack
 * and Switch Access see): everything you can tap has a label, its own text or a content
 * description. (Tap size needs no check: Compose stretches small touch targets to 48 dp.)
 * Only what is on screen is checked; returns one line per problem.
 */
fun SemanticsNodeInteractionsProvider.accessibilityProblems(): List<String> {
  val root = onRoot().fetchSemanticsNode()
  val problems = mutableListOf<String>()
  fun visit(node: SemanticsNode) {
    val c = node.config
    val tappable = c.getOrNull(SemanticsActions.OnClick) != null
    val onScreen = node.boundsInRoot.let { it.width > 0 && it.height > 0 && it.overlaps(root.boundsInRoot) }
    if (tappable && onScreen && c.getOrNull(SemanticsProperties.Disabled) == null) {
      val label =
        c.getOrNull(SemanticsProperties.ContentDescription).orEmpty().joinToString() +
          c.getOrNull(SemanticsProperties.Text).orEmpty().joinToString { it.text } +
          c.getOrNull(SemanticsProperties.EditableText)?.text.orEmpty()
      val what = label.ifBlank { c.getOrNull(SemanticsProperties.TestTag) ?: c.getOrNull(SemanticsProperties.Role)?.toString() ?: "node ${node.id}" }
      if (label.isBlank()) problems += "No label: $what (role ${c.getOrNull(SemanticsProperties.Role)})"
    }
    node.children.forEach(::visit)
  }
  visit(root)
  return problems
}
