package com.openweight.app.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.openweight.app.R

/**
 * A screen inside one of the bottom tabs. The top bar handles the status bar inset and the app's
 * bottom navigation bar handles the bottom one, so the content itself adds no window insets.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenScaffold(
  title: String,
  modifier: Modifier = Modifier,
  onBack: (() -> Unit)? = null,
  actions: @Composable RowScope.() -> Unit = {},
  floatingActionButton: @Composable () -> Unit = {},
  snackbarHostState: SnackbarHostState? = null,
  content: @Composable (PaddingValues) -> Unit,
) {
  Scaffold(
    modifier = modifier,
    topBar = {
      TopAppBar(
        title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        navigationIcon = {
          if (onBack != null) {
            IconButton(onClick = onBack) {
              Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
            }
          }
        },
        actions = actions,
      )
    },
    floatingActionButton = floatingActionButton,
    snackbarHost = { if (snackbarHostState != null) SnackbarHost(snackbarHostState) },
    contentWindowInsets = WindowInsets(0, 0, 0, 0),
    content = content,
  )
}

/** A friendly placeholder for a list or panel with nothing in it yet. */
@Composable
fun EmptyState(message: String, modifier: Modifier = Modifier, hint: String? = null) {
  Column(
    modifier = modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 48.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Text(
      text = message,
      style = MaterialTheme.typography.bodyLarge,
      textAlign = TextAlign.Center,
    )
    if (hint != null) {
      Text(
        text = hint,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
      )
    }
  }
}
