package com.openweight.app.workout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.openweight.app.R
import com.openweight.app.core.ui.ScreenScaffold
import com.openweight.app.core.ui.theme.OpenWeightTheme

/** Workout tab home: today's plan on top, then the entry points to blocks, workouts, exercises. */
@Composable
fun WorkoutHomeScreen(modifier: Modifier = Modifier) {
  ScreenScaffold(title = stringResource(R.string.tab_workout), modifier = modifier) { padding ->
    Column(
      modifier =
        Modifier.fillMaxSize()
          .padding(padding)
          .verticalScroll(rememberScrollState())
          .padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text(stringResource(R.string.workout_today), style = MaterialTheme.typography.titleLarge)
          Text(
            stringResource(R.string.workout_today_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
      Column {
        SectionEntry(stringResource(R.string.workout_blocks))
        HorizontalDivider()
        SectionEntry(stringResource(R.string.workout_workouts))
        HorizontalDivider()
        SectionEntry(stringResource(R.string.workout_exercises))
      }
    }
  }
}

@Composable
private fun SectionEntry(title: String) {
  ListItem(
    headlineContent = { Text(title) },
    supportingContent = { Text(stringResource(R.string.coming_soon)) },
  )
}

@Preview(showBackground = true)
@Composable
private fun WorkoutHomeScreenPreview() {
  OpenWeightTheme { WorkoutHomeScreen() }
}
