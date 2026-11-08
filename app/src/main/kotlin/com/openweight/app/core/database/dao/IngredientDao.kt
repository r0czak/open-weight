package com.openweight.app.core.database.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.openweight.app.core.database.entity.IngredientEntity
import kotlinx.coroutines.flow.Flow

/** An ingredient plus how many recipes use it, for the ingredient list. */
data class IngredientWithUsage(@Embedded val ingredient: IngredientEntity, val usageCount: Int)

@Dao
interface IngredientDao {
  @Query(
    """
    SELECT i.*,
      (SELECT COUNT(DISTINCT ri.recipeId) FROM recipe_ingredient ri WHERE ri.ingredientId = i.id) AS usageCount
    FROM ingredient i
    ORDER BY i.name COLLATE NOCASE
    """
  )
  fun observeAllWithUsage(): Flow<List<IngredientWithUsage>>

  @Query("SELECT DISTINCT shopCategory FROM ingredient WHERE shopCategory != '' ORDER BY shopCategory COLLATE NOCASE")
  fun observeCategories(): Flow<List<String>>

  @Query("SELECT * FROM ingredient WHERE id = :id") suspend fun getById(id: String): IngredientEntity?

  @Query("SELECT * FROM ingredient WHERE name = :name COLLATE NOCASE LIMIT 1")
  suspend fun findByName(name: String): IngredientEntity?

  @Query("SELECT COUNT(DISTINCT recipeId) FROM recipe_ingredient WHERE ingredientId = :id")
  suspend fun countRecipesUsing(id: String): Int

  @Insert suspend fun insert(ingredient: IngredientEntity)

  @Update suspend fun update(ingredient: IngredientEntity)

  @Query("DELETE FROM ingredient WHERE id = :id") suspend fun delete(id: String)
}
