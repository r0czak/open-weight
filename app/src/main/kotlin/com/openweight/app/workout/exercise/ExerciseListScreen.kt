package com.openweight.app.workout.exercise

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.openweight.app.R
import com.openweight.app.core.ui.CardListItem
import com.openweight.app.core.ui.EmptyState
import com.openweight.app.core.ui.ScreenScaffold
import com.openweight.app.workout.data.ExerciseListItem
import com.openweight.app.workout.data.StarterExercises
import kotlinx.coroutines.launch

@Composable
fun ExerciseListRoute(
  onBack: () -> Unit,
  onOpen: (String) -> Unit,
  onCreate: () -> Unit,
  viewModel: ExerciseListViewModel = hiltViewModel(),
) {
  val state by viewModel.state.collectAsStateWithLifecycle()
  val snackbar = remember { SnackbarHostState() }
  val scope = rememberCoroutineScope()
  val resources = LocalResources.current
  var menu by remember { mutableStateOf(false) }
  val addStarter = {
    viewModel.addStarter { r ->
      val message =
        if (r.added == 0) resources.getString(R.string.starter_none_new)
        else resources.getQuantityString(R.plurals.starter_added, r.added, r.added) +
          if (r.skipped > 0) " " + resources.getQuantityString(R.plurals.starter_kept, r.skipped, r.skipped) else ""
      scope.launch { snackbar.showSnackbar(message) }
    }
  }
  ScreenScaffold(
    title = stringResource(R.string.workout_exercises),
    onBack = onBack,
    snackbarHostState = snackbar,
    actions = {
      Box {
        IconButton(onClick = { menu = true }, modifier = Modifier.testTag("exercise_menu")) {
          Icon(Icons.Filled.MoreVert, stringResource(R.string.action_more))
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
          DropdownMenuItem(
            text = { Text(stringResource(R.string.starter_menu)) },
            onClick = { menu = false; addStarter() },
            modifier = Modifier.testTag("add_starter_menu"),
          )
        }
      }
    },
    floatingActionButton = {
      FloatingActionButton(onClick = onCreate, modifier = Modifier.testTag("add_exercise")) {
        Icon(Icons.Filled.Add, stringResource(R.string.exercise_new))
      }
    },
  ) { padding ->
    Column(Modifier.fillMaxSize().padding(padding)) {
      SearchField(state.query, viewModel::onQueryChange)
      when {
        state.loading -> Unit
        state.totalCount == 0 ->
          Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            EmptyState(stringResource(R.string.exercise_empty), hint = stringResource(R.string.exercise_empty_hint))
            Button(onClick = addStarter, modifier = Modifier.testTag("add_starter")) {
              Text(pluralStringResource(R.plurals.starter_button, StarterExercises.COUNT, StarterExercises.COUNT))
            }
          }
        state.items.isEmpty() -> EmptyState(stringResource(R.string.search_no_results, state.query))
        else -> ExerciseList(state.items, onOpen)
      }
    }
  }
}

@Composable
fun SearchField(query: String, onChange: (String) -> Unit, placeholder: String? = null) {
  OutlinedTextField(
    value = query,
    onValueChange = onChange,
    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).testTag("search"),
    placeholder = { Text(placeholder ?: stringResource(R.string.exercise_search)) },
    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
    singleLine = true,
  )
}

@Composable
private fun ExerciseList(items: List<ExerciseListItem>, onOpen: (String) -> Unit) {
  LazyColumn(contentPadding = PaddingValues(bottom = 88.dp)) {
    items(items, key = { it.id }) { item ->
      CardListItem(items.indexOf(item), items.size) {
        ListItem(
          modifier = Modifier.clickable { onOpen(item.id) },
          headlineContent = { Text(item.name) },
          supportingContent = {
            val details = listOf(item.muscleGroup, item.equipment).filter { it.isNotBlank() }
            if (details.isNotEmpty()) Text(details.joinToString(" · "))
          },
          trailingContent = {
            if (item.usageCount > 0) {
              Text(pluralStringResource(R.plurals.used_in_workouts, item.usageCount, item.usageCount))
            }
          },
        )
      }
    }
  }
}
