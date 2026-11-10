package com.openweight.app.progress.strength

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform

/** The tiers' colours, cold to hot: the same in light and dark themes, so the legend always matches. */
val Tier.color: Color
  get() =
    when (this) {
      Tier.UNTRAINED -> Color(0xFF8D9DB0)
      Tier.NOVICE -> Color(0xFF4F9BE8)
      Tier.INTERMEDIATE -> Color(0xFF2BB0A0)
      Tier.PROFICIENT -> Color(0xFF6CC04A)
      Tier.ADVANCED -> Color(0xFFF2C230)
      Tier.ELITE -> Color(0xFFF07A3A)
      Tier.WORLD_CLASS -> Color(0xFFC2459C)
    }

/** Which side of the body a figure shows. */
enum class BodySide { FRONT, BACK }

/**
 * A body seen from the [side], each muscle filled with its tier's colour; muscles with no score
 * stay in the body's own colour. Drawn on a 100 × 205 grid, left half only, mirrored for the right.
 */
@Composable
fun BodyFigure(side: BodySide, muscles: Map<Muscle, MuscleScore>, modifier: Modifier = Modifier) {
  val body = if (isSystemInDarkTheme()) Color(0xFF3A3F47) else Color(0xFFDCE1E7)
  val untrained = MaterialTheme.colorScheme.outlineVariant
  val line = MaterialTheme.colorScheme.surface
  Canvas(modifier.aspectRatio(WIDTH / HEIGHT)) {
    val scale = size.height / HEIGHT
    withTransform({ translate((size.width - WIDTH * scale) / 2, 0f); scale(scale, scale, Offset.Zero) }) {
      drawCircle(body, radius = 9.5f, center = Offset(50f, 13f))
      for (part in BodyShapes.silhouette) drawMirrored(part, body)
      for ((muscle, parts) in if (side == BodySide.FRONT) BodyShapes.front else BodyShapes.back) {
        val color = muscles[muscle]?.tier?.color ?: untrained
        for (part in parts) {
          drawMirrored(part, color)
          drawMirrored(part, line, Stroke(width = 0.9f))
        }
      }
    }
  }
}

private const val WIDTH = 100f
private const val HEIGHT = 205f

private fun DrawScope.drawMirrored(points: List<Offset>, color: Color, stroke: Stroke? = null) {
  val left = smoothPath(points)
  val right = smoothPath(points.map { Offset(WIDTH - it.x, it.y) })
  if (stroke == null) {
    drawPath(left, color)
    drawPath(right, color)
  } else {
    drawPath(left, color, style = stroke)
    drawPath(right, color, style = stroke)
  }
}

/** A closed smooth curve through every point (Catmull-Rom as cubic Béziers). */
private fun smoothPath(p: List<Offset>): Path {
  val path = Path()
  val n = p.size
  path.moveTo(p[0].x, p[0].y)
  for (i in 0 until n) {
    val p0 = p[(i - 1 + n) % n]
    val p1 = p[i]
    val p2 = p[(i + 1) % n]
    val p3 = p[(i + 2) % n]
    val c1 = p1 + (p2 - p0) / 6f
    val c2 = p2 - (p3 - p1) / 6f
    path.cubicTo(c1.x, c1.y, c2.x, c2.y, p2.x, p2.y)
  }
  path.close()
  return path
}

/** The figure's shapes: the left half of the body (the viewer's left), in grid units. */
internal object BodyShapes {
  private fun pts(vararg xy: Int): List<Offset> = xy.toList().chunked(2).map { (x, y) -> Offset(x.toFloat(), y.toFloat()) }

  val silhouette: List<List<Offset>> =
    listOf(
      pts(46, 20, 54, 20, 55, 30, 45, 30), // neck
      pts(50, 27, 44, 28, 34, 31, 28, 36, 29, 50, 32, 64, 34, 80, 35, 94, 37, 102, 50, 106, 63, 102, 65, 94, 66, 80, 68, 64, 71, 50, 72, 36, 66, 31, 56, 28), // torso
      pts(29, 33, 22, 37, 18, 48, 17, 62, 18, 74, 24, 77, 28, 66, 31, 50), // upper arm
      pts(18, 73, 13, 88, 10, 102, 16, 104, 21, 90, 25, 76), // forearm
      pts(10, 101, 8, 110, 11, 117, 16, 114, 17, 104), // hand
      pts(36, 94, 32, 110, 31, 132, 34, 150, 44, 152, 47, 134, 49, 112, 50, 104), // thigh
      pts(33, 150, 31, 166, 33, 186, 40, 188, 42, 170, 44, 152), // shin
      pts(33, 185, 29, 196, 32, 200, 41, 199, 41, 187), // foot
    )

  val front: Map<Muscle, List<List<Offset>>> =
    mapOf(
      Muscle.TRAPS to listOf(pts(46, 26, 42, 30, 36, 32, 45, 33, 48, 30)),
      Muscle.SHOULDERS to listOf(pts(35, 33, 27, 35, 22, 42, 21, 51, 26, 52, 32, 44)),
      Muscle.CHEST to listOf(pts(49, 36, 38, 35, 32, 42, 33, 52, 41, 56, 49, 54)),
      Muscle.BICEPS to listOf(pts(22, 55, 20, 63, 21, 72, 25, 73, 28, 64, 27, 55)),
      Muscle.FOREARMS to listOf(pts(19, 78, 14, 90, 12, 100, 16, 101, 21, 89, 24, 79)),
      Muscle.ABS to listOf(pts(49, 58, 44, 59, 43, 72, 44, 86, 46, 96, 49, 99), pts(41, 60, 36, 58, 34, 70, 36, 84, 42, 92, 42, 74)),
      Muscle.QUADS to listOf(pts(37, 102, 33, 116, 33, 134, 37, 147, 43, 148, 46, 134, 47, 114, 44, 104)),
      Muscle.CALVES to listOf(pts(34, 158, 33, 170, 35, 182, 38, 183, 40, 170, 40, 158)),
    )

  val back: Map<Muscle, List<List<Offset>>> =
    mapOf(
      Muscle.TRAPS to listOf(pts(49, 24, 44, 28, 35, 33, 41, 38, 46, 47, 49, 58)),
      Muscle.SHOULDERS to listOf(pts(34, 34, 27, 35, 22, 42, 22, 50, 27, 50, 33, 41)),
      Muscle.UPPER_BACK to listOf(pts(40, 41, 32, 44, 31, 55, 36, 70, 43, 78, 47, 62, 45, 50)),
      Muscle.TRICEPS to listOf(pts(22, 54, 20, 63, 21, 72, 26, 73, 28, 63, 27, 54)),
      Muscle.FOREARMS to listOf(pts(19, 78, 14, 90, 12, 100, 16, 101, 21, 89, 24, 79)),
      Muscle.LOWER_BACK to listOf(pts(49, 62, 46, 70, 43, 82, 45, 92, 49, 93)),
      Muscle.GLUTES to listOf(pts(49, 96, 40, 95, 35, 102, 36, 114, 43, 119, 49, 115)),
      Muscle.HAMSTRINGS to listOf(pts(35, 120, 33, 134, 36, 148, 42, 149, 45, 136, 46, 122)),
      Muscle.CALVES to listOf(pts(34, 156, 32, 166, 34, 178, 38, 182, 41, 172, 41, 158)),
    )
}
