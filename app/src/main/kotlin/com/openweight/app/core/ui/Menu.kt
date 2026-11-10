package com.openweight.app.core.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

/*
 * The app's menu style: rows grouped in rounded cards under a small primary-coloured heading. Each
 * row has its icon in a tinted rounded square, a title, an optional line under it, and something at
 * the end (a chevron when it opens a screen). Used by the Workout tab, Settings and the shortcuts
 * at the top of the Diet panels.
 */

/** A titled group of [MenuRow]s in one card; rows are separated by inset dividers. */
@Composable
fun MenuSection(title: String?, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
  Column(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
    if (title != null) {
      Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, top = 10.dp, bottom = 8.dp),
      )
    }
    Card(
      shape = RoundedCornerShape(16.dp),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
      Column(content = content)
    }
  }
}

/** The divider between two rows of a [MenuSection], starting after the icon. */
@Composable
fun MenuDivider() {
  HorizontalDivider(Modifier.padding(start = 72.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
}

/** The icon of a menu row: tinted, in a rounded square of the matching container colour. */
@Composable
fun MenuIcon(@DrawableRes icon: Int, modifier: Modifier = Modifier, accent: MenuAccent = MenuAccent.PRIMARY) {
  val (container, content) = accent.colors()
  Box(modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(container), contentAlignment = Alignment.Center) {
    Icon(painterResource(icon), contentDescription = null, tint = content, modifier = Modifier.size(22.dp))
  }
}

/** Which theme colours a row's icon takes; menus use them to tell groups apart at a glance. */
enum class MenuAccent { PRIMARY, TERTIARY, NEUTRAL }

/** Blue (the app's main colour), plum, or a neutral grey; each soft enough to sit in a card, in light and dark themes. */
@Composable
private fun MenuAccent.colors(): Pair<Color, Color> =
  with(MaterialTheme.colorScheme) {
    when (this@colors) {
      MenuAccent.PRIMARY -> secondaryContainer to primary
      MenuAccent.TERTIARY -> tertiary.copy(alpha = 0.16f) to tertiary
      MenuAccent.NEUTRAL -> surfaceContainerHighest to onSurfaceVariant
    }
  }

/**
 * One menu entry. With [onClick] the whole row is the touch target and, unless [trailing] says
 * otherwise, ends in a chevron; without it the row only shows information.
 */
@Composable
fun MenuRow(
  @DrawableRes icon: Int,
  title: String,
  modifier: Modifier = Modifier,
  subtitle: String? = null,
  accent: MenuAccent = MenuAccent.PRIMARY,
  titleColor: Color = Color.Unspecified,
  onClick: (() -> Unit)? = null,
  trailing: (@Composable () -> Unit)? = if (onClick != null) ({ Chevron() }) else null,
) {
  Row(
    modifier
      .fillMaxWidth()
      .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
      .heightIn(min = 64.dp)
      .padding(horizontal = 16.dp, vertical = 10.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    MenuIcon(icon, accent = accent)
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
      Text(title, style = MaterialTheme.typography.titleMedium, color = titleColor)
      if (subtitle != null) {
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
    }
    trailing?.invoke()
  }
}

@Composable
fun Chevron() {
  Icon(
    Icons.AutoMirrored.Filled.KeyboardArrowRight,
    contentDescription = null,
    tint = MaterialTheme.colorScheme.onSurfaceVariant,
  )
}

/**
 * A single [MenuRow] in its own card, for a link at the top of a list screen (Ingredients above
 * the recipes, Diet plans above the calendar) so it reads as a way somewhere, not as list content.
 */
@Composable
fun ShortcutCard(@DrawableRes icon: Int, title: String, subtitle: String?, onClick: () -> Unit, tag: String, accent: MenuAccent = MenuAccent.PRIMARY) {
  MenuSection(title = null, modifier = Modifier.padding(top = 4.dp)) {
    MenuRow(icon, title, Modifier.testTag(tag), subtitle = subtitle, accent = accent, onClick = onClick)
  }
}
