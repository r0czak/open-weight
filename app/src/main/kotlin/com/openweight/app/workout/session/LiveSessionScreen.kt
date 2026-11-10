package com.openweight.app.workout.session

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.openweight.app.R
import com.openweight.app.core.TimeAndIds
import com.openweight.app.core.model.WeightUnit
import com.openweight.app.core.model.format
import com.openweight.app.core.model.formatNumber
import com.openweight.app.core.model.formatReps
import com.openweight.app.core.model.label
import com.openweight.app.core.model.toKg
import com.openweight.app.core.ui.ScreenScaffold
import com.openweight.app.diet.parseNumber
import com.openweight.app.settings.SettingsRepository
import com.openweight.app.workout.data.SetValues
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface LiveSessionState {
  data object Loading : LiveSessionState

  /** Discarded, or never existed. */
  data object Gone : LiveSessionState

  data class Loaded(
    val session: Session,
    val unit: WeightUnit,
    /** By exercise id: what was done the last time it was trained (before this session). */
    val lastTime: Map<String, List<LoggedSet>> = emptyMap(),
  ) : LiveSessionState
}

@HiltViewModel(assistedFactory = LiveSessionViewModel.Factory::class)
class LiveSessionViewModel
@AssistedInject
constructor(
  private val sessions: SessionRepository,
  settings: SettingsRepository,
  private val clock: TimeAndIds,
  private val alerts: RestAlerts,
  private val saved: SavedStateHandle,
  @Assisted private val sessionId: String,
) : ViewModel() {
  @AssistedFactory
  interface Factory {
    fun create(sessionId: String): LiveSessionViewModel
  }

  /** Read once when the session opens: finishing this session must not turn it into "last time". */
  private val lastTime = MutableStateFlow<Map<String, List<LoggedSet>>>(emptyMap())

  /**
   * The end (epoch ms) of a rest the user skipped. Shared through [RestAlerts], so a second screen
   * on the same session agrees, and kept in [saved], so it survives Android stopping the app.
   */
  val skipped: StateFlow<Long?> = alerts.skippedRest

  init {
    if (alerts.skippedRest.value == null) saved.get<Long>(KEY_SKIPPED)?.let { alerts.skippedRest.value = it }
  }

  /** The setting is on but Android won't show notifications yet: ask for the permission. */
  val wantsNotifications: StateFlow<Boolean> =
    settings.settings.map { it.restAlerts && !alerts.canNotify() }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

  init {
    // Keeps the "rest is over" alarm in step with the session: set after each confirmed set,
    // gone when the rest is skipped, the set undone, or the session closed.
    viewModelScope.launch {
      combine(sessions.session(sessionId), alerts.skippedRest, settings.settings.map { it.restAlerts }) { s, skipped, on -> Triple(s, skipped, on) }
        .map { (s, skipped, on) ->
          val endsAt = s?.restEndsAt?.takeIf { on && it.toEpochMilli() != skipped && it.isAfter(clock.now()) }
          endsAt to s?.let(::nextLabel)
        }
        .distinctUntilChanged()
        .collect { (endsAt, next) -> if (endsAt != null) alerts.schedule(endsAt, next) else alerts.cancel() }
    }
  }

  private fun nextLabel(s: Session): NextSet? =
    s.exercises.firstNotNullOfOrNull { e -> e.sets.indexOfFirst { it.id == s.nextSetId }.takeIf { it >= 0 }?.let { NextSet(e.name, it + 1) } }

  init {
    viewModelScope.launch {
      val ids = sessions.session(sessionId).first()?.exercises?.mapNotNull { it.exerciseId }.orEmpty()
      lastTime.value = sessions.lastTime(ids)
    }
  }

  fun useWeight(exercisePosition: Int, weightKg: Double) =
    viewModelScope.launch { sessions.setWeightForRemaining(sessionId, exercisePosition, weightKg) }

  fun skipRest(endsAt: Instant) {
    alerts.skippedRest.value = endsAt.toEpochMilli()
    saved[KEY_SKIPPED] = endsAt.toEpochMilli()
  }

  private companion object {
    const val KEY_SKIPPED = "skipped_rest"
  }

  val state: StateFlow<LiveSessionState> =
    combine(sessions.session(sessionId), settings.settings, lastTime) { s, set, last ->
        if (s == null) LiveSessionState.Gone else LiveSessionState.Loaded(s, set.weightUnit, last)
      }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LiveSessionState.Loading)

  /**
   * Seconds of rest left, null when no rest is running. Counted from the last set's timestamp, so
   * it is right after the screen was off. (It ticks here, not in the UI: a ticking composable
   * would never let Compose go idle in tests.)
   */
  @OptIn(ExperimentalCoroutinesApi::class)
  val restLeft: StateFlow<Long?> =
    sessions
      .session(sessionId)
      .map { it?.restEndsAt }
      .distinctUntilChanged()
      .flatMapLatest { endsAt ->
        flow {
          if (endsAt == null) return@flow emit(null)
          while (true) {
            val left = ((Duration.between(clock.now(), endsAt).toMillis() + 999) / 1000).coerceAtLeast(0)
            emit(left)
            if (left == 0L) break
            delay(250)
          }
        }
          // Real time on a background thread: on the main thread, UI tests fast-forward delays
          // and this loop would spin without the clock moving.
          .flowOn(Dispatchers.Default)
      }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

  fun confirm(setId: String, weightKg: Double?, reps: Int) = viewModelScope.launch { sessions.confirmSet(setId, weightKg, reps) }

  fun undo(setId: String) = viewModelScope.launch { sessions.undoSet(setId) }

  fun addSet(exercisePosition: Int) = viewModelScope.launch { sessions.addSet(sessionId, exercisePosition) }

  fun removeSet(setId: String) = viewModelScope.launch { sessions.removeSet(setId) }

  fun finish() = viewModelScope.launch { sessions.finish(sessionId) }

  fun discard() = viewModelScope.launch { sessions.discard(sessionId) }
}

/**
 * Training a workout: every planned set with a suggestion (last time's numbers, or the plan's),
 * confirmed with one tap or corrected first; a rest countdown after each set; then a summary.
 * Leaving the screen keeps the session open; Home offers to resume it.
 */
@Composable
fun LiveSessionRoute(
  sessionId: String,
  onBack: () -> Unit,
  viewModel: LiveSessionViewModel =
    hiltViewModel<LiveSessionViewModel, LiveSessionViewModel.Factory>(key = "session-$sessionId") { it.create(sessionId) },
) {
  val state by viewModel.state.collectAsStateWithLifecycle()
  LaunchedEffect(state) { if (state is LiveSessionState.Gone) onBack() }
  val loaded = state as? LiveSessionState.Loaded
  val session = loaded?.session
  var menu by remember { mutableStateOf(false) }
  var confirmFinish by remember { mutableStateOf(false) }
  var confirmDiscard by remember { mutableStateOf(false) }
  var editing by remember { mutableStateOf<Pair<SessionExercise, SessionSet>?>(null) }
  val open = session != null && session.finishedAt == null
  val wantsNotifications by viewModel.wantsNotifications.collectAsStateWithLifecycle()
  val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
  var askedNotifications by rememberSaveable { mutableStateOf(false) }
  LaunchedEffect(wantsNotifications, open) {
    // Android 13+: the rest alert needs the notification permission; ask once per session screen.
    if (wantsNotifications && open && !askedNotifications && Build.VERSION.SDK_INT >= 33) {
      askedNotifications = true
      askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
  }

  ScreenScaffold(
    title = session?.workoutName.orEmpty(),
    onBack = onBack,
    actions = {
      if (session != null && open) {
        TextButton(
          onClick = { if (session.doneCount < session.setCount) confirmFinish = true else viewModel.finish() },
          enabled = session.doneCount > 0,
          modifier = Modifier.testTag("finish_session"),
        ) {
          Text(stringResource(R.string.session_finish))
        }
        Box {
          IconButton(onClick = { menu = true }, modifier = Modifier.testTag("session_menu")) {
            Icon(Icons.Filled.MoreVert, stringResource(R.string.action_more))
          }
          DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(
              text = { Text(stringResource(R.string.session_discard)) },
              onClick = { menu = false; confirmDiscard = true },
              modifier = Modifier.testTag("discard_session"),
            )
          }
        }
      }
    },
  ) { padding ->
    if (loaded == null || session == null) return@ScreenScaffold
    if (!open) {
      Summary(session, loaded.unit, onDone = onBack, modifier = Modifier.padding(padding))
      return@ScreenScaffold
    }
    Column(Modifier.fillMaxSize().padding(padding)) {
      Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
          stringResource(R.string.session_progress, session.doneCount, session.setCount),
          style = MaterialTheme.typography.labelLarge,
          modifier = Modifier.testTag("session_progress"),
        )
        LinearProgressIndicator(progress = { session.doneCount.toFloat() / session.setCount.coerceAtLeast(1) }, modifier = Modifier.fillMaxWidth())
      }
      LazyColumn(
        modifier = Modifier.weight(1f).testTag("session_list"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        items(session.exercises, key = { it.position }) { exercise ->
          ExerciseCard(
            exercise = exercise,
            unit = loaded.unit,
            lastTime = exercise.exerciseId?.let { loaded.lastTime[it] }?.let { Progress.lastTime(it, loaded.unit) },
            onUseWeight = { viewModel.useWeight(exercise.position, it) },
            nextSetId = session.nextSetId,
            onToggle = { set -> if (set.done) viewModel.undo(set.id) else viewModel.confirm(set.id, set.weightKg, set.reps) },
            onEdit = { set -> editing = exercise to set },
            onAddSet = { viewModel.addSet(exercise.position) },
          )
        }
      }
      val restLeft by viewModel.restLeft.collectAsStateWithLifecycle()
      val skippedRest by viewModel.skipped.collectAsStateWithLifecycle()
      RestBar(session.restEndsAt, restLeft, skippedRest, onSkip = { session.restEndsAt?.let(viewModel::skipRest) })
    }
  }

  editing?.let { (exercise, set) ->
    LogSetDialog(
      exerciseName = exercise.name,
      setNumber = exercise.sets.indexOfFirst { it.id == set.id } + 1,
      set = set,
      unit = loaded?.unit ?: WeightUnit.KG,
      canRemove = exercise.sets.size > 1,
      onConfirm = { kg, reps ->
        editing = null
        viewModel.confirm(set.id, kg, reps)
      },
      onRemove = {
        editing = null
        viewModel.removeSet(set.id)
      },
      onDismiss = { editing = null },
    )
  }
  if (confirmFinish && session != null) {
    val left = session.setCount - session.doneCount
    AlertDialog(
      onDismissRequest = { confirmFinish = false },
      title = { Text(stringResource(R.string.session_finish_title)) },
      text = { Text(pluralStringResource(R.plurals.session_finish_text, left, left)) },
      confirmButton = {
        TextButton(onClick = { confirmFinish = false; viewModel.finish() }, modifier = Modifier.testTag("confirm_finish")) {
          Text(stringResource(R.string.session_finish))
        }
      },
      dismissButton = { TextButton(onClick = { confirmFinish = false }) { Text(stringResource(R.string.session_keep_going)) } },
    )
  }
  if (confirmDiscard) {
    AlertDialog(
      onDismissRequest = { confirmDiscard = false },
      title = { Text(stringResource(R.string.session_discard_title)) },
      text = { Text(stringResource(R.string.session_discard_text)) },
      confirmButton = {
        TextButton(onClick = { confirmDiscard = false; viewModel.discard() }, modifier = Modifier.testTag("confirm_discard")) {
          Text(stringResource(R.string.session_discard))
        }
      },
      dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text(stringResource(R.string.action_cancel)) } },
    )
  }
}

@Composable
private fun ExerciseCard(
  exercise: SessionExercise,
  unit: WeightUnit,
  lastTime: LastTime?,
  onUseWeight: (Double) -> Unit,
  nextSetId: String?,
  onToggle: (SessionSet) -> Unit,
  onEdit: (SessionSet) -> Unit,
  onAddSet: () -> Unit,
) {
  Card(
    modifier = Modifier.fillMaxWidth().testTag("session_exercise_${exercise.position}"),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
  ) {
    Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 4.dp)) {
      Text("${exercise.position + 1}. ${exercise.name}", style = MaterialTheme.typography.titleMedium)
      if (lastTime != null) {
        Text(
          stringResource(R.string.session_last_time, lastTime.sets.map { setText(it, unit) }.joinToString(", ")),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.testTag("last_time_${exercise.position}"),
        )
        val step = lastTime.stepUpKg
        // Only while it helps: once a remaining set already carries that weight, the hint goes.
        if (step != null && exercise.sets.any { !it.done && it.weightKg != null && it.weightKg < step - 0.001 }) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
              stringResource(R.string.session_step_up, unit.format(step)),
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.primary,
              modifier = Modifier.weight(1f).testTag("step_up_${exercise.position}"),
            )
            val label = stringResource(R.string.session_step_up_use_label, unit.format(step), exercise.name)
            TextButton(
              onClick = { onUseWeight(step) },
              modifier = Modifier.semantics { contentDescription = label }.testTag("use_step_${exercise.position}"),
            ) {
              Text(stringResource(R.string.session_step_up_use))
            }
          }
        }
      }
      exercise.sets.forEachIndexed { i, set ->
        SetRow(
          exercise = exercise,
          number = i + 1,
          set = set,
          unit = unit,
          isNext = set.id == nextSetId,
          onToggle = { onToggle(set) },
          onEdit = { onEdit(set) },
        )
      }
      if (exercise.sets.size < SessionRepository.MAX_SETS_PER_EXERCISE) {
        TextButton(onClick = onAddSet, modifier = Modifier.testTag("session_add_set_${exercise.position}")) {
          Icon(Icons.Filled.Add, contentDescription = null)
          Text(stringResource(R.string.set_add), Modifier.padding(start = 4.dp))
        }
      }
    }
  }
}

@Composable
private fun SetRow(
  exercise: SessionExercise,
  number: Int,
  set: SessionSet,
  unit: WeightUnit,
  isNext: Boolean,
  onToggle: () -> Unit,
  onEdit: () -> Unit,
) {
  val tag = "${exercise.position}_$number"
  val faded = MaterialTheme.colorScheme.onSurfaceVariant
  Row(
    Modifier.fillMaxWidth()
      .padding(vertical = 2.dp)
      .clip(RoundedCornerShape(12.dp))
      .then(if (isNext) Modifier.background(MaterialTheme.colorScheme.secondaryContainer) else Modifier)
      .clickable(onClickLabel = stringResource(R.string.session_change_set), onClick = onEdit)
      .padding(start = 8.dp, top = 4.dp, bottom = 4.dp)
      .testTag("session_set_$tag"),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text("$number", fontWeight = FontWeight.SemiBold, modifier = Modifier.width(28.dp))
    Column(Modifier.weight(1f)) {
      Text(
        stringResource(R.string.session_set_value, unit.format(set.weightKg), set.reps),
        style = MaterialTheme.typography.bodyLarge,
        color = if (set.done) faded else MaterialTheme.colorScheme.onSurface,
        textDecoration = if (set.done) TextDecoration.LineThrough else null,
        modifier = Modifier.testTag("session_value_$tag"),
      )
      Text(
        stringResource(R.string.session_set_target, formatReps(set.targetRepsMin, set.targetRepsMax)),
        style = MaterialTheme.typography.bodySmall,
        color = faded,
      )
    }
    if (set.done) {
      FilledIconButton(onClick = onToggle, modifier = Modifier.testTag("session_check_$tag")) {
        Icon(Icons.Filled.Check, stringResource(R.string.session_undo_set, number, exercise.name))
      }
    } else {
      OutlinedIconButton(onClick = onToggle, modifier = Modifier.testTag("session_check_$tag")) {
        Icon(Icons.Filled.Check, stringResource(R.string.session_confirm_set, number, exercise.name))
      }
    }
  }
}

/** The rest countdown after a set, with a nudge when it ends. */
@Composable
private fun RestBar(endsAt: Instant?, left: Long?, skipped: Long?, onSkip: () -> Unit) {
  if (endsAt == null || left == null) return
  var wasRunning by remember(endsAt) { mutableStateOf(false) }
  val haptics = LocalHapticFeedback.current
  LaunchedEffect(left) {
    if (left > 0) wasRunning = true else if (wasRunning) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
  }
  if (skipped == endsAt.toEpochMilli()) return
  Surface(tonalElevation = 3.dp, modifier = Modifier.testTag("rest_bar")) {
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
      Text(
        if (left > 0) stringResource(R.string.session_rest, formatClock(left)) else stringResource(R.string.session_rest_over),
        style = MaterialTheme.typography.titleMedium,
        color = if (left > 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary,
        modifier = Modifier.weight(1f).testTag("rest_text"),
      )
      TextButton(onClick = onSkip, modifier = Modifier.testTag("rest_skip")) {
        Text(stringResource(if (left > 0) R.string.session_rest_skip else R.string.action_ok))
      }
    }
  }
}

/** "1:05", "12:00", "1:02:03". */
internal fun formatClock(seconds: Long): String {
  val h = seconds / 3600
  val m = seconds % 3600 / 60
  val s = seconds % 60
  return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

@Composable
private fun LogSetDialog(
  exerciseName: String,
  setNumber: Int,
  set: SessionSet,
  unit: WeightUnit,
  canRemove: Boolean,
  onConfirm: (weightKg: Double?, reps: Int) -> Unit,
  onRemove: () -> Unit,
  onDismiss: () -> Unit,
) {
  var weight by remember { mutableStateOf(set.weightKg?.let(unit::formatNumber).orEmpty()) }
  var reps by remember { mutableStateOf(set.reps.toString()) }
  var weightError by remember { mutableStateOf(false) }
  var repsError by remember { mutableStateOf(false) }
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(stringResource(R.string.session_set_title, setNumber, exerciseName)) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
          stringResource(R.string.session_set_target, formatReps(set.targetRepsMin, set.targetRepsMax)),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedTextField(
            value = weight,
            onValueChange = { weight = it; weightError = false },
            label = { Text(stringResource(R.string.session_weight, unit.label)) },
            isError = weightError,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.weight(1f).testTag("log_weight"),
          )
          OutlinedTextField(
            value = reps,
            onValueChange = { reps = it; repsError = false },
            label = { Text(stringResource(R.string.set_col_reps)) },
            isError = repsError,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f).testTag("log_reps"),
          )
        }
        Text(stringResource(R.string.session_weight_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
    },
    confirmButton = {
      TextButton(
        onClick = {
          // parseNumber refuses "NaN" and "Infinity" too, not only text.
          val kg = parseNumber(weight)?.let(unit::toKg)
          val r = reps.trim().toIntOrNull()
          weightError = (weight.isNotBlank() && kg == null) || (kg != null && kg !in 0.0..SetValues.MAX_WEIGHT_KG)
          repsError = r == null || r !in 0..SetValues.MAX_REPS
          if (!weightError && !repsError) onConfirm(kg, r!!)
        },
        modifier = Modifier.testTag("log_confirm"),
      ) {
        Text(stringResource(if (set.done) R.string.action_save else R.string.session_set_done))
      }
    },
    dismissButton = {
      Row {
        if (canRemove) TextButton(onClick = onRemove, modifier = Modifier.testTag("log_remove")) { Text(stringResource(R.string.set_delete)) }
        TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
      }
    },
  )
}

@Composable
private fun Summary(session: Session, unit: WeightUnit, onDone: () -> Unit, modifier: Modifier = Modifier) {
  val minutes = session.duration(session.finishedAt ?: session.startedAt).toMinutes().toInt()
  Column(modifier.fillMaxSize().padding(24.dp).testTag("session_summary"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Text(stringResource(R.string.session_done_title), style = MaterialTheme.typography.headlineSmall)
    Text(pluralStringResource(R.plurals.session_done_minutes, minutes, minutes))
    Text(stringResource(R.string.session_done_sets, session.doneCount, session.setCount), modifier = Modifier.testTag("summary_sets"))
    Text(stringResource(R.string.session_done_volume, unit.format(session.volumeKg)))
    Button(onClick = onDone, modifier = Modifier.padding(top = 16.dp).testTag("summary_done")) { Text(stringResource(R.string.action_done)) }
  }
}
