package com.openweight.app.workout.blocks

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.openweight.app.R
import com.openweight.app.core.Today
import com.openweight.app.core.ui.CardListItem
import com.openweight.app.core.ui.EmptyState
import com.openweight.app.core.ui.ScreenScaffold
import com.openweight.app.core.ui.formatDateRange
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class BlockStatus { ACTIVE, UPCOMING, PAST }

data class BlockListItem(val block: BlockSummary, val status: BlockStatus, val week: Int, val totalWeeks: Int)

data class BlockListState(val loading: Boolean = true, val items: List<BlockListItem> = emptyList())

@HiltViewModel
class BlockListViewModel
@Inject
constructor(private val repository: TrainingBlockRepository, private val todayNow: Today) : ViewModel() {
  private val today: LocalDate
    get() = todayNow.date.value

  val state: StateFlow<BlockListState> =
    // Follows the date: a block becomes active, or moves on a week, when a new day begins.
    combine(repository.blocks(), todayNow.date) { blocks, today ->
        val items =
          blocks.map { b ->
            val scheduled = ScheduledBlock(b.id, b.name, b.startDate, b.endDate, emptyMap())
            val status =
              when {
                scheduled.contains(today) -> BlockStatus.ACTIVE
                b.startDate.isAfter(today) -> BlockStatus.UPCOMING
                else -> BlockStatus.PAST
              }
            BlockListItem(b, status, scheduled.weekNumber(today), scheduled.totalWeeks)
          }
        // Active first, then upcoming (soonest first), then past (most recent first).
        BlockListState(
          loading = false,
          items =
            items.filter { it.status == BlockStatus.ACTIVE } +
              items.filter { it.status == BlockStatus.UPCOMING } +
              items.filter { it.status == BlockStatus.PAST }.reversed(),
        )
      }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BlockListState())

  private val _opened = Channel<String>(Channel.BUFFERED)
  val opened: Flow<String> = _opened.receiveAsFlow()

  fun create(name: String, start: LocalDate, end: LocalDate) {
    viewModelScope.launch { _opened.send(repository.createBlock(name, start, end)) }
  }

  /** New blocks default to starting next Monday, for 4 weeks. */
  val defaultStart: LocalDate
    get() = today.with(TemporalAdjusters.next(java.time.DayOfWeek.MONDAY))
}

@Composable
fun BlockListRoute(
  onBack: () -> Unit,
  onOpen: (String) -> Unit,
  viewModel: BlockListViewModel = hiltViewModel(),
) {
  val state by viewModel.state.collectAsStateWithLifecycle()
  var creating by remember { mutableStateOf(false) }
  LaunchedEffect(viewModel) { viewModel.opened.collect(onOpen) }

  ScreenScaffold(
    title = stringResource(R.string.workout_blocks),
    onBack = onBack,
    floatingActionButton = {
      FloatingActionButton(onClick = { creating = true }, modifier = Modifier.testTag("add_block")) {
        Icon(Icons.Filled.Add, stringResource(R.string.block_new))
      }
    },
  ) { padding ->
    Box(Modifier.fillMaxSize().padding(padding)) {
      when {
        state.loading -> Unit
        state.items.isEmpty() ->
          EmptyState(stringResource(R.string.block_empty), hint = stringResource(R.string.block_empty_hint))
        else ->
          LazyColumn(contentPadding = PaddingValues(bottom = 88.dp)) {
            var lastStatus: BlockStatus? = null
            state.items.forEach { item ->
              if (item.status != lastStatus) {
                lastStatus = item.status
                item(key = "header-${item.status}") { StatusHeader(item.status) }
              }
              item(key = item.block.id) {
                // Each status group is its own card.
                val group = state.items.filter { it.status == item.status }
                CardListItem(group.indexOf(item), group.size) { BlockRow(item, onClick = { onOpen(item.block.id) }) }
              }
            }
          }
      }
    }
  }

  if (creating) {
    val start = viewModel.defaultStart
    BlockInfoDialog(
      title = stringResource(R.string.block_new),
      initialName = "",
      initialStart = start,
      initialEnd = start.plusWeeks(4).minusDays(1),
      initialNotes = null,
      onDismiss = { creating = false },
      onConfirm = { name, s, e, _ ->
        creating = false
        viewModel.create(name, s, e)
      },
    )
  }
}

@Composable
private fun StatusHeader(status: BlockStatus) {
  Text(
    stringResource(
      when (status) {
        BlockStatus.ACTIVE -> R.string.block_status_active
        BlockStatus.UPCOMING -> R.string.block_status_upcoming
        BlockStatus.PAST -> R.string.block_status_past
      }
    ),
    style = MaterialTheme.typography.titleSmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
  )
}

@Composable
private fun BlockRow(item: BlockListItem, onClick: () -> Unit) {
  val b = item.block
  ListItem(
    modifier = Modifier.clickable(onClick = onClick),
    headlineContent = { Text(b.name) },
    supportingContent = {
      Text(
        formatDateRange(b.startDate, b.endDate) + " · " +
          pluralStringResource(R.plurals.workout_count, b.workoutCount, b.workoutCount)
      )
    },
    trailingContent = {
      Text(
        if (item.status == BlockStatus.ACTIVE) stringResource(R.string.block_week_short, item.week, item.totalWeeks)
        else pluralStringResource(R.plurals.block_weeks, item.totalWeeks, item.totalWeeks)
      )
    },
  )
}
