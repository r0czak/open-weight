package com.openweight.app.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.openweight.app.core.database.entity.DietPlanEntity
import com.openweight.app.core.database.entity.DietPlanMealEntity
import com.openweight.app.core.database.entity.MealEntryEntity
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

/** Meals on the calendar and diet plans (repeating weeks of meals). */
@Dao
interface MealPlanDao {
  // ---- Calendar ----

  @Query("SELECT * FROM meal_entry WHERE date BETWEEN :from AND :to ORDER BY date, slot, createdAt")
  fun observeMeals(from: LocalDate, to: LocalDate): Flow<List<MealEntryEntity>>

  @Query("SELECT * FROM meal_entry WHERE date BETWEEN :from AND :to ORDER BY date, createdAt")
  suspend fun getMeals(from: LocalDate, to: LocalDate): List<MealEntryEntity>

  @Query("SELECT * FROM meal_entry WHERE id = :id") suspend fun getMeal(id: String): MealEntryEntity?

  @Query("SELECT DISTINCT date FROM meal_entry WHERE date BETWEEN :from AND :to")
  suspend fun datesWithMeals(from: LocalDate, to: LocalDate): List<LocalDate>

  @Insert suspend fun insertMeals(meals: List<MealEntryEntity>)

  @Update suspend fun updateMeal(meal: MealEntryEntity)

  @Query("DELETE FROM meal_entry WHERE id = :id") suspend fun deleteMeal(id: String)

  @Query("DELETE FROM meal_entry WHERE date BETWEEN :from AND :to")
  suspend fun deleteMealsBetween(from: LocalDate, to: LocalDate)

  // ---- Diet plans ----

  @Query("SELECT * FROM diet_plan ORDER BY name COLLATE NOCASE") fun observePlans(): Flow<List<DietPlanEntity>>

  @Query("SELECT * FROM diet_plan WHERE id = :id") fun observePlan(id: String): Flow<DietPlanEntity?>

  @Query("SELECT * FROM diet_plan WHERE id = :id") suspend fun getPlan(id: String): DietPlanEntity?

  @Query("SELECT * FROM diet_plan_meal ORDER BY createdAt") fun observeAllPlanMeals(): Flow<List<DietPlanMealEntity>>

  @Query("SELECT * FROM diet_plan_meal WHERE dietPlanId = :planId ORDER BY createdAt")
  suspend fun getPlanMeals(planId: String): List<DietPlanMealEntity>

  @Query("SELECT * FROM diet_plan_meal WHERE id = :id") suspend fun getPlanMeal(id: String): DietPlanMealEntity?

  @Insert suspend fun insertPlan(plan: DietPlanEntity)

  @Update suspend fun updatePlan(plan: DietPlanEntity)

  @Query("DELETE FROM diet_plan WHERE id = :id") suspend fun deletePlan(id: String)

  @Insert suspend fun insertPlanMeals(meals: List<DietPlanMealEntity>)

  @Update suspend fun updatePlanMeal(meal: DietPlanMealEntity)

  @Query("DELETE FROM diet_plan_meal WHERE id = :id") suspend fun deletePlanMeal(id: String)
}
