package com.openweight.app.diet.data

import androidx.room.withTransaction
import com.openweight.app.core.TimeAndIds
import com.openweight.app.core.database.OpenWeightDatabase
import com.openweight.app.core.database.entity.DietPlanEntity
import com.openweight.app.core.database.entity.DietPlanMealEntity
import com.openweight.app.core.database.entity.MealEntryEntity
import com.openweight.app.core.model.MealSlot
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/** A meal on the calendar, with its calories and macros for the servings planned. */
data class PlannedMeal(
  val id: String,
  val date: LocalDate,
  val slot: MealSlot,
  val recipeId: String,
  val recipeName: String,
  val servings: Double,
  val macros: Macros,
  /** False when the recipe has lines that can't be counted. */
  val complete: Boolean,
  val fromPlan: Boolean,
)

data class DayMeals(val date: LocalDate, val meals: List<PlannedMeal>) {
  val total: Macros
    get() = meals.fold(Macros.ZERO) { sum, m -> sum + m.macros }

  val complete: Boolean
    get() = meals.all { it.complete }
}

data class PlanMeal(
  val id: String,
  val dayOfWeek: DayOfWeek,
  val slot: MealSlot,
  val recipeId: String,
  val recipeName: String,
  val servings: Double,
  val macros: Macros,
  val complete: Boolean,
)

data class DietPlanSummary(val id: String, val name: String, val mealCount: Int, val averageKcalPerDay: Double)

data class DietPlanDetail(val id: String, val name: String, val notes: String, val meals: List<PlanMeal>) {
  fun dayTotal(day: DayOfWeek): Macros =
    meals.filter { it.dayOfWeek == day }.fold(Macros.ZERO) { sum, m -> sum + m.macros }
}

enum class ApplyMode {
  /** Days that already have meals stay as they are. */
  SKIP_DAYS_WITH_MEALS,

  /** Days in the range lose their meals and get the plan's. */
  REPLACE,
}

data class ApplyResult(val daysFilled: Int, val daysSkipped: Int, val mealsAdded: Int)

/** The meal calendar and diet plans. Every rule about them lives here, not in the UI. */
@Singleton
class MealPlanRepository
@Inject
constructor(
  private val db: OpenWeightDatabase,
  private val diet: DietRepository,
  private val clock: TimeAndIds,
) {
  private val dao = db.mealPlanDao()

  // ---- Calendar ----

  /** Every date from [from] to [to], each with its meals (possibly none). */
  fun days(from: LocalDate, to: LocalDate): Flow<List<DayMeals>> =
    combine(dao.observeMeals(from, to), diet.recipes()) { meals, recipes ->
      val byId = recipes.associateBy { it.id }
      val planned =
        meals.mapNotNull { m ->
          val r = byId[m.recipeId] ?: return@mapNotNull null
          PlannedMeal(m.id, m.date, m.slot, r.id, r.name, m.servings, r.nutrition.perServing * m.servings, r.nutrition.complete, m.dietPlanId != null)
        }
      val byDate = planned.groupBy { it.date }
      generateSequence(from) { it.plusDays(1) }
        .takeWhile { !it.isAfter(to) }
        .map { d -> DayMeals(d, byDate[d].orEmpty().sortedBy { it.slot.ordinal }) }
        .toList()
    }

  suspend fun addMeal(date: LocalDate, slot: MealSlot, recipeId: String, servings: Double): String {
    requireServings(servings)
    val now = clock.now()
    val id = clock.newId()
    dao.insertMeals(listOf(MealEntryEntity(id, date, slot, recipeId, servings, null, now, now)))
    return id
  }

  suspend fun setServings(mealId: String, servings: Double) {
    requireServings(servings)
    db.withTransaction {
      val m = dao.getMeal(mealId) ?: return@withTransaction
      dao.updateMeal(m.copy(servings = servings, updatedAt = clock.now()))
    }
  }

  suspend fun removeMeal(mealId: String) = dao.deleteMeal(mealId)

  suspend fun clearDay(date: LocalDate) = dao.deleteMealsBetween(date, date)

  /** Copies one day's meals onto another day; [replace] clears the target first. Returns meals copied. */
  suspend fun copyDay(from: LocalDate, to: LocalDate, replace: Boolean): Int = copyRange(from, to, days = 1, replace)

  /** Copies a Monday–Sunday week onto another week; [replace] clears the target week first. */
  suspend fun copyWeek(fromMonday: LocalDate, toMonday: LocalDate, replace: Boolean): Int =
    copyRange(fromMonday, toMonday, days = 7, replace)

  private suspend fun copyRange(from: LocalDate, to: LocalDate, days: Long, replace: Boolean): Int {
    require(from != to) { "Pick a different day to copy to" }
    return db.withTransaction {
      val source = dao.getMeals(from, from.plusDays(days - 1))
      if (replace) dao.deleteMealsBetween(to, to.plusDays(days - 1))
      val now = clock.now()
      val shift = ChronoUnit.DAYS.between(from, to)
      dao.insertMeals(
        source.map { it.copy(id = clock.newId(), date = it.date.plusDays(shift), createdAt = now, updatedAt = now) }
      )
      source.size
    }
  }

  // ---- Diet plans ----

  fun plans(): Flow<List<DietPlanSummary>> =
    combine(dao.observePlans(), dao.observeAllPlanMeals(), diet.recipes()) { plans, meals, recipes ->
      val kcal = recipes.associate { it.id to it.nutrition.perServing.kcal }
      val byPlan = meals.groupBy { it.dietPlanId }
      plans.map { p ->
        val planMeals = byPlan[p.id].orEmpty()
        val week = planMeals.sumOf { (kcal[it.recipeId] ?: 0.0) * it.servings }
        DietPlanSummary(p.id, p.name, planMeals.size, week / 7)
      }
    }

  fun plan(id: String): Flow<DietPlanDetail?> =
    combine(dao.observePlan(id), dao.observeAllPlanMeals(), diet.recipes()) { plan, meals, recipes ->
      plan?.let { p ->
        val byId = recipes.associateBy { it.id }
        DietPlanDetail(
          id = p.id,
          name = p.name,
          notes = p.notes,
          meals =
            meals
              .filter { it.dietPlanId == p.id }
              .mapNotNull { m ->
                val r = byId[m.recipeId] ?: return@mapNotNull null
                PlanMeal(m.id, m.dayOfWeek, m.slot, r.id, r.name, m.servings, r.nutrition.perServing * m.servings, r.nutrition.complete)
              }
              .sortedWith(compareBy({ it.dayOfWeek }, { it.slot.ordinal })),
        )
      }
    }

  suspend fun createPlan(name: String): String {
    val clean = name.trim()
    require(clean.isNotEmpty()) { "Name can't be empty" }
    val now = clock.now()
    val id = clock.newId()
    dao.insertPlan(DietPlanEntity(id, clean, "", now, now))
    return id
  }

  suspend fun updatePlan(id: String, name: String, notes: String) {
    val clean = name.trim()
    require(clean.isNotEmpty()) { "Name can't be empty" }
    db.withTransaction {
      val p = dao.getPlan(id) ?: return@withTransaction
      dao.updatePlan(p.copy(name = clean, notes = notes.trim(), updatedAt = clock.now()))
    }
  }

  /** Deletes a plan. Meals it already put on the calendar stay there. */
  suspend fun deletePlan(id: String) = dao.deletePlan(id)

  suspend fun addPlanMeal(planId: String, day: DayOfWeek, slot: MealSlot, recipeId: String, servings: Double): String {
    requireServings(servings)
    val now = clock.now()
    val id = clock.newId()
    dao.insertPlanMeals(listOf(DietPlanMealEntity(id, planId, day, slot, recipeId, servings, now, now)))
    return id
  }

  suspend fun setPlanMealServings(planMealId: String, servings: Double) {
    requireServings(servings)
    db.withTransaction {
      val m = dao.getPlanMeal(planMealId) ?: return@withTransaction
      dao.updatePlanMeal(m.copy(servings = servings, updatedAt = clock.now()))
    }
  }

  suspend fun removePlanMeal(planMealId: String) = dao.deletePlanMeal(planMealId)

  /**
   * Puts the plan's meals on the calendar for every date from [from] to [to], by weekday. Days
   * that already have meals are skipped or replaced, per [mode]. One transaction.
   */
  suspend fun applyPlan(planId: String, from: LocalDate, to: LocalDate, mode: ApplyMode): ApplyResult {
    require(!to.isBefore(from)) { "The range must end on or after its start" }
    require(ChronoUnit.DAYS.between(from, to) < MAX_APPLY_DAYS) { "At most $MAX_APPLY_DAYS days at once" }
    return db.withTransaction {
      val planMeals = dao.getPlanMeals(planId).groupBy { it.dayOfWeek }
      val busy = dao.datesWithMeals(from, to).toSet()
      val now = clock.now()
      var filled = 0
      var skipped = 0
      val toInsert = mutableListOf<MealEntryEntity>()
      var date = from
      while (!date.isAfter(to)) {
        val meals = planMeals[date.dayOfWeek].orEmpty()
        when {
          meals.isEmpty() -> Unit
          date in busy && mode == ApplyMode.SKIP_DAYS_WITH_MEALS -> skipped++
          else -> {
            if (date in busy) dao.deleteMealsBetween(date, date)
            meals.forEach { toInsert += MealEntryEntity(clock.newId(), date, it.slot, it.recipeId, it.servings, planId, now, now) }
            filled++
          }
        }
        date = date.plusDays(1)
      }
      dao.insertMeals(toInsert)
      ApplyResult(filled, skipped, toInsert.size)
    }
  }

  private fun requireServings(servings: Double) =
    require(servings > 0 && servings <= MAX_SERVINGS) { "Servings must be above 0 and at most $MAX_SERVINGS" }

  companion object {
    const val MAX_SERVINGS = 20.0
    const val MAX_APPLY_DAYS = 366
  }
}
