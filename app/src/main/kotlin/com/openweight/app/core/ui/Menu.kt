package com.openweight.app.core.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

/*
 * The app's menu style: rows grouped in clean rounded cards on the grey page, under a small grey
 * heading. Each row has its icon in a coloured circle, a title, an optional line under it, and
 * something at the end (a chevron when it opens a screen). Used by the Workout tab, Settings, the
 * workout summary and the shortcuts at the top of the Diet panels.
 */

/** A titled group of [MenuRow]s in one card; rows are separated by inset dividers. */
@Composable
fun MenuSection(title: String?, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
  Column(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
    if (title != null) {
      Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, top = 14.dp, bottom = 8.dp),
      )
    }
    Card(
      shape = MaterialTheme.shapes.large,
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

/** The icon of a menu row: in its accent colour, on a soft circle of the same colour. */
@Composable
fun MenuIcon(@DrawableRes icon: Int, modifier: Modifier = Modifier, accent: MenuAccent = MenuAccent.BLUE) {
  val (container, content) = accent.colors()
  Box(modifier.size(40.dp).clip(CircleShape).background(container), contentAlignment = Alignment.Center) {
    Icon(painterResource(icon), contentDescription = null, tint = content, modifier = Modifier.size(22.dp))
  }
}

/** Each row's own colour, so entries are told apart at a glance; the same hues in both themes, lighter in dark. */
enum class MenuAccent(private val light: Long, private val dark: Long) {
  BLUE(0xFF2B5BF5, 0xFF7C9CFF),
  VIOLET(0xFF7A4DF0, 0xFFB49BFF),
  GREEN(0xFF12A150, 0xFF4FD08A),
  ORANGE(0xFFE8710A, 0xFFFFA65C),
  PINK(0xFFD6337A, 0xFFFF7FB5),
  TEAL(0xFF0E9AA7, 0xFF4FD3DE),
  GREY(0xFF5B6475, 0xFFA9B1BF);

  @Composable
  internal fun colors(): Pair<Color, Color> {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val color = Color(if (dark) this.dark else light)
    return color.copy(alpha = if (dark) 0.2f else 0.12f) to color
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
  accent: MenuAccent = MenuAccent.BLUE,
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
fun ShortcutCard(@DrawableRes icon: Int, title: String, subtitle: String?, onClick: () -> Unit, tag: String, accent: MenuAccent = MenuAccent.BLUE) {
  MenuSection(title = null, modifier = Modifier.padding(top = 4.dp)) {
    MenuRow(icon, title, Modifier.testTag(tag), subtitle = subtitle, accent = accent, onClick = onClick)
  }
}

/**
 * Pill-shaped tabs: the chosen one filled, the others plain text on the page. [tags] gives each
 * tab its test tag.
 */
@Composable
fun PillTabs(labels: List<String>, selected: Int, onSelect: (Int) -> Unit, tags: List<String>, modifier: Modifier = Modifier) {
  Row(
    modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    labels.forEachIndexed { index, label ->
      val isSelected = index == selected
      Box(
        Modifier
          .clip(CircleShape)
          .background(if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.surfaceContainerLow)
          .selectable(selected = isSelected, role = Role.Tab, onClick = { onSelect(index) })
          .heightIn(min = 40.dp)
          .padding(horizontal = 16.dp)
          .testTag(tags[index]),
        contentAlignment = Alignment.Center,
      ) {
        Text(
          label,
          style = MaterialTheme.typography.labelLarge,
          color = if (isSelected) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurface,
        )
      }
    }
  }
}

/**
 * One row of a list shown as a card: consecutive rows join into one rounded card on the page (the
 * first rounds its top, the last its bottom), with inset dividers between them. Rows inside take
 * the card's colour as their surface, so a ListItem needs no colour of its own.
 */
@Composable
fun CardListItem(index: Int, count: Int, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
  val radius = 24.dp
  val shape =
    RoundedCornerShape(
      topStart = if (index == 0) radius else 0.dp,
      topEnd = if (index == 0) radius else 0.dp,
      bottomStart = if (index == count - 1) radius else 0.dp,
      bottomEnd = if (index == count - 1) radius else 0.dp,
    )
  val card = MaterialTheme.colorScheme.surfaceContainerLow
  Column(modifier.padding(horizontal = 16.dp).padding(top = if (index == 0) 8.dp else 0.dp).clip(shape).background(card)) {
    MaterialTheme(colorScheme = MaterialTheme.colorScheme.copy(surface = card), typography = MaterialTheme.typography, shapes = MaterialTheme.shapes) {
      content()
    }
    if (index < count - 1) {
      HorizontalDivider(Modifier.padding(start = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
    }
  }
}
