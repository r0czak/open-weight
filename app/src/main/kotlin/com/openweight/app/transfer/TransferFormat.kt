package com.openweight.app.transfer

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The Open Weight file format (see the Import/Export Format doc). One format for everything from a
 * single workout to a full backup; a partial export is the same file with fewer records.
 *
 * Arrays are left out when empty. Fields from newer app versions are ignored (`ignoreUnknownKeys`),
 * so files keep importing as the format grows. Enum values (units, days, meal slots) are written by
 * name and checked on import, so a bad value gets a clear message instead of "damaged file".
 */
@Serializable
data class OpenWeightFile(
  val format: String,
  val formatVersion: Int,
  val appVersion: String? = null,
  val exportedAt: String? = null,
  val scope: TransferScope? = null,
  val data: TransferData = TransferData(),
) {
  companion object {
    const val FORMAT = "open-weight"
    const val CURRENT_VERSION = 1
  }
}

/** [kind] is one of [ScopeKind]'s names in lower case: full, workout, diet, items, range. */
@Serializable data class TransferScope(val kind: String, val description: String? = null)

enum class ScopeKind {
  FULL,
  WORKOUT,
  DIET,
  ITEMS,
  RANGE,
  /** Finished training sessions and body weight. */
  PROGRESS;

  val id: String
    get() = name.lowercase()
}

@Serializable
data class TransferData(
  val exercises: List<ExerciseDto> = emptyList(),
  val workouts: List<WorkoutDto> = emptyList(),
  val trainingBlocks: List<TrainingBlockDto> = emptyList(),
  val ingredients: List<IngredientDto> = emptyList(),
  val recipes: List<RecipeDto> = emptyList(),
  val dietPlans: List<DietPlanDto> = emptyList(),
  val mealEntries: List<MealEntryDto> = emptyList(),
  val shoppingLists: List<ShoppingListDto> = emptyList(),
  /** Finished training sessions only (an open one stays on the phone). */
  val workoutSessions: List<WorkoutSessionDto> = emptyList(),
  val bodyWeights: List<BodyWeightDto> = emptyList(),
  val householdMembers: List<HouseholdMemberDto> = emptyList(),
  /** Full backups only. */
  val settings: SettingsDto? = null,
)

@Serializable
data class ExerciseDto(
  val id: String,
  val name: String,
  val muscleGroup: String = "",
  val equipment: String = "",
  val notes: String = "",
  val createdAt: String? = null,
  val updatedAt: String? = null,
)

@Serializable
data class WorkoutDto(
  val id: String,
  val name: String,
  val type: String = "",
  val notes: String = "",
  val exercises: List<WorkoutExerciseDto> = emptyList(),
  val createdAt: String? = null,
  val updatedAt: String? = null,
)

@Serializable
data class WorkoutExerciseDto(
  val exerciseId: String,
  val notes: String = "",
  val sets: List<SetDto> = emptyList(),
)

@Serializable
data class SetDto(
  val repsMin: Int,
  val repsMax: Int? = null,
  val weightKg: Double? = null,
  val restSeconds: Int = 90,
)

@Serializable
data class TrainingBlockDto(
  val id: String,
  val name: String,
  val startDate: String,
  val endDate: String,
  val notes: String = "",
  val workoutIds: List<String> = emptyList(),
  val schedule: List<ScheduleDayDto> = emptyList(),
  val createdAt: String? = null,
  val updatedAt: String? = null,
)

/** [dayOfWeek] is MONDAY…SUNDAY; a null [workoutId] (or a missing day) means rest. */
@Serializable data class ScheduleDayDto(val dayOfWeek: String, val workoutId: String? = null)

/** Nutrition is per 100 g, or per 100 ml when [defaultUnit] is a volume. */
@Serializable
data class IngredientDto(
  val id: String,
  val name: String,
  val defaultUnit: String = "G",
  val shopCategory: String = "",
  val kcalPer100: Double? = null,
  val proteinPer100: Double? = null,
  val carbsPer100: Double? = null,
  val fatPer100: Double? = null,
  val gramsPerPiece: Double? = null,
  val createdAt: String? = null,
  val updatedAt: String? = null,
)

@Serializable
data class RecipeDto(
  val id: String,
  val name: String,
  val servings: Int = 1,
  val prepMinutes: Int? = null,
  val steps: String = "",
  val tags: String = "",
  val notes: String = "",
  val ingredients: List<RecipeLineDto> = emptyList(),
  val createdAt: String? = null,
  val updatedAt: String? = null,
)

/** One line of a recipe, in the unit the user entered (G, KG, ML, L, TSP, TBSP, PCS). */
@Serializable data class RecipeLineDto(val ingredientId: String, val quantity: Double, val unit: String)

@Serializable
data class DietPlanDto(
  val id: String,
  val name: String,
  val notes: String = "",
  val meals: List<PlanMealDto> = emptyList(),
  val createdAt: String? = null,
  val updatedAt: String? = null,
)

/** [slot] is BREAKFAST, LUNCH, DINNER or SNACK. */
@Serializable
data class PlanMealDto(val dayOfWeek: String, val slot: String, val recipeId: String, val servings: Double = 1.0)

/** A meal on the calendar. [dietPlanId] only records where it came from; if that plan is missing, it is dropped. */
@Serializable
data class MealEntryDto(
  val id: String,
  val date: String,
  val slot: String,
  val recipeId: String,
  val servings: Double = 1.0,
  val dietPlanId: String? = null,
  val createdAt: String? = null,
  val updatedAt: String? = null,
  /** When it was ticked as eaten; absent while only planned. */
  val eatenAt: String? = null,
  /** Household members who also eat it. A member in neither the file nor the app is dropped. */
  val memberIds: List<String> = emptyList(),
)

@Serializable
data class ShoppingListDto(
  val id: String,
  val name: String = "Shopping list",
  val fromDate: String,
  val toDate: String,
  val generatedAt: String? = null,
  val items: List<ShoppingItemDto> = emptyList(),
  val createdAt: String? = null,
  val updatedAt: String? = null,
)

/** [ingredientId] links a calendar item to its ingredient; if that ingredient is missing, it is dropped. */
@Serializable
data class ShoppingItemDto(
  val name: String,
  val ingredientId: String? = null,
  val quantity: Double? = null,
  val unit: String? = null,
  val category: String = "",
  val checked: Boolean = false,
  val isManual: Boolean = false,
  val isQuantityEdited: Boolean = false,
)

/** [weightUnit] is KG or LB; [themeMode] is SYSTEM, LIGHT or DARK; targets per day. */
@Serializable
data class SettingsDto(
  val weightUnit: String? = null,
  val themeMode: String? = null,
  val restAlerts: Boolean? = null,
  val kcalTarget: Int? = null,
  val proteinTarget: Int? = null,
)

/**
 * A finished training session. Names are kept as text; [workoutId] and a set's exerciseId are
 * dropped when that record is in neither the file nor the app.
 */
@Serializable
data class WorkoutSessionDto(
  val id: String,
  val workoutId: String? = null,
  val workoutName: String,
  val plannedDate: String,
  val startedAt: String,
  val finishedAt: String,
  val notes: String = "",
  val sets: List<SessionSetDto> = emptyList(),
  val createdAt: String? = null,
  val updatedAt: String? = null,
)

@Serializable
data class SessionSetDto(
  val exerciseId: String? = null,
  val exerciseName: String,
  val exercisePosition: Int,
  val setPosition: Int,
  val targetRepsMin: Int,
  val targetRepsMax: Int? = null,
  val restSeconds: Int = 90,
  val weightKg: Double? = null,
  val reps: Int,
  val done: Boolean = false,
  val completedAt: String? = null,
)

/** One weigh-in; one per date. */
@Serializable
data class BodyWeightDto(val id: String, val date: String, val weightKg: Double, val createdAt: String? = null, val updatedAt: String? = null)

/** Someone you cook for; [portion] of yours. */
@Serializable
data class HouseholdMemberDto(
  val id: String,
  val name: String,
  val portion: Double,
  val eatsByDefault: Boolean = true,
  val createdAt: String? = null,
  val updatedAt: String? = null,
)

internal val TransferJson = Json {
  ignoreUnknownKeys = true
  prettyPrint = true
  prettyPrintIndent = "  "
  explicitNulls = false
}
