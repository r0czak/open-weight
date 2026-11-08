package com.openweight.app.diet.data

import androidx.room.withTransaction
import com.openweight.app.core.TimeAndIds
import com.openweight.app.core.database.OpenWeightDatabase
import com.openweight.app.core.database.dao.RecipeRow
import com.openweight.app.core.database.entity.IngredientEntity
import com.openweight.app.core.database.entity.RecipeEntity
import com.openweight.app.core.database.entity.RecipeIngredientEntity
import com.openweight.app.core.model.QuantityUnit
import com.openweight.app.workout.data.DeleteResult
import com.openweight.app.workout.data.DuplicateNameException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Ingredients and recipes: every editing rule lives here, not in the UI. */
@Singleton
class DietRepository
@Inject
constructor(private val db: OpenWeightDatabase, private val clock: TimeAndIds) {
  private val ingredientDao = db.ingredientDao()
  private val recipeDao = db.recipeDao()

  // ---- Ingredients ----

  fun ingredients(): Flow<List<IngredientListItem>> =
    ingredientDao.observeAllWithUsage().map { rows ->
      rows.map {
        val i = it.ingredient
        IngredientListItem(i.id, i.name, i.shopCategory, i.defaultUnit, i.kcalPer100, it.usageCount)
      }
    }

  /** Categories already used, then the presets, without duplicates. */
  fun shopCategories(): Flow<List<String>> =
    ingredientDao.observeCategories().map { used -> (used + SHOP_CATEGORIES).distinctBy { it.lowercase() } }

  suspend fun getIngredient(id: String): Ingredient? = ingredientDao.getById(id)?.toModel()

  /** Creates ([id] null) or updates an ingredient; names are unique, ignoring case. */
  suspend fun saveIngredient(id: String?, values: IngredientValues): String {
    val name = values.name.trim()
    require(name.isNotEmpty()) { "Name can't be empty" }
    return db.withTransaction {
      val sameName = ingredientDao.findByName(name)
      if (sameName != null && sameName.id != id) throw DuplicateNameException(name)
      val now = clock.now()
      val existing = id?.let { ingredientDao.getById(it) }
      val entity =
        IngredientEntity(
          id = existing?.id ?: id ?: clock.newId(),
          name = name,
          defaultUnit = values.defaultUnit,
          shopCategory = values.shopCategory.trim(),
          kcalPer100 = values.kcalPer100,
          proteinPer100 = values.proteinPer100,
          carbsPer100 = values.carbsPer100,
          fatPer100 = values.fatPer100,
          gramsPerPiece = values.gramsPerPiece,
          createdAt = existing?.createdAt ?: now,
          updatedAt = now,
        )
      if (existing == null) ingredientDao.insert(entity) else ingredientDao.update(entity)
      entity.id
    }
  }

  /** An ingredient used in a recipe is kept; the user removes it from those recipes first. */
  suspend fun deleteIngredient(id: String): DeleteResult =
    db.withTransaction {
      val usage = ingredientDao.countRecipesUsing(id)
      if (usage > 0) DeleteResult.InUse(usage)
      else {
        ingredientDao.delete(id)
        DeleteResult.Deleted
      }
    }

  // ---- Recipes ----

  fun recipes(): Flow<List<RecipeSummary>> =
    recipeDao.observeAll().map { rows ->
      rows.map { row ->
        val r = row.recipe
        RecipeSummary(r.id, r.name, r.servings, r.prepMinutes, r.tags, row.items.size, nutritionOf(row))
      }
    }

  fun recipe(id: String): Flow<RecipeDetail?> = recipeDao.observe(id).map { it?.toDetail() }

  suspend fun createRecipe(info: RecipeInfo): String {
    val now = clock.now()
    val id = clock.newId()
    recipeDao.insertRecipe(
      RecipeEntity(
        id = id,
        name = info.name.trim(),
        servings = info.servings,
        prepMinutes = info.prepMinutes,
        steps = info.steps.trim(),
        tags = info.tags.trim(),
        notes = info.notes.trim(),
        createdAt = now,
        updatedAt = now,
      )
    )
    return id
  }

  suspend fun updateRecipeInfo(id: String, info: RecipeInfo) {
    db.withTransaction {
      val r = recipeDao.get(id)?.recipe ?: return@withTransaction
      recipeDao.updateRecipe(
        r.copy(
          name = info.name.trim(),
          servings = info.servings,
          prepMinutes = info.prepMinutes,
          steps = info.steps.trim(),
          tags = info.tags.trim(),
          notes = info.notes.trim(),
          updatedAt = clock.now(),
        )
      )
    }
  }

  /** Copies a recipe with its ingredient lines; returns the new recipe's id. */
  suspend fun duplicateRecipe(id: String): String =
    db.withTransaction {
      val row = requireNotNull(recipeDao.get(id)) { "Recipe not found" }
      val now = clock.now()
      val newId = clock.newId()
      recipeDao.insertRecipe(row.recipe.copy(id = newId, name = "${row.recipe.name} (copy)", createdAt = now, updatedAt = now))
      recipeDao.insertItems(
        row.items.map { it.item.copy(id = clock.newId(), recipeId = newId, createdAt = now, updatedAt = now) }
      )
      newId
    }

  /** A recipe on the calendar or in a diet plan is kept; the user removes it there first. */
  suspend fun deleteRecipe(id: String): DeleteResult =
    db.withTransaction {
      val usage = recipeDao.countUsages(id)
      if (usage > 0) DeleteResult.InUse(usage)
      else {
        recipeDao.deleteRecipe(id)
        DeleteResult.Deleted
      }
    }

  suspend fun addItem(recipeId: String, ingredientId: String, quantity: Double, unit: QuantityUnit): String {
    require(validQuantity(quantity, unit)) { "Quantity out of range" }
    return db.withTransaction {
      val now = clock.now()
      val id = clock.newId()
      val position = (recipeDao.getItems(recipeId).maxOfOrNull { it.position } ?: -1) + 1
      recipeDao.insertItems(listOf(RecipeIngredientEntity(id, recipeId, ingredientId, position, quantity, unit, now, now)))
      recipeDao.touchRecipe(recipeId, now)
      id
    }
  }

  suspend fun updateItem(itemId: String, quantity: Double, unit: QuantityUnit) {
    require(validQuantity(quantity, unit)) { "Quantity out of range" }
    db.withTransaction {
      val item = recipeDao.getItem(itemId) ?: return@withTransaction
      val now = clock.now()
      recipeDao.updateItems(listOf(item.copy(quantity = quantity, unit = unit, updatedAt = now)))
      recipeDao.touchRecipe(item.recipeId, now)
    }
  }

  suspend fun removeItem(itemId: String) {
    db.withTransaction {
      val item = recipeDao.getItem(itemId) ?: return@withTransaction
      recipeDao.deleteItem(itemId)
      renumber(item.recipeId)
      recipeDao.touchRecipe(item.recipeId, clock.now())
    }
  }

  suspend fun moveItem(itemId: String, delta: Int) {
    db.withTransaction {
      val item = recipeDao.getItem(itemId) ?: return@withTransaction
      val items = recipeDao.getItems(item.recipeId).toMutableList()
      val from = items.indexOfFirst { it.id == itemId }
      val to = (from + delta).coerceIn(0, items.lastIndex)
      if (from == to) return@withTransaction
      items.add(to, items.removeAt(from))
      val now = clock.now()
      recipeDao.updateItems(items.mapIndexedNotNull { i, it -> if (it.position == i) null else it.copy(position = i, updatedAt = now) })
      recipeDao.touchRecipe(item.recipeId, now)
    }
  }

  private suspend fun renumber(recipeId: String) {
    val now = clock.now()
    recipeDao.updateItems(
      recipeDao.getItems(recipeId).mapIndexedNotNull { i, it -> if (it.position == i) null else it.copy(position = i, updatedAt = now) }
    )
  }

  // ---- Mapping ----

  private fun nutritionOf(row: RecipeRow): RecipeNutrition =
    NutritionCalculator.forRecipe(
      row.items.sortedBy { it.item.position }.map { it.ingredient.name to Triple(it.item.quantity, it.item.unit, it.ingredient.nutrition()) },
      row.recipe.servings,
    )

  private fun RecipeRow.toDetail(): RecipeDetail {
    val sorted = items.sortedBy { it.item.position }
    return RecipeDetail(
      id = recipe.id,
      name = recipe.name,
      servings = recipe.servings,
      prepMinutes = recipe.prepMinutes,
      steps = recipe.steps,
      tags = recipe.tags,
      notes = recipe.notes,
      items =
        sorted.map {
          RecipeItem(
            id = it.item.id,
            ingredientId = it.ingredient.id,
            name = it.ingredient.name,
            quantity = it.item.quantity,
            unit = it.item.unit,
            macros = NutritionCalculator.forAmount(it.item.quantity, it.item.unit, it.ingredient.nutrition()),
          )
        },
      nutrition = nutritionOf(this),
    )
  }
}

internal fun IngredientEntity.nutrition() =
  IngredientNutrition(
    per100 = kcalPer100?.let { Macros(it, proteinPer100 ?: 0.0, carbsPer100 ?: 0.0, fatPer100 ?: 0.0) },
    defaultUnit = defaultUnit,
    gramsPerPiece = gramsPerPiece,
  )

private fun IngredientEntity.toModel() =
  Ingredient(id, name, shopCategory, defaultUnit, kcalPer100, proteinPer100, carbsPer100, fatPer100, gramsPerPiece)
