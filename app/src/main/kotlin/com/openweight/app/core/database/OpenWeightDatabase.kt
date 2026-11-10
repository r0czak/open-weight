package com.openweight.app.core.database

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RenameColumn
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.AutoMigrationSpec
import com.openweight.app.core.database.dao.BodyWeightDao
import com.openweight.app.core.database.dao.ExerciseDao
import com.openweight.app.core.database.dao.IngredientDao
import com.openweight.app.core.database.dao.MealPlanDao
import com.openweight.app.core.database.dao.RecipeDao
import com.openweight.app.core.database.dao.SessionDao
import com.openweight.app.core.database.dao.ShoppingDao
import com.openweight.app.core.database.dao.TransferDao
import com.openweight.app.core.database.dao.TrainingBlockDao
import com.openweight.app.core.database.dao.WorkoutDao
import com.openweight.app.core.database.entity.BlockScheduleDayEntity
import com.openweight.app.core.database.entity.BodyWeightEntity
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
import com.openweight.app.core.database.entity.SessionSetEntity
import com.openweight.app.core.database.entity.WorkoutEntity
import com.openweight.app.core.database.entity.WorkoutSessionEntity
import com.openweight.app.core.database.entity.WorkoutExerciseEntity

/**
 * The app's single on-device database. Its schema is exported to `app/schemas/` and committed:
 * every version bump needs a migration and a test that upgrades from the previous version.
 */
@Database(
  version = 5,
  exportSchema = true,
  autoMigrations =
    [
      AutoMigration(from = 1, to = 2, spec = OpenWeightDatabase.Migration1To2::class),
      // v3: ingredient.gramsPerPiece (nullable), so pieces count towards macros.
      AutoMigration(from = 2, to = 3),
      // v4: live training (workout_session, session_set).
      AutoMigration(from = 3, to = 4),
      // v5: body weight.
      AutoMigration(from = 4, to = 5),
    ],
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
      // Progress
      WorkoutSessionEntity::class,
      SessionSetEntity::class,
      BodyWeightEntity::class,
    ],
)
@TypeConverters(Converters::class)
abstract class OpenWeightDatabase : RoomDatabase() {
  abstract fun exerciseDao(): ExerciseDao

  abstract fun workoutDao(): WorkoutDao

  abstract fun trainingBlockDao(): TrainingBlockDao

  abstract fun ingredientDao(): IngredientDao

  abstract fun recipeDao(): RecipeDao

  abstract fun mealPlanDao(): MealPlanDao

  abstract fun shoppingDao(): ShoppingDao

  abstract fun transferDao(): TransferDao

  abstract fun sessionDao(): SessionDao

  abstract fun bodyWeightDao(): BodyWeightDao

  /** v2: a set's target became a rep range (`reps` → `repsMin`, plus optional `repsMax`). */
  @RenameColumn(tableName = "set_plan", fromColumnName = "reps", toColumnName = "repsMin")
  class Migration1To2 : AutoMigrationSpec

  companion object {
    const val NAME = "open-weight.db"
  }
}
