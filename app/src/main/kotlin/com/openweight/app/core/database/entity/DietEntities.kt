package com.openweight.app.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.openweight.app.core.model.MealSlot
import com.openweight.app.core.model.QuantityUnit
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate

/**
 * Nutrition values are optional and given per 100 g, or per 100 ml when [defaultUnit] is a volume.
 * [gramsPerPiece] lets amounts in pieces ("2 eggs") count towards calories and macros.
 */
@Entity(tableName = "ingredient", indices = [Index("name")])
data class IngredientEntity(
  @PrimaryKey val id: String,
  val name: String,
  val defaultUnit: QuantityUnit = QuantityUnit.G,
  val shopCategory: String = "",
  val kcalPer100: Double? = null,
  val proteinPer100: Double? = null,
  val carbsPer100: Double? = null,
  val fatPer100: Double? = null,
  val gramsPerPiece: Double? = null,
  val createdAt: Instant,
  val updatedAt: Instant,
)

@Entity(tableName = "recipe", indices = [Index("name")])
data class RecipeEntity(
  @PrimaryKey val id: String,
  val name: String,
  val servings: Int = 1,
  val prepMinutes: Int? = null,
  val steps: String = "",
  val tags: String = "",
  val notes: String = "",
  val createdAt: Instant,
  val updatedAt: Instant,
)

@Entity(
  tableName = "recipe_ingredient",
  foreignKeys =
    [
      ForeignKey(
        entity = RecipeEntity::class,
        parentColumns = ["id"],
        childColumns = ["recipeId"],
        onDelete = ForeignKey.CASCADE,
      ),
      ForeignKey(
        entity = IngredientEntity::class,
        parentColumns = ["id"],
        childColumns = ["ingredientId"],
        onDelete = ForeignKey.RESTRICT,
      ),
    ],
  indices = [Index("recipeId"), Index("ingredientId")],
)
data class RecipeIngredientEntity(
  @PrimaryKey val id: String,
  val recipeId: String,
  val ingredientId: String,
  val position: Int,
  val quantity: Double,
  val unit: QuantityUnit,
  val createdAt: Instant,
  val updatedAt: Instant,
)

/** A named, repeating week of meals that can be applied to a date range of the calendar. */
@Entity(tableName = "diet_plan", indices = [Index("name")])
data class DietPlanEntity(
  @PrimaryKey val id: String,
  val name: String,
  val notes: String = "",
  val createdAt: Instant,
  val updatedAt: Instant,
)

@Entity(
  tableName = "diet_plan_meal",
  foreignKeys =
    [
      ForeignKey(
        entity = DietPlanEntity::class,
        parentColumns = ["id"],
        childColumns = ["dietPlanId"],
        onDelete = ForeignKey.CASCADE,
      ),
      ForeignKey(
        entity = RecipeEntity::class,
        parentColumns = ["id"],
        childColumns = ["recipeId"],
        onDelete = ForeignKey.RESTRICT,
      ),
    ],
  indices = [Index("dietPlanId"), Index("recipeId")],
)
data class DietPlanMealEntity(
  @PrimaryKey val id: String,
  val dietPlanId: String,
  val dayOfWeek: DayOfWeek,
  val slot: MealSlot,
  val recipeId: String,
  val servings: Double,
  val createdAt: Instant,
  val updatedAt: Instant,
)

/** A meal on the calendar. [dietPlanId] is set when the entry was filled in from a diet plan. */
@Entity(
  tableName = "meal_entry",
  foreignKeys =
    [
      ForeignKey(
        entity = RecipeEntity::class,
        parentColumns = ["id"],
        childColumns = ["recipeId"],
        onDelete = ForeignKey.RESTRICT,
      ),
      ForeignKey(
        entity = DietPlanEntity::class,
        parentColumns = ["id"],
        childColumns = ["dietPlanId"],
        onDelete = ForeignKey.SET_NULL,
      ),
    ],
  indices = [Index("date"), Index("recipeId"), Index("dietPlanId")],
)
data class MealEntryEntity(
  @PrimaryKey val id: String,
  val date: LocalDate,
  val slot: MealSlot,
  val recipeId: String,
  val servings: Double,
  val dietPlanId: String? = null,
  val createdAt: Instant,
  val updatedAt: Instant,
)

@Entity(tableName = "shopping_list")
data class ShoppingListEntity(
  @PrimaryKey val id: String,
  val name: String,
  val fromDate: LocalDate,
  val toDate: LocalDate,
  val generatedAt: Instant,
  val createdAt: Instant,
  val updatedAt: Instant,
)

@Entity(
  tableName = "shopping_item",
  foreignKeys =
    [
      ForeignKey(
        entity = ShoppingListEntity::class,
        parentColumns = ["id"],
        childColumns = ["listId"],
        onDelete = ForeignKey.CASCADE,
      ),
      ForeignKey(
        entity = IngredientEntity::class,
        parentColumns = ["id"],
        childColumns = ["ingredientId"],
        onDelete = ForeignKey.SET_NULL,
      ),
    ],
  indices = [Index("listId"), Index("ingredientId")],
)
data class ShoppingItemEntity(
  @PrimaryKey val id: String,
  val listId: String,
  val ingredientId: String?,
  val name: String,
  val quantity: Double?,
  val unit: QuantityUnit?,
  val category: String = "",
  val checked: Boolean = false,
  val isManual: Boolean = false,
  val isQuantityEdited: Boolean = false,
  val createdAt: Instant,
  val updatedAt: Instant,
)
