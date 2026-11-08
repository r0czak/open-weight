package com.openweight.app.core.database

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RenameColumn
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.AutoMigrationSpec
import com.openweight.app.core.database.dao.ExerciseDao
import com.openweight.app.core.database.dao.TrainingBlockDao
import com.openweight.app.core.database.dao.WorkoutDao
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
  version = 2,
  exportSchema = true,
  autoMigrations = [AutoMigration(from = 1, to = 2, spec = OpenWeightDatabase.Migration1To2::class)],
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
  abstract fun exerciseDao(): ExerciseDao

  abstract fun workoutDao(): WorkoutDao

  abstract fun trainingBlockDao(): TrainingBlockDao

  /** v2: a set's target became a rep range (`reps` → `repsMin`, plus optional `repsMax`). */
  @RenameColumn(tableName = "set_plan", fromColumnName = "reps", toColumnName = "repsMin")
  class Migration1To2 : AutoMigrationSpec

  companion object {
    const val NAME = "open-weight.db"
  }
}
