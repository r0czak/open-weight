package com.openweight.app.diet.shopping

import com.openweight.app.diet.PanelHeader
import com.openweight.app.diet.panelHeader

import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.openweight.app.R
import com.openweight.app.core.TimeAndIds
import com.openweight.app.core.model.QuantityUnit
import com.openweight.app.core.ui.DateRangeDialog
import com.openweight.app.core.ui.EmptyState
import com.openweight.app.core.ui.formatDateRange
import com.openweight.app.diet.data.DietRepository
import com.openweight.app.diet.data.GenerateResult
import com.openweight.app.diet.data.ShoppingItem
import com.openweight.app.diet.data.ShoppingListView
import com.openweight.app.diet.data.ShoppingRepository
import com.openweight.app.diet.data.validQuantity
import com.openweight.app.diet.formatNumber
import com.openweight.app.diet.formatQuantity
import com.openweight.app.diet.label
import com.openweight.app.diet.parseNumber
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ShoppingState(
  val loaded: Boolean = false,
  val list: ShoppingListView? = null,
  /** Shop categories to offer for items added by hand. */
  val categories: List<String> = emptyList(),
)

@HiltViewModel
class ShoppingViewModel
@Inject
constructor(private val shopping: ShoppingRepository, diet: DietRepository, private val clock: TimeAndIds) : ViewModel() {
  /** Read when used, not once: the app can stay open past midnight. */
  val today: LocalDate
    get() = clock.today()

  val state: StateFlow<ShoppingState> =
    combine(shopping.current(), diet.shopCategories()) { list, categories -> ShoppingState(true, list, categories) }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ShoppingState())

  fun generate(from: LocalDate, to: LocalDate, done: (GenerateResult) -> Unit) =
    viewModelScope.launch { done(shopping.generate(from, to)) }

  fun setChecked(id: String, checked: Boolean) = viewModelScope.launch { shopping.setChecked(id, checked) }

  fun addManual(name: String, quantity: Double?, unit: QuantityUnit?, category: String) =
    viewModelScope.launch { shopping.addManual(name, quantity, unit, category) }

  fun editQuantity(id: String, quantity: Double?, unit: QuantityUnit?) = viewModelScope.launch { shopping.editQuantity(id, quantity, unit) }

  fun delete(id: String) = viewModelScope.launch { shopping.deleteItem(id) }

  fun uncheckAll() = viewModelScope.launch { shopping.uncheckAll() }
}

/**
 * The Diet tab's Shopping panel: what to buy for the meals planned in a date range, grouped by shop
 * category. Items can be ticked, their amounts changed, and items added by hand; updating from the
 * calendar keeps all of that.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShoppingPanel(
  snackbar: SnackbarHostState,
  adding: Boolean,
  onAddingDone: () -> Unit,
  header: PanelHeader? = null,
  viewModel: ShoppingViewModel = hiltViewModel(),
) {
  val state by viewModel.state.collectAsStateWithLifecycle()
  var choosingDates by remember { mutableStateOf(false) }
  var editing by remember { mutableStateOf<ShoppingItem?>(null) }
  val resources = LocalResources.current
  val scope = rememberCoroutineScope()
  val generate: (LocalDate, LocalDate) -> Unit = { from, to ->
    viewModel.generate(from, to) { r ->
      val message =
        resources.getString(R.string.shopping_updated, r.added, r.updated, r.removed) +
          if (r.kept > 0) " " + resources.getQuantityString(R.plurals.shopping_kept, r.kept, r.kept) else ""
      scope.launch { snackbar.showSnackbar(message) }
    }
  }
  val list = state.list

  when {
    !state.loaded -> LazyColumn(Modifier.fillMaxSize()) { panelHeader(header) }
    list == null ->
      LazyColumn(Modifier.fillMaxSize().testTag("shopping"), horizontalAlignment = Alignment.CenterHorizontally) {
        panelHeader(header)
        item(key = "empty") {
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            EmptyState(message = stringResource(R.string.diet_shopping_empty), hint = stringResource(R.string.shopping_empty_hint))
            Button(onClick = { generate(viewModel.today, viewModel.today.plusDays(6)) }, modifier = Modifier.testTag("shopping_next_week")) {
              Text(stringResource(R.string.shopping_next_7_days))
            }
            OutlinedButton(onClick = { choosingDates = true }, modifier = Modifier.padding(top = 8.dp).testTag("shopping_choose_dates")) {
              Text(stringResource(R.string.shopping_choose_dates))
            }
          }
        }
      }
    else -> ShoppingList(list, viewModel, header, onUpdate = { generate(list.from, list.to) }, onChooseDates = { choosingDates = true }, onEdit = { editing = it })
  }

  if (choosingDates) {
    DateRangeDialog(
      title = stringResource(R.string.shopping_dates_title),
      confirmLabel = stringResource(R.string.shopping_make_list),
      maxDays = ShoppingRepository.MAX_DAYS,
      initialFrom = list?.from ?: viewModel.today,
      initialTo = list?.to ?: viewModel.today.plusDays(6),
      onDismiss = { choosingDates = false },
      onConfirm = { from, to ->
        choosingDates = false
        generate(from, to)
      },
    )
  }
  if (adding) {
    ShoppingItemDialog(
      item = null,
      categories = state.categories,
      onDismiss = onAddingDone,
      onSave = { name, q, u, category ->
        onAddingDone()
        viewModel.addManual(name, q, u, category)
      },
    )
  }
  editing?.let { item ->
    ShoppingItemDialog(
      item = item,
      categories = state.categories,
      onDismiss = { editing = null },
      onSave = { _, q, u, _ ->
        editing = null
        viewModel.editQuantity(item.id, q, u)
      },
      onDelete = {
        editing = null
        viewModel.delete(item.id)
      },
    )
  }
}

@Composable
private fun ShoppingList(
  list: ShoppingListView,
  viewModel: ShoppingViewModel,
  header: PanelHeader?,
  onUpdate: () -> Unit,
  onChooseDates: () -> Unit,
  onEdit: (ShoppingItem) -> Unit,
) {
  var menu by remember { mutableStateOf(false) }
  val groups = list.byCategory(stringResource(R.string.shopping_other))
  LazyColumn(Modifier.fillMaxSize().testTag("shopping"), contentPadding = PaddingValues(bottom = 88.dp)) {
    panelHeader(header)
    item(key = "header") {
      Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
          Text(
            stringResource(R.string.shopping_for_meals, formatDateRange(list.from, list.to)),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.testTag("shopping_range"),
          )
          if (list.items.isNotEmpty()) {
            Text(
              stringResource(R.string.shopping_progress, list.items.count { it.checked }, list.items.size),
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.testTag("shopping_progress"),
            )
          }
        }
        FilledTonalButton(onClick = onUpdate, modifier = Modifier.testTag("shopping_update")) {
          Icon(Icons.Filled.Refresh, contentDescription = null)
          Text(stringResource(R.string.shopping_update), Modifier.padding(start = 6.dp))
        }
        Box {
          IconButton(onClick = { menu = true }, modifier = Modifier.testTag("shopping_menu")) {
            Icon(Icons.Filled.MoreVert, stringResource(R.string.action_more))
          }
          DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(
              text = { Text(stringResource(R.string.shopping_choose_dates)) },
              onClick = { menu = false; onChooseDates() },
              modifier = Modifier.testTag("shopping_change_dates"),
            )
            DropdownMenuItem(
              text = { Text(stringResource(R.string.shopping_uncheck_all)) },
              onClick = { menu = false; viewModel.uncheckAll() },
              enabled = list.items.any { it.checked },
              modifier = Modifier.testTag("shopping_uncheck_all"),
            )
          }
        }
      }
      Text(
        stringResource(R.string.shopping_update_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp),
      )
      HorizontalDivider(Modifier.padding(top = 8.dp))
    }
    if (list.items.isEmpty()) {
      item(key = "empty") { EmptyState(message = stringResource(R.string.shopping_nothing), hint = stringResource(R.string.shopping_nothing_hint)) }
    }
    groups.forEach { (category, items) ->
      item(key = "cat-$category") {
        Text(
          category,
          style = MaterialTheme.typography.titleSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
        )
      }
      items(items, key = { it.id }) { item ->
        ItemRow(item, onCheck = { viewModel.setChecked(item.id, it) }, onEdit = { onEdit(item) })
      }
    }
  }
}

@Composable
private fun ItemRow(item: ShoppingItem, onCheck: (Boolean) -> Unit, onEdit: () -> Unit) {
  val faded = if (item.checked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
  Row(
    // The whole row is the checkbox, so TalkBack reads "Oats, 560 g, checked" as one item.
    Modifier.fillMaxWidth()
      .toggleable(value = item.checked, role = Role.Checkbox, onValueChange = onCheck)
      .padding(start = 4.dp, end = 4.dp)
      .testTag("shop_${item.name}"),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Checkbox(checked = item.checked, onCheckedChange = null, modifier = Modifier.padding(12.dp))
    Column(Modifier.weight(1f)) {
      Text(
        item.name,
        color = faded,
        textDecoration = if (item.checked) TextDecoration.LineThrough else null,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      val note =
        when {
          item.isManual -> stringResource(R.string.shopping_added_by_you)
          item.isQuantityEdited -> stringResource(R.string.shopping_changed_by_you)
          else -> null
        }
      if (note != null) Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
    }
    if (item.quantity != null && item.unit != null) {
      Text(
        formatQuantity(item.quantity, item.unit),
        color = faded,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.testTag("shop_qty_${item.name}"),
      )
    }
    IconButton(onClick = onEdit, modifier = Modifier.testTag("shop_edit_${item.name}")) {
      Icon(Icons.Filled.Edit, stringResource(R.string.shopping_edit_item, item.name))
    }
  }
}

/**
 * Adds an item by hand ([item] null: name, optional amount, category) or changes one's amount. An
 * item from the calendar needs an amount, in a unit of the same kind (so updates still match it).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ShoppingItemDialog(
  item: ShoppingItem?,
  categories: List<String>,
  onDismiss: () -> Unit,
  onSave: (name: String, quantity: Double?, unit: QuantityUnit?, category: String) -> Unit,
  onDelete: (() -> Unit)? = null,
) {
  val fromCalendar = item != null && !item.isManual
  var name by remember { mutableStateOf(item?.name.orEmpty()) }
  var amount by remember { mutableStateOf(item?.quantity?.let(::formatNumber).orEmpty()) }
  var unit by remember { mutableStateOf(item?.unit ?: QuantityUnit.PCS) }
  var category by remember { mutableStateOf(item?.category.orEmpty()) }
  var nameError by remember { mutableStateOf(false) }
  var amountError by remember { mutableStateOf(false) }
  val units = if (fromCalendar) QuantityUnit.entries.filter { it.dimension == item.unit?.dimension } else QuantityUnit.entries

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(item?.name ?: stringResource(R.string.shopping_add_item)) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (item == null) {
          OutlinedTextField(
            value = name,
            onValueChange = { name = it; nameError = false },
            label = { Text(stringResource(R.string.shopping_item_name)) },
            isError = nameError,
            supportingText = { if (nameError) Text(stringResource(R.string.error_name_empty)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth().testTag("shop_item_name"),
          )
        }
        OutlinedTextField(
          value = amount,
          onValueChange = { amount = it; amountError = false },
          label = { Text(stringResource(if (fromCalendar) R.string.recipe_quantity else R.string.shopping_amount_optional)) },
          isError = amountError,
          supportingText = { if (amountError) Text(stringResource(R.string.error_quantity)) },
          singleLine = true,
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
          modifier = Modifier.fillMaxWidth().testTag("shop_item_amount"),
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          units.forEach { u ->
            FilterChip(selected = unit == u, onClick = { unit = u }, label = { Text(u.label) }, modifier = Modifier.testTag("shop_unit_${u.name}"))
          }
        }
        if (item == null) {
          OutlinedTextField(
            value = category,
            onValueChange = { category = it },
            label = { Text(stringResource(R.string.ingredient_category)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth().testTag("shop_item_category"),
          )
          FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            categories.forEach { c ->
              FilterChip(selected = category.equals(c, ignoreCase = true), onClick = { category = c }, label = { Text(c) })
            }
          }
        }
      }
    },
    confirmButton = {
      TextButton(
        onClick = {
          val q = parseNumber(amount)
          nameError = item == null && name.isBlank()
          amountError =
            if (amount.isBlank()) fromCalendar
            else q == null || !validQuantity(q, unit)
          if (!nameError && !amountError) onSave(name, q, if (q == null) null else unit, category)
        },
        modifier = Modifier.testTag("shop_item_save"),
      ) {
        Text(stringResource(if (item == null) R.string.action_add else R.string.action_save))
      }
    },
    dismissButton = {
      Row {
        if (onDelete != null) {
          TextButton(onClick = onDelete, modifier = Modifier.testTag("shop_item_delete")) { Text(stringResource(R.string.action_delete)) }
        }
        TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
      }
    },
  )
}
