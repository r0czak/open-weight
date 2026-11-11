package com.openweight.app.testing

import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performScrollToNode

/**
 * Asks every scrollable list on screen to bring a node matching [matcher] into view. Screens open
 * with a header above their content, and lazy lists only compose what is near the screen, so a
 * test looking for something further down scrolls to it first, as a user would.
 */
fun SemanticsNodeInteractionsProvider.scrollIntoView(matcher: SemanticsMatcher) {
  // Already composed (on screen, or a snackbar): nothing to scroll.
  if (onAllNodes(matcher, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()) return
  val lists = onAllNodes(hasScrollToNodeAction())
  for (i in lists.fetchSemanticsNodes().indices) runCatching { lists[i].performScrollToNode(matcher) }
}

fun SemanticsNodeInteractionsProvider.scrollToText(text: String, substring: Boolean = false) = scrollIntoView(hasText(text, substring = substring))

fun SemanticsNodeInteractionsProvider.scrollToTag(tag: String) = scrollIntoView(hasTestTag(tag))
