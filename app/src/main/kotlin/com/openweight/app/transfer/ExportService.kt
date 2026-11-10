package com.openweight.app.transfer

import androidx.room.withTransaction
import com.openweight.app.core.TimeAndIds
import com.openweight.app.core.database.OpenWeightDatabase
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
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** What to export. Items can mix kinds; each brings what it depends on. */
sealed interface ExportRequest {
  data object Everything : ExportRequest

  data object WorkoutData : ExportRequest

  data object DietData : ExportRequest

  data class Items(
    val blockIds: Set<String> = emptySet(),
    val workoutIds: Set<String> = emptySet(),
    val recipeIds: Set<String> = emptySet(),
    val dietPlanIds: Set<String> = emptySet(),
  ) : ExportRequest {
    val isEmpty: Boolean
      get() = blockIds.isEmpty() && workoutIds.isEmpty() && recipeIds.isEmpty() && dietPlanIds.isEmpty()
  }

  /** Calendar meals from [from] to [to], with their recipes, ingredients and who eats them. */
  data class Range(val from: LocalDate, val to: LocalDate) : ExportRequest

  /** Finished training sessions and body weight. */
  data object Progress : ExportRequest
}

class ExportedFile(val fileName: String, val file: OpenWeightFile) {
  val bytes: ByteArray by lazy { TransferJson.encodeToString(OpenWeightFile.serializer(), file).encodeToByteArray() }
}

/**
 * Builds Open Weight files. An export always includes what its records depend on (a block's
 * workouts, their exercises; a recipe's ingredients), so every file imports on an empty phone.
 */
@Singleton
class ExportService
@Inject
constructor(private val db: OpenWeightDatabase, private val clock: TimeAndIds, private val appInfo: AppInfo) {

  suspend fun export(request: ExportRequest, settings: SettingsDto? = null): ExportedFile {
    val all = db.withTransaction { Snapshot.read(db) }
    val today = clock.today()
    val (scope, data, name) =
      when (request) {
        ExportRequest.Everything ->
          Triple(
            TransferScope(ScopeKind.FULL.id, "Full backup"),
            all.workoutPart() + all.dietPart() + TransferData(bodyWeights = all.weights.map(all::weight), settings = settings),
            "backup-$today",
          )
        ExportRequest.WorkoutData -> Triple(TransferScope(ScopeKind.WORKOUT.id, "All workout data"), all.workoutPart(), "workout-$today")
        ExportRequest.Progress ->
          Triple(
            TransferScope(ScopeKind.PROGRESS.id, "Training history and body weight"),
            TransferData(workoutSessions = all.sessions.map(all::session), bodyWeights = all.weights.map(all::weight)),
            "progress-$today",
          )
        ExportRequest.DietData -> Triple(TransferScope(ScopeKind.DIET.id, "All diet data"), all.dietPart(), "diet-$today")
        is ExportRequest.Items -> items(all, request, today)
        is ExportRequest.Range -> {
          require(!request.to.isBefore(request.from)) { "The range must end on or after its start" }
          val meals = all.meals.filter { !it.date.isBefore(request.from) && !it.date.isAfter(request.to) }
          val memberIds = meals.flatMap { all.memberIdsOf(it.id) }.toSet()
          val data =
            all.recipesWithIngredients(meals.map { it.recipeId }.toSet())
              .copy(mealEntries = meals.map(all::meal), householdMembers = all.members.filter { it.id in memberIds }.map(all::member))
          Triple(TransferScope(ScopeKind.RANGE.id, "Meals ${request.from} – ${request.to}"), data, "meals-${request.from}-to-${request.to}")
        }
      }
    val file =
      OpenWeightFile(
        format = OpenWeightFile.FORMAT,
        formatVersion = OpenWeightFile.CURRENT_VERSION,
        appVersion = appInfo.versionName(),
        exportedAt = clock.now().toString(),
        scope = scope,
        data = data,
      )
    return ExportedFile("openweight-$name.json", file)
  }

  private fun items(all: Snapshot, request: ExportRequest.Items, today: LocalDate): Triple<TransferScope, TransferData, String> {
    val blocks = all.blocks.filter { it.id in request.blockIds }
    val workoutIds = request.workoutIds + all.blockWorkouts.filter { it.blockId in request.blockIds }.map { it.workoutId }
    val plans = all.plans.filter { it.id in request.dietPlanIds }
    val recipeIds = request.recipeIds + all.planMeals.filter { it.dietPlanId in request.dietPlanIds }.map { it.recipeId }

    val workoutPart = all.workoutsWithExercises(workoutIds).copy(trainingBlocks = blocks.map(all::block))
    val dietPart = all.recipesWithIngredients(recipeIds).copy(dietPlans = plans.map(all::plan))
    val data = workoutPart + dietPart

    // One picked item names the file after itself; several get a date.
    val picked =
      blocks.map { "Training block" to it.name } +
        all.workouts.filter { it.id in request.workoutIds }.map { "Workout" to it.name } +
        all.recipes.filter { it.id in request.recipeIds }.map { "Recipe" to it.name } +
        plans.map { "Diet plan" to it.name }
    val single = picked.singleOrNull()
    val description = single?.let { "${it.first}: ${it.second}" } ?: picked.groupingBy { it.first }.eachCount().entries.joinToString { "${it.value} × ${it.key}" }
    val name = single?.let { "${slug(it.first)}-${slug(it.second).ifEmpty { today.toString() }}" } ?: "items-$today"
    return Triple(TransferScope(ScopeKind.ITEMS.id, description), data, name)
  }

  companion object {
    /** "Push A · Intro" → "push-a-intro": lower case letters and digits, joined by dashes. */
    fun slug(text: String): String =
      text.lowercase().replace(Regex("[^\\p{L}\\p{Nd}]+"), "-").trim('-').take(40).trimEnd('-')
  }
}

/** The app's version, written into every file. */
fun interface AppInfo {
  fun versionName(): String
}

private operator fun TransferData.plus(o: TransferData) =
  TransferData(
    exercises = exercises + o.exercises,
    workouts = workouts + o.workouts,
    trainingBlocks = trainingBlocks + o.trainingBlocks,
    ingredients = ingredients + o.ingredients,
    recipes = recipes + o.recipes,
    dietPlans = dietPlans + o.dietPlans,
    mealEntries = mealEntries + o.mealEntries,
    shoppingLists = shoppingLists + o.shoppingLists,
    workoutSessions = workoutSessions + o.workoutSessions,
    bodyWeights = bodyWeights + o.bodyWeights,
    householdMembers = householdMembers + o.householdMembers,
    settings = settings ?: o.settings,
  )

/** Every table, read in one transaction, and its records as file records. */
private class Snapshot(
  val exercises: List<ExerciseEntity>,
  val workouts: List<WorkoutEntity>,
  val workoutExercises: List<WorkoutExerciseEntity>,
  val setPlans: List<SetPlanEntity>,
  val blocks: List<TrainingBlockEntity>,
  val blockWorkouts: List<BlockWorkoutEntity>,
  val scheduleDays: List<BlockScheduleDayEntity>,
  val ingredients: List<IngredientEntity>,
  val recipes: List<RecipeEntity>,
  val recipeLines: List<RecipeIngredientEntity>,
  val plans: List<DietPlanEntity>,
  val planMeals: List<DietPlanMealEntity>,
  val meals: List<MealEntryEntity>,
  val lists: List<ShoppingListEntity>,
  val listItems: List<ShoppingItemEntity>,
  val sessions: List<WorkoutSessionEntity>,
  val sessionSets: List<SessionSetEntity>,
  val weights: List<BodyWeightEntity>,
  val members: List<HouseholdMemberEntity>,
  val mealMembers: List<MealMemberEntity>,
) {
  private val setsBySession = sessionSets.groupBy { it.sessionId }
  private val membersByMeal = mealMembers.groupBy({ it.mealId }, { it.memberId })

  fun memberIdsOf(mealId: String): List<String> = membersByMeal[mealId].orEmpty()
  private val entriesByWorkout = workoutExercises.groupBy { it.workoutId }
  private val setsByEntry = setPlans.groupBy { it.workoutExerciseId }
  private val linesByRecipe = recipeLines.groupBy { it.recipeId }

  fun workoutPart() =
    TransferData(exercises = exercises.map(::exercise), workouts = workouts.map(::workout), trainingBlocks = blocks.map(::block), workoutSessions = sessions.map(::session))

  fun dietPart() =
    TransferData(
      ingredients = ingredients.map(::ingredient),
      recipes = recipes.map(::recipe),
      dietPlans = plans.map(::plan),
      mealEntries = meals.map(::meal),
      shoppingLists = lists.map(::list),
      householdMembers = members.map(::member),
    )

  fun workoutsWithExercises(ids: Set<String>): TransferData {
    val picked = workouts.filter { it.id in ids }
    val exerciseIds = picked.flatMap { w -> entriesByWorkout[w.id].orEmpty().map { it.exerciseId } }.toSet()
    return TransferData(exercises = exercises.filter { it.id in exerciseIds }.map(::exercise), workouts = picked.map(::workout))
  }

  fun recipesWithIngredients(ids: Set<String>): TransferData {
    val picked = recipes.filter { it.id in ids }
    val ingredientIds = picked.flatMap { r -> linesByRecipe[r.id].orEmpty().map { it.ingredientId } }.toSet()
    return TransferData(ingredients = ingredients.filter { it.id in ingredientIds }.map(::ingredient), recipes = picked.map(::recipe))
  }

  fun exercise(e: ExerciseEntity) = ExerciseDto(e.id, e.name, e.muscleGroup, e.equipment, e.notes, e.createdAt.toString(), e.updatedAt.toString())

  fun workout(w: WorkoutEntity) =
    WorkoutDto(
      id = w.id,
      name = w.name,
      type = w.type,
      notes = w.notes,
      exercises =
        entriesByWorkout[w.id].orEmpty().map { entry ->
          WorkoutExerciseDto(
            entry.exerciseId,
            entry.notes,
            setsByEntry[entry.id].orEmpty().map { SetDto(it.repsMin, it.repsMax, it.weightKg, it.restSeconds) },
          )
        },
      createdAt = w.createdAt.toString(),
      updatedAt = w.updatedAt.toString(),
    )

  fun block(b: TrainingBlockEntity) =
    TrainingBlockDto(
      id = b.id,
      name = b.name,
      startDate = b.startDate.toString(),
      endDate = b.endDate.toString(),
      notes = b.notes,
      workoutIds = blockWorkouts.filter { it.blockId == b.id }.map { it.workoutId },
      // Rest days are left out.
      schedule = scheduleDays.filter { it.blockId == b.id && it.workoutId != null }.sortedBy { it.dayOfWeek }.map { ScheduleDayDto(it.dayOfWeek.name, it.workoutId) },
      createdAt = b.createdAt.toString(),
      updatedAt = b.updatedAt.toString(),
    )

  fun ingredient(i: IngredientEntity) =
    IngredientDto(i.id, i.name, i.defaultUnit.name, i.shopCategory, i.kcalPer100, i.proteinPer100, i.carbsPer100, i.fatPer100, i.gramsPerPiece, i.createdAt.toString(), i.updatedAt.toString())

  fun recipe(r: RecipeEntity) =
    RecipeDto(
      id = r.id,
      name = r.name,
      servings = r.servings,
      prepMinutes = r.prepMinutes,
      steps = r.steps,
      tags = r.tags,
      notes = r.notes,
      ingredients = linesByRecipe[r.id].orEmpty().map { RecipeLineDto(it.ingredientId, it.quantity, it.unit.name) },
      createdAt = r.createdAt.toString(),
      updatedAt = r.updatedAt.toString(),
    )

  fun plan(p: DietPlanEntity) =
    DietPlanDto(
      id = p.id,
      name = p.name,
      notes = p.notes,
      meals =
        planMeals.filter { it.dietPlanId == p.id }.sortedWith(compareBy({ it.dayOfWeek }, { it.slot })).map {
          PlanMealDto(it.dayOfWeek.name, it.slot.name, it.recipeId, it.servings)
        },
      createdAt = p.createdAt.toString(),
      updatedAt = p.updatedAt.toString(),
    )

  fun meal(m: MealEntryEntity) =
    MealEntryDto(m.id, m.date.toString(), m.slot.name, m.recipeId, m.servings, m.dietPlanId, m.createdAt.toString(), m.updatedAt.toString(), m.eatenAt?.toString(), memberIdsOf(m.id))

  fun session(s: WorkoutSessionEntity) =
    WorkoutSessionDto(
      id = s.id,
      workoutId = s.workoutId,
      workoutName = s.workoutName,
      plannedDate = s.plannedDate.toString(),
      startedAt = s.startedAt.toString(),
      finishedAt = s.finishedAt!!.toString(),
      notes = s.notes,
      sets =
        setsBySession[s.id].orEmpty().map {
          SessionSetDto(it.exerciseId, it.exerciseName, it.exercisePosition, it.setPosition, it.targetRepsMin, it.targetRepsMax, it.restSeconds, it.weightKg, it.reps, it.done, it.completedAt?.toString())
        },
      createdAt = s.createdAt.toString(),
      updatedAt = s.updatedAt.toString(),
    )

  fun weight(w: BodyWeightEntity) = BodyWeightDto(w.id, w.date.toString(), w.weightKg, w.createdAt.toString(), w.updatedAt.toString())

  fun member(m: HouseholdMemberEntity) = HouseholdMemberDto(m.id, m.name, m.portion, m.eatsByDefault, m.createdAt.toString(), m.updatedAt.toString())

  fun list(l: ShoppingListEntity) =
    ShoppingListDto(
      id = l.id,
      name = l.name,
      fromDate = l.fromDate.toString(),
      toDate = l.toDate.toString(),
      generatedAt = l.generatedAt.toString(),
      items =
        listItems.filter { it.listId == l.id }.map {
          ShoppingItemDto(it.name, it.ingredientId, it.quantity, it.unit?.name, it.category, it.checked, it.isManual, it.isQuantityEdited)
        },
      createdAt = l.createdAt.toString(),
      updatedAt = l.updatedAt.toString(),
    )

  companion object {
    suspend fun read(db: OpenWeightDatabase): Snapshot {
      val d = db.transferDao()
      return Snapshot(
        d.exercises(), d.workouts(), d.workoutExercises(), d.setPlans(), d.blocks(), d.blockWorkouts(), d.scheduleDays(),
        d.ingredients(), d.recipes(), d.recipeLines(), d.dietPlans(), d.planMeals(), d.mealEntries(), d.shoppingLists(), d.shoppingItems(),
        // Only finished sessions travel: an open one stays on the phone.
        d.finishedSessions(), d.sessionSets(), d.bodyWeights(), d.householdMembers(), d.mealMembers(),
      )
    }
  }
}
