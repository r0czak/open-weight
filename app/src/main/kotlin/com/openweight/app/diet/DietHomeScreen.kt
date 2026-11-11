package com.openweight.app.diet

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.openweight.app.R
import com.openweight.app.core.ui.PillTabs
import com.openweight.app.core.Today
import com.openweight.app.core.ui.HeroCaption
import com.openweight.app.core.ui.HeroHeader
import com.openweight.app.core.ui.HeroLabel
import com.openweight.app.core.ui.HeroProgress
import com.openweight.app.core.ui.HeroTitle
import com.openweight.app.core.ui.HeroValue
import com.openweight.app.diet.data.DayMeals
import com.openweight.app.diet.data.Macros
import com.openweight.app.diet.data.MealPlanRepository
import com.openweight.app.settings.SettingsRepository
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import com.openweight.app.core.model.MealSlot
import com.openweight.app.diet.calendar.CalendarPanel
import com.openweight.app.diet.recipes.RecipesPanel
import com.openweight.app.diet.shopping.ShoppingPanel
import java.time.LocalDate

/** The three diet panels from the spec. */
enum class DietPanel(val title: Int) {
  RECIPES(R.string.diet_recipes),
  CALENDAR(R.string.diet_calendar),
  SHOPPING(R.string.diet_shopping),
}

/** Today's eating, for the Diet tab's header. */
data class DietToday(val loaded: Boolean = false, val day: DayMeals? = null, val kcalTarget: Int? = null, val proteinTarget: Int? = null)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DietHomeViewModel @Inject constructor(meals: MealPlanRepository, settings: SettingsRepository, today: Today) : ViewModel() {
  val today: StateFlow<DietToday> =
    combine(today.date.flatMapLatest { meals.days(it, it) }, settings.settings) { days, s -> DietToday(true, days.firstOrNull(), s.kcalTarget, s.proteinTarget) }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DietToday())
}

@Composable
fun DietHomeScreen(
  modifier: Modifier = Modifier,
  onOpenRecipe: (String) -> Unit = {},
  onCreateRecipe: () -> Unit = {},
  onOpenIngredients: () -> Unit = {},
  onAddMeal: (LocalDate, MealSlot) -> Unit = { _, _ -> },
  onSwapMeal: (mealId: String, date: LocalDate, slot: MealSlot) -> Unit = { _, _, _ -> },
  onOpenPlans: () -> Unit = {},
  viewModel: DietHomeViewModel = hiltViewModel(),
) {
  val today by viewModel.today.collectAsStateWithLifecycle()
  var selected by rememberSaveable { mutableIntStateOf(0) }
  var addingShopItem by rememberSaveable { mutableStateOf(false) }
  val snackbar = remember { SnackbarHostState() }
  Scaffold(
    modifier = modifier,
    contentWindowInsets = WindowInsets(0, 0, 0, 0),
    snackbarHost = { SnackbarHost(snackbar) },
    floatingActionButton = {
      when (DietPanel.entries[selected]) {
        DietPanel.RECIPES ->
          FloatingActionButton(onClick = onCreateRecipe, modifier = Modifier.testTag("add_recipe")) {
            Icon(Icons.Filled.Add, stringResource(R.string.recipe_new))
          }
        DietPanel.SHOPPING ->
          FloatingActionButton(onClick = { addingShopItem = true }, modifier = Modifier.testTag("add_shopping_item")) {
            Icon(Icons.Filled.Add, stringResource(R.string.shopping_add_item))
          }
        DietPanel.CALENDAR -> Unit
      }
    },
  ) { padding ->
    val header =
      PanelHeader(
        hero = { DietHero(today) },
        tabs = {
          PillTabs(
            labels = DietPanel.entries.map { stringResource(it.title) },
            selected = selected,
            onSelect = { selected = it },
            tags = DietPanel.entries.map { "panel_${it.name}" },
          )
        },
      )
    Column(Modifier.fillMaxSize().padding(padding)) {
      when (DietPanel.entries[selected]) {
        DietPanel.RECIPES -> RecipesPanel(onOpenRecipe = onOpenRecipe, onOpenIngredients = onOpenIngredients, header = header)
        DietPanel.CALENDAR ->
          CalendarPanel(snackbar = snackbar, onAddMeal = onAddMeal, onOpenRecipe = onOpenRecipe, onOpenPlans = onOpenPlans, onSwapMeal = onSwapMeal, header = header)
        DietPanel.SHOPPING -> ShoppingPanel(snackbar = snackbar, adding = addingShopItem, onAddingDone = { addingShopItem = false }, header = header)
      }
    }
  }
}


/** The Diet tab's header: today's calories eaten against the target (or what is planned), and protein. */
@Composable
private fun DietHero(today: DietToday) {
  HeroHeader {
    HeroTitle(stringResource(R.string.tab_diet))
    HeroLabel(stringResource(R.string.diet_hero_today))
    val day = today.day
    if (!today.loaded) {
      HeroValue(" ")
      return@HeroHeader
    }
    val eaten = day?.eaten ?: Macros.ZERO
    val planned = day?.total ?: Macros.ZERO
    val kcalTarget = today.kcalTarget
    if (kcalTarget != null || (day?.anyEaten == true)) {
      HeroValue(formatKcalNumber(eaten.kcal), unit = stringResource(R.string.diet_hero_of, formatKcal(kcalTarget?.toDouble() ?: planned.kcal)))
      val goal = kcalTarget?.toDouble() ?: planned.kcal
      HeroProgress(if (goal > 0) (eaten.kcal / goal).toFloat() else 0f, Modifier.padding(top = 12.dp))
    } else {
      HeroValue(formatKcalNumber(planned.kcal), unit = stringResource(R.string.diet_hero_planned))
    }
    val proteinTarget = today.proteinTarget
    HeroCaption(
      if (proteinTarget != null) stringResource(R.string.diet_hero_protein_of, formatGrams(eaten.protein), formatGrams(proteinTarget.toDouble()))
      else stringResource(R.string.diet_hero_protein, formatGrams(planned.protein)),
      Modifier.padding(top = 10.dp),
    )
  }
}

/**
 * The top of the Diet tab, shown inside each panel's list: the hero scrolls away with the list and
 * the pill tabs stick to the top, so switching panels stays one tap away.
 */
class PanelHeader(val hero: @Composable () -> Unit, val tabs: @Composable () -> Unit)

@OptIn(ExperimentalFoundationApi::class)
fun LazyListScope.panelHeader(header: PanelHeader?) {
  if (header == null) return
  item(key = "diet_hero") { header.hero() }
  stickyHeader(key = "diet_tabs") { Box(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background)) { header.tabs() } }
}
