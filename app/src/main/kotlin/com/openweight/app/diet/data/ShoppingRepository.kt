package com.openweight.app.diet.data

import androidx.room.withTransaction
import com.openweight.app.core.TimeAndIds
import com.openweight.app.core.database.OpenWeightDatabase
import com.openweight.app.core.database.entity.ShoppingItemEntity
import com.openweight.app.core.database.entity.ShoppingListEntity
import com.openweight.app.core.model.QuantityUnit
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

data class ShoppingItem(
  val id: String,
  val name: String,
  val quantity: Double?,
  val unit: QuantityUnit?,
  val category: String,
  val checked: Boolean,
  val isManual: Boolean,
  /** The amount was changed by hand; updating from the calendar keeps it. */
  val isQuantityEdited: Boolean,
)

data class ShoppingListView(
  val id: String,
  val from: LocalDate,
  val to: LocalDate,
  val generatedAt: Instant,
  val items: List<ShoppingItem>,
) {
  /** Items grouped by shop category (alphabetical, "Other" last), each sorted by name. */
  fun byCategory(other: String): List<Pair<String, List<ShoppingItem>>> =
    items
      .groupBy { it.category.ifBlank { other } }
      .toList()
      .sortedWith(compareBy({ it.first == other }, { it.first.lowercase() }))
      .map { (c, list) -> c to list.sortedBy { it.name.lowercase() } }
}

/** What an update from the calendar changed. */
data class GenerateResult(val added: Int, val updated: Int, val removed: Int, val kept: Int)

/** The shopping list, built from the meal calendar and edited by hand. Rules live here. */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class ShoppingRepository
@Inject
constructor(private val db: OpenWeightDatabase, private val clock: TimeAndIds) {
  private val dao = db.shoppingDao()

  fun current(): Flow<ShoppingListView?> =
    dao.observeCurrentList().flatMapLatest { list ->
      if (list == null) flowOf(null)
      else
        dao.observeItems(list.id).map { items ->
          ShoppingListView(list.id, list.fromDate, list.toDate, list.generatedAt, items.map { it.toModel() })
        }
    }

  /**
   * Builds the list for [from]–[to] from the meals on the calendar, or updates the current one.
   * Items added by hand stay; ticks stay on items still needed; amounts changed by hand are kept;
   * ingredients no longer needed are removed. One transaction.
   */
  suspend fun generate(from: LocalDate, to: LocalDate): GenerateResult {
    require(!to.isBefore(from)) { "The range must end on or after its start" }
    require(ChronoUnit.DAYS.between(from, to) < MAX_DAYS) { "At most $MAX_DAYS days" }
    return db.withTransaction {
      val needed = ShoppingListBuilder.build(mealsFor(from, to))
      val now = clock.now()
      val list =
        dao.currentList()?.also { dao.updateList(it.copy(fromDate = from, toDate = to, generatedAt = now, updatedAt = now)) }
          ?: ShoppingListEntity(clock.newId(), "Shopping list", from, to, now, now, now).also { dao.insertList(it) }

      val existing = dao.getItems(list.id).filter { !it.isManual }
      val byKey = existing.associateBy { it.ingredientId to it.unit?.dimension }
      val toInsert = mutableListOf<ShoppingItemEntity>()
      val toUpdate = mutableListOf<ShoppingItemEntity>()
      var kept = 0
      for (n in needed) {
        val old = byKey[n.ingredientId to n.dimension]
        when {
          old == null ->
            toInsert += ShoppingItemEntity(clock.newId(), list.id, n.ingredientId, n.name, n.quantity, n.unit, n.category, createdAt = now, updatedAt = now)
          old.isQuantityEdited -> {
            kept++
            toUpdate += old.copy(name = n.name, category = n.category, updatedAt = now)
          }
          else -> toUpdate += old.copy(name = n.name, quantity = n.quantity, unit = n.unit, category = n.category, updatedAt = now)
        }
      }
      val neededKeys = needed.map { it.ingredientId to it.dimension }.toSet()
      val stale = existing.filter { (it.ingredientId to it.unit?.dimension) !in neededKeys }
      dao.insertItems(toInsert)
      dao.updateItems(toUpdate)
      if (stale.isNotEmpty()) dao.deleteItems(stale.map { it.id })
      GenerateResult(added = toInsert.size, updated = toUpdate.size - kept, removed = stale.size, kept = kept)
    }
  }

  private suspend fun mealsFor(from: LocalDate, to: LocalDate): List<MealForShopping> {
    val recipes = db.recipeDao()
    val cache = mutableMapOf<String, com.openweight.app.core.database.dao.RecipeRow?>()
    return db.mealPlanDao().getMeals(from, to).mapNotNull { meal ->
      val row = cache.getOrPut(meal.recipeId) { recipes.get(meal.recipeId) } ?: return@mapNotNull null
      MealForShopping(
        recipeServings = row.recipe.servings,
        servings = meal.servings,
        lines = row.items.map { RecipeLine(it.ingredient.id, it.ingredient.name, it.ingredient.shopCategory, it.item.quantity, it.item.unit) },
      )
    }
  }

  suspend fun setChecked(itemId: String, checked: Boolean) {
    db.withTransaction {
      val item = dao.getItem(itemId) ?: return@withTransaction
      dao.updateItems(listOf(item.copy(checked = checked, updatedAt = clock.now())))
    }
  }

  /** Adds an item by hand. With no list yet, starts one for today. */
  suspend fun addManual(name: String, quantity: Double?, unit: QuantityUnit?, category: String): String {
    val clean = name.trim()
    require(clean.isNotEmpty()) { "Name can't be empty" }
    require(quantity == null || (unit != null && validQuantity(quantity, unit))) { "Quantity out of range" }
    return db.withTransaction {
      val now = clock.now()
      val list =
        dao.currentList()
          ?: ShoppingListEntity(clock.newId(), "Shopping list", clock.today(), clock.today(), now, now, now).also { dao.insertList(it) }
      val id = clock.newId()
      dao.insertItems(
        listOf(ShoppingItemEntity(id, list.id, null, clean, quantity, if (quantity == null) null else unit, category.trim(), isManual = true, createdAt = now, updatedAt = now))
      )
      id
    }
  }

  /**
   * Changes an amount by hand; for calendar items, updates then keep it ("changed by you"). A
   * calendar item needs an amount in the same kind of unit, so a later update can still match it.
   */
  suspend fun editQuantity(itemId: String, quantity: Double?, unit: QuantityUnit?) {
    require(quantity == null || (unit != null && validQuantity(quantity, unit))) { "Quantity out of range" }
    db.withTransaction {
      val item = dao.getItem(itemId) ?: return@withTransaction
      if (!item.isManual) {
        require(quantity != null && unit?.dimension == item.unit?.dimension) { "Use an amount in a unit of the same kind" }
      }
      dao.updateItems(
        listOf(item.copy(quantity = quantity, unit = if (quantity == null) null else unit, isQuantityEdited = !item.isManual, updatedAt = clock.now()))
      )
    }
  }

  suspend fun deleteItem(itemId: String) = dao.deleteItems(listOf(itemId))

  suspend fun uncheckAll() {
    dao.currentList()?.let { dao.uncheckAll(it.id) }
  }

  companion object {
    const val MAX_DAYS = 62
  }
}

private fun ShoppingItemEntity.toModel() =
  ShoppingItem(id, name, quantity, unit, category, checked, isManual, isQuantityEdited)
