package com.openweight.app.core.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/*
 * The hero: the top of a tab's first screen, a deep blue-to-violet gradient with soft light spots,
 * running under the status bar and rounded at the bottom. It holds the screen's big title, its one
 * headline number and its quick actions, in white. Below it the page carries on with cards.
 */

private val Hero = Color.White
private val HeroSoft = Color.White.copy(alpha = 0.72f)

@Composable
private fun isDark() = MaterialTheme.colorScheme.background.luminance() < 0.5f

/** The hero's gradient: blue to violet, deeper in the dark theme. */
@Composable
fun heroColors(): List<Color> =
  if (isDark()) listOf(Color(0xFF1C35A8), Color(0xFF34209A), Color(0xFF4A1B86))
  else listOf(Color(0xFF2B5BF5), Color(0xFF4F3DF0), Color(0xFF7B3FE4))

/** A full-width gradient header for a tab's first screen; content is white. */
@Composable
fun HeroHeader(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
  val colors = heroColors()
  Box(
    modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
      .background(Brush.linearGradient(colors, start = Offset.Zero, end = Offset(1400f, 1400f)))
  ) {
    // Soft light spots for depth.
    Canvas(Modifier.matchParentSize()) {
      drawCircle(
        Brush.radialGradient(listOf(Color(0x66FF7AD9), Color.Transparent), center = Offset(size.width * 0.95f, size.height * 0.1f), radius = size.width * 0.6f),
        radius = size.width * 0.6f,
        center = Offset(size.width * 0.95f, size.height * 0.1f),
      )
      drawCircle(
        Brush.radialGradient(listOf(Color(0x5538D9FF), Color.Transparent), center = Offset(0f, size.height), radius = size.width * 0.55f),
        radius = size.width * 0.55f,
        center = Offset(0f, size.height),
      )
    }
    CompositionLocalProvider(LocalContentColor provides Hero) {
      Column(Modifier.statusBarsPadding().padding(start = 20.dp, end = 12.dp, top = 8.dp, bottom = 20.dp), content = content)
    }
  }
}

/** The hero's top row: the screen's big title, with icon buttons at the end. */
@Composable
fun HeroTitle(title: String, actions: @Composable RowScope.() -> Unit = {}) {
  Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
    Text(title, style = MaterialTheme.typography.headlineMedium, color = Hero, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
    actions()
  }
}

/** A small label over the hero's number ("Today · Tue 10 Nov"). */
@Composable
fun HeroLabel(text: String, modifier: Modifier = Modifier) {
  Text(text, style = MaterialTheme.typography.labelLarge, color = HeroSoft, modifier = modifier)
}

/** The hero's headline: a big value with an optional smaller unit after it. */
@Composable
fun HeroValue(value: String, modifier: Modifier = Modifier, unit: String? = null) {
  Row(modifier.padding(end = 8.dp), verticalAlignment = Alignment.Bottom) {
    Text(value, style = MaterialTheme.typography.displaySmall, color = Hero, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
    if (unit != null) {
      Spacer(Modifier.width(8.dp))
      Text(unit, style = MaterialTheme.typography.titleLarge, color = HeroSoft, modifier = Modifier.padding(bottom = 6.dp))
    }
  }
}

/** A line of supporting text in the hero. */
@Composable
fun HeroCaption(text: String, modifier: Modifier = Modifier) {
  Text(text, style = MaterialTheme.typography.bodyMedium, color = HeroSoft, modifier = modifier.padding(end = 8.dp))
}

/** A translucent pill in the hero (a status such as "Done" or "Week 2 of 4"). */
@Composable
fun HeroPill(text: String, modifier: Modifier = Modifier, @DrawableRes icon: Int? = null) {
  Row(
    modifier.clip(CircleShape).background(Color.White.copy(alpha = 0.18f)).padding(horizontal = 12.dp, vertical = 6.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    if (icon != null) Icon(painterResource(icon), contentDescription = null, tint = Hero, modifier = Modifier.size(16.dp))
    Text(text, style = MaterialTheme.typography.labelLarge, color = Hero)
  }
}

/** A thin bar in the hero: how far along something is (0..1). */
@Composable
fun HeroProgress(fraction: Float, modifier: Modifier = Modifier) {
  Box(modifier.fillMaxWidth().padding(end = 8.dp).height(6.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.22f))) {
    Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(6.dp).clip(CircleShape).background(Hero))
  }
}

/** The row of round quick actions at the bottom of the hero. */
@Composable
fun HeroActions(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
  Row(modifier.fillMaxWidth().padding(top = 20.dp, end = 8.dp), horizontalArrangement = Arrangement.SpaceAround, content = content)
}

/** One quick action: a round translucent button with its icon, and its label under it. */
@Composable
fun HeroAction(@DrawableRes icon: Int, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
  Column(
    modifier.width(76.dp).clip(RoundedCornerShape(16.dp)).clickable(role = Role.Button, onClick = onClick).padding(vertical = 4.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Box(Modifier.size(52.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
      Icon(painterResource(icon), contentDescription = null, tint = Hero, modifier = Modifier.size(24.dp))
    }
    Spacer(Modifier.height(8.dp))
    Text(label, style = MaterialTheme.typography.labelMedium, color = Hero, maxLines = 1, overflow = TextOverflow.Ellipsis)
  }
}

/**
 * A tile for a two-column grid under the hero: a coloured round icon, a small title, a big value
 * and a caption. With [onClick] the whole tile opens something.
 */
@Composable
fun StatTile(
  @DrawableRes icon: Int,
  title: String,
  value: String,
  modifier: Modifier = Modifier,
  caption: String? = null,
  accent: MenuAccent = MenuAccent.BLUE,
  onClick: (() -> Unit)? = null,
  extra: @Composable ColumnScope.() -> Unit = {},
) {
  Card(
    modifier = modifier,
    shape = MaterialTheme.shapes.large,
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
  ) {
    Column(
      Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier.semantics(mergeDescendants = true) {}).padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      MenuIcon(icon, accent = accent)
      Spacer(Modifier.height(8.dp))
      Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
      Text(value, style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
      if (caption != null) {
        Text(caption, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
      }
      extra()
    }
  }
}

/** Two [StatTile]s side by side. */
@Composable
fun TileRow(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
  Row(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), content = content)
}

/** A section heading on the page, between cards. */
@Composable
fun PageHeading(text: String, modifier: Modifier = Modifier, action: @Composable RowScope.() -> Unit = {}) {
  Row(modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 18.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
    Text(text, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f).testTag("heading_$text"))
    action()
  }
}
