package com.openweight.app.core.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * A small line chart drawn by the app itself (no charting library): one value per point, in
 * order, with the lowest and highest values labelled. [description] is what TalkBack reads.
 */
@Composable
fun LineChart(
  values: List<Double>,
  minLabel: String,
  maxLabel: String,
  firstLabel: String,
  lastLabel: String,
  description: String,
  modifier: Modifier = Modifier,
) {
  val line = MaterialTheme.colorScheme.primary
  val grid = MaterialTheme.colorScheme.outlineVariant
  Column(modifier.semantics(mergeDescendants = true) { contentDescription = description }) {
    Row(Modifier.fillMaxWidth()) {
      Text(maxLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Canvas(Modifier.fillMaxWidth().height(140.dp).padding(vertical = 6.dp)) {
      if (values.isEmpty()) return@Canvas
      val lo = values.min()
      val hi = values.max()
      val span = (hi - lo).takeIf { it > 0 } ?: 1.0
      fun point(i: Int): Offset {
        val x = if (values.size == 1) size.width / 2 else size.width * i / (values.size - 1)
        val y = size.height - ((values[i] - lo) / span * size.height).toFloat()
        return Offset(x, if (hi == lo) size.height / 2 else y)
      }
      drawLine(grid, Offset(0f, 0f), Offset(size.width, 0f), strokeWidth = 1.dp.toPx())
      drawLine(grid, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1.dp.toPx())
      val path = Path().apply {
        values.indices.forEach { i -> point(i).let { if (i == 0) moveTo(it.x, it.y) else lineTo(it.x, it.y) } }
      }
      drawPath(path, line, style = Stroke(width = 2.5.dp.toPx()))
      values.indices.forEach { drawCircle(line, radius = 4.dp.toPx(), center = point(it)) }
    }
    Row(Modifier.fillMaxWidth()) {
      Text(minLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
    }
    Row(Modifier.fillMaxWidth().padding(top = 2.dp)) {
      Text(firstLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
      Text(lastLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
  }
}
