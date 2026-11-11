package com.openweight.app.core.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.openweight.app.R

/**
 * Inter, bundled (the app is offline), in five static weights; the bold and extra-bold cuts use
 * Inter's display optical size, tighter for big headings and numbers. Licence: assets/licenses.
 */
val Inter =
  FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
    Font(R.font.inter_extrabold, FontWeight.ExtraBold),
  )

private fun style(size: Int, line: Int, weight: FontWeight, tracking: Double = 0.0) =
  TextStyle(fontFamily = Inter, fontWeight = weight, fontSize = size.sp, lineHeight = line.sp, letterSpacing = tracking.sp)

/** Big, heavy and tight at the top (numbers and titles), calm and regular in the body. */
val Typography =
  Typography(
    displayLarge = style(52, 58, FontWeight.ExtraBold, -1.5),
    displayMedium = style(44, 50, FontWeight.ExtraBold, -1.2),
    displaySmall = style(38, 44, FontWeight.ExtraBold, -1.0),
    headlineLarge = style(32, 38, FontWeight.Bold, -0.8),
    headlineMedium = style(28, 34, FontWeight.Bold, -0.6),
    headlineSmall = style(22, 28, FontWeight.Bold, -0.3),
    titleLarge = style(20, 26, FontWeight.Bold, -0.2),
    titleMedium = style(16, 22, FontWeight.SemiBold, -0.1),
    titleSmall = style(14, 20, FontWeight.SemiBold),
    bodyLarge = style(16, 24, FontWeight.Normal),
    bodyMedium = style(14, 20, FontWeight.Normal),
    bodySmall = style(12, 16, FontWeight.Normal),
    labelLarge = style(14, 20, FontWeight.SemiBold),
    labelMedium = style(12, 16, FontWeight.SemiBold),
    labelSmall = style(11, 16, FontWeight.Medium, 0.2),
  )
