package com.openweight.app.core.database.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.Update
import com.openweight.app.core.database.entity.IngredientEntity
import com.openweight.app.core.database.entity.RecipeEntity
import com.openweight.app.core.database.entity.RecipeIngredientEntity
import java.time.Instant
import kotlinx.coroutines.flow.Flow

data class RecipeIngredientRow(
  @Embedded val item: RecipeIngredientEntity,
  @Relation(parentColumn = "ingredientId", entityColumn = "id") val ingredient: IngredientEntity,
)

/** A recipe with its ingredient lines (unordered: sort by position). */
data class RecipeRow(
  @Embedded val recipe: RecipeEntity,
  @Relation(entity = RecipeIngredientEntity::class, parentColumn = "id", entityColumn = "recipeId")
  val items: List<RecipeIngredientRow>,
)

@Dao
interface RecipeDao {
  @Transaction
  @Query("SELECT * FROM recipe ORDER BY name COLLATE NOCASE")
  fun observeAll(): Flow<List<RecipeRow>>

  @Transaction
  @Query("SELECT * FROM recipe WHERE id = :id")
  fun observe(id: String): Flow<RecipeRow?>

  @Transaction
  @Query("SELECT * FROM recipe WHERE id = :id")
  suspend fun get(id: String): RecipeRow?

  @Query("SELECT * FROM recipe WHERE name = :name COLLATE NOCASE LIMIT 1")
  suspend fun findByName(name: String): RecipeEntity?

  /** Calendar meals and diet-plan meals that use the recipe. */
  @Query(
    """
    SELECT (SELECT COUNT(*) FROM meal_entry WHERE recipeId = :id) +
      (SELECT COUNT(*) FROM diet_plan_meal WHERE recipeId = :id)
    """
  )
  suspend fun countUsages(id: String): Int

  @Insert suspend fun insertRecipe(recipe: RecipeEntity)

  @Update suspend fun updateRecipe(recipe: RecipeEntity)

  @Query("UPDATE recipe SET updatedAt = :now WHERE id = :id")
  suspend fun touchRecipe(id: String, now: Instant)

  @Query("DELETE FROM recipe WHERE id = :id") suspend fun deleteRecipe(id: String)

  @Query("SELECT * FROM recipe_ingredient WHERE recipeId = :recipeId ORDER BY position")
  suspend fun getItems(recipeId: String): List<RecipeIngredientEntity>

  @Query("SELECT * FROM recipe_ingredient WHERE id = :id")
  suspend fun getItem(id: String): RecipeIngredientEntity?

  @Insert suspend fun insertItems(items: List<RecipeIngredientEntity>)

  @Update suspend fun updateItems(items: List<RecipeIngredientEntity>)

  @Query("DELETE FROM recipe_ingredient WHERE id = :id") suspend fun deleteItem(id: String)
}
