package com.openweight.app.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.openweight.app.core.database.entity.BlockScheduleDayEntity
import com.openweight.app.core.database.entity.BlockWorkoutEntity
import com.openweight.app.core.database.entity.DietPlanEntity
import com.openweight.app.core.database.entity.DietPlanMealEntity
import com.openweight.app.core.database.entity.ExerciseEntity
import com.openweight.app.core.database.entity.IngredientEntity
import com.openweight.app.core.database.entity.MealEntryEntity
import com.openweight.app.core.database.entity.RecipeEntity
import com.openweight.app.core.database.entity.RecipeIngredientEntity
import com.openweight.app.core.database.entity.SetPlanEntity
import com.openweight.app.core.database.entity.ShoppingItemEntity
import com.openweight.app.core.database.entity.ShoppingListEntity
import com.openweight.app.core.database.entity.TrainingBlockEntity
import com.openweight.app.core.database.entity.WorkoutEntity
import com.openweight.app.core.database.entity.WorkoutExerciseEntity

/**
 * The app's single on-device database. Its schema is exported to `app/schemas/` and committed:
 * every version bump needs a migration and a test that upgrades from the previous version.
 */
@Database(
  version = 1,
  exportSchema = true,
  entities =
    [
      // Workout
      ExerciseEntity::class,
      WorkoutEntity::class,
      WorkoutExerciseEntity::class,
      SetPlanEntity::class,
      TrainingBlockEntity::class,
      BlockWorkoutEntity::class,
      BlockScheduleDayEntity::class,
      // Diet
      IngredientEntity::class,
      RecipeEntity::class,
      RecipeIngredientEntity::class,
      DietPlanEntity::class,
      DietPlanMealEntity::class,
      MealEntryEntity::class,
      ShoppingListEntity::class,
      ShoppingItemEntity::class,
    ],
)
@TypeConverters(Converters::class)
abstract class OpenWeightDatabase : RoomDatabase() {
  companion object {
    const val NAME = "open-weight.db"
  }
}
