package com.openweight.app.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.openweight.app.core.database.entity.BlockScheduleDayEntity
import com.openweight.app.core.database.entity.BlockWorkoutEntity
import com.openweight.app.core.database.entity.BodyWeightEntity
import com.openweight.app.core.database.entity.HouseholdMemberEntity
import com.openweight.app.core.database.entity.MealMemberEntity
import com.openweight.app.core.database.entity.SessionSetEntity
import com.openweight.app.core.database.entity.WorkoutSessionEntity
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
 * Whole-table reads and raw writes for import and export. The app's data is small (a person's
 * plans), so exports read whole tables and filter in memory.
 */
@Dao
interface TransferDao {
  // Reads, children in position order.
  @Query("SELECT * FROM exercise") suspend fun exercises(): List<ExerciseEntity>
  @Query("SELECT * FROM workout") suspend fun workouts(): List<WorkoutEntity>
  @Query("SELECT * FROM workout_exercise ORDER BY position") suspend fun workoutExercises(): List<WorkoutExerciseEntity>
  @Query("SELECT * FROM set_plan ORDER BY position") suspend fun setPlans(): List<SetPlanEntity>
  @Query("SELECT * FROM training_block") suspend fun blocks(): List<TrainingBlockEntity>
  @Query("SELECT * FROM block_workout ORDER BY position") suspend fun blockWorkouts(): List<BlockWorkoutEntity>
  @Query("SELECT * FROM block_schedule_day") suspend fun scheduleDays(): List<BlockScheduleDayEntity>
  @Query("SELECT * FROM ingredient") suspend fun ingredients(): List<IngredientEntity>
  @Query("SELECT * FROM recipe") suspend fun recipes(): List<RecipeEntity>
  @Query("SELECT * FROM recipe_ingredient ORDER BY position") suspend fun recipeLines(): List<RecipeIngredientEntity>
  @Query("SELECT * FROM diet_plan") suspend fun dietPlans(): List<DietPlanEntity>
  @Query("SELECT * FROM diet_plan_meal ORDER BY createdAt") suspend fun planMeals(): List<DietPlanMealEntity>
  @Query("SELECT * FROM meal_entry ORDER BY date") suspend fun mealEntries(): List<MealEntryEntity>
  @Query("SELECT * FROM shopping_list") suspend fun shoppingLists(): List<ShoppingListEntity>
  @Query("SELECT * FROM shopping_item ORDER BY name COLLATE NOCASE") suspend fun shoppingItems(): List<ShoppingItemEntity>
  @Query("SELECT * FROM workout_session WHERE finishedAt IS NOT NULL ORDER BY finishedAt") suspend fun finishedSessions(): List<WorkoutSessionEntity>
  @Query("SELECT * FROM workout_session") suspend fun allSessions(): List<WorkoutSessionEntity>
  @Query("SELECT * FROM session_set ORDER BY exercisePosition, setPosition") suspend fun sessionSets(): List<SessionSetEntity>
  @Query("SELECT * FROM body_weight ORDER BY date") suspend fun bodyWeights(): List<BodyWeightEntity>
  @Query("SELECT * FROM household_member ORDER BY createdAt") suspend fun householdMembers(): List<HouseholdMemberEntity>
  @Query("SELECT * FROM meal_member") suspend fun mealMembers(): List<MealMemberEntity>

  // Top-level records: insert, or overwrite in place (an update keeps links pointing at the row).
  @Insert suspend fun insert(row: ExerciseEntity)
  @Update suspend fun update(row: ExerciseEntity)
  @Insert suspend fun insert(row: WorkoutEntity)
  @Update suspend fun update(row: WorkoutEntity)
  @Insert suspend fun insert(row: TrainingBlockEntity)
  @Update suspend fun update(row: TrainingBlockEntity)
  @Insert suspend fun insert(row: IngredientEntity)
  @Update suspend fun update(row: IngredientEntity)
  @Insert suspend fun insert(row: RecipeEntity)
  @Update suspend fun update(row: RecipeEntity)
  @Insert suspend fun insert(row: DietPlanEntity)
  @Update suspend fun update(row: DietPlanEntity)
  @Insert suspend fun insert(row: MealEntryEntity)
  @Update suspend fun update(row: MealEntryEntity)
  @Insert suspend fun insert(row: ShoppingListEntity)
  @Update suspend fun update(row: ShoppingListEntity)
  @Insert suspend fun insert(row: WorkoutSessionEntity)
  @Update suspend fun update(row: WorkoutSessionEntity)
  @Insert suspend fun insert(row: BodyWeightEntity)
  @Update suspend fun update(row: BodyWeightEntity)
  @Insert suspend fun insert(row: HouseholdMemberEntity)
  @Update suspend fun update(row: HouseholdMemberEntity)

  // Children are always written fresh.
  @Insert suspend fun insertWorkoutExercises(rows: List<WorkoutExerciseEntity>)
  @Insert suspend fun insertSetPlans(rows: List<SetPlanEntity>)
  @Insert suspend fun insertBlockWorkouts(rows: List<BlockWorkoutEntity>)
  @Insert suspend fun insertScheduleDays(rows: List<BlockScheduleDayEntity>)
  @Insert suspend fun insertRecipeLines(rows: List<RecipeIngredientEntity>)
  @Insert suspend fun insertPlanMeals(rows: List<DietPlanMealEntity>)
  @Insert suspend fun insertShoppingItems(rows: List<ShoppingItemEntity>)
  @Insert suspend fun insertSessionSets(rows: List<SessionSetEntity>)
  @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertMealMembers(rows: List<MealMemberEntity>)

  // Before an overwrite, a record's children go (sets go with their workout entry).
  @Query("DELETE FROM workout_exercise WHERE workoutId = :id") suspend fun deleteWorkoutChildren(id: String)
  @Query("DELETE FROM block_workout WHERE blockId = :id") suspend fun deleteBlockWorkouts(id: String)
  @Query("DELETE FROM block_schedule_day WHERE blockId = :id") suspend fun deleteScheduleDays(id: String)
  @Query("DELETE FROM recipe_ingredient WHERE recipeId = :id") suspend fun deleteRecipeLines(id: String)
  @Query("DELETE FROM diet_plan_meal WHERE dietPlanId = :id") suspend fun deletePlanMeals(id: String)
  @Query("DELETE FROM shopping_item WHERE listId = :id") suspend fun deleteShoppingItems(id: String)
  @Query("DELETE FROM session_set WHERE sessionId = :id") suspend fun deleteSessionSets(id: String)
  @Query("DELETE FROM meal_member WHERE mealId = :id") suspend fun deleteMealMembers(id: String)

  // "Replace everything": children before parents, so no foreign key blocks a delete.
  @Query("DELETE FROM session_set") suspend fun clearSessionSets()
  @Query("DELETE FROM workout_session") suspend fun clearSessions()
  @Query("DELETE FROM body_weight") suspend fun clearBodyWeights()
  @Query("DELETE FROM meal_member") suspend fun clearMealMembers()
  @Query("DELETE FROM household_member") suspend fun clearHouseholdMembers()
  @Query("DELETE FROM shopping_item") suspend fun clearShoppingItems()
  @Query("DELETE FROM shopping_list") suspend fun clearShoppingLists()
  @Query("DELETE FROM meal_entry") suspend fun clearMealEntries()
  @Query("DELETE FROM diet_plan_meal") suspend fun clearPlanMeals()
  @Query("DELETE FROM diet_plan") suspend fun clearDietPlans()
  @Query("DELETE FROM recipe_ingredient") suspend fun clearRecipeLines()
  @Query("DELETE FROM recipe") suspend fun clearRecipes()
  @Query("DELETE FROM ingredient") suspend fun clearIngredients()
  @Query("DELETE FROM block_schedule_day") suspend fun clearScheduleDays()
  @Query("DELETE FROM block_workout") suspend fun clearBlockWorkouts()
  @Query("DELETE FROM training_block") suspend fun clearBlocks()
  @Query("DELETE FROM set_plan") suspend fun clearSetPlans()
  @Query("DELETE FROM workout_exercise") suspend fun clearWorkoutExercises()
  @Query("DELETE FROM workout") suspend fun clearWorkouts()
  @Query("DELETE FROM exercise") suspend fun clearExercises()

  /** Every table, so "Replace everything" leaves nothing of the old data behind. */
  suspend fun clearAll() {
    clearSessionSets()
    clearSessions()
    clearBodyWeights()
    clearMealMembers()
    clearShoppingItems()
    clearShoppingLists()
    clearMealEntries()
    clearPlanMeals()
    clearDietPlans()
    clearRecipeLines()
    clearRecipes()
    clearIngredients()
    clearScheduleDays()
    clearBlockWorkouts()
    clearBlocks()
    clearSetPlans()
    clearWorkoutExercises()
    clearWorkouts()
    clearExercises()
    clearHouseholdMembers()
  }
}
