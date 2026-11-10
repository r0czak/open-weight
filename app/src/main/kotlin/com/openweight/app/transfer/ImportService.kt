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
import com.openweight.app.diet.household.HouseholdRepository
import com.openweight.app.progress.WeightRules
import com.openweight.app.core.database.entity.DietPlanEntity
import com.openweight.app.core.database.entity.DietPlanMealEntity
import com.openweight.app.core.database.entity.ExerciseEntity
import com.openweight.app.core.database.entity.IngredientEntity
import com.openweight.app.core.database.entity.MAX_WORKOUTS_PER_BLOCK
import com.openweight.app.core.database.entity.MealEntryEntity
import com.openweight.app.core.database.entity.RecipeEntity
import com.openweight.app.core.database.entity.RecipeIngredientEntity
import com.openweight.app.core.database.entity.SetPlanEntity
import com.openweight.app.core.database.entity.ShoppingItemEntity
import com.openweight.app.core.database.entity.ShoppingListEntity
import com.openweight.app.core.database.entity.TrainingBlockEntity
import com.openweight.app.core.database.entity.WorkoutEntity
import com.openweight.app.core.database.entity.WorkoutExerciseEntity
import com.openweight.app.core.model.MealSlot
import com.openweight.app.core.model.QuantityUnit
import com.openweight.app.diet.data.INGREDIENT_UNITS
import com.openweight.app.diet.data.IngredientValues
import com.openweight.app.diet.data.MealPlanRepository
import com.openweight.app.diet.data.RecipeInfo
import com.openweight.app.diet.data.ShoppingRepository
import com.openweight.app.diet.data.validQuantity
import com.openweight.app.workout.data.SetValues
import com.openweight.app.workout.data.WorkoutRepository
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.SerializationException

/** Why a file can't be imported, in words the user can act on. Nothing was changed. */
class ImportException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** The kinds of records a file holds, in the order the preview lists them. */
enum class RecordKind {
  TRAINING_BLOCK,
  WORKOUT,
  EXERCISE,
  SESSION,
  BODY_WEIGHT,
  DIET_PLAN,
  RECIPE,
  INGREDIENT,
  HOUSEHOLD_MEMBER,
  MEAL,
  SHOPPING_LIST,
}

/** What happens to a record in the file that is already in the app. */
enum class ConflictChoice {
  /** The app's record stays; the file's is ignored. */
  SKIP,
  /** The file's record overwrites the app's. */
  REPLACE,
  /** Whichever was changed last is kept. */
  NEWER_WINS,
  /**
   * The file's record is added as a copy, with a new id and " (imported)" after its name. A
   * weigh-in can't have a copy (one per day): it is skipped. A session copy keeps its name.
   */
  KEEP_BOTH,
}

/** A record in the file that is already in the app: same id, or (exercises, ingredients) same name. */
data class ImportMatch(
  val kind: RecordKind,
  val fileId: String,
  val name: String,
  val byName: Boolean,
  /** The file's copy was changed after the app's; null when the file doesn't say. */
  val fileIsNewer: Boolean?,
  /** Calendar meals: their day. */
  val date: LocalDate? = null,
) {
  val key: String
    get() = key(kind, fileId)

  companion object {
    fun key(kind: RecordKind, fileId: String) = "$kind:$fileId"
  }
}

data class KindCount(val inFile: Int, val alreadyHere: Int) {
  val new: Int
    get() = inFile - alreadyHere
}

/** What a file holds and what is already in the app, before anything is written. */
data class ImportPreview(
  val scope: ScopeKind?,
  val description: String?,
  val exportedAt: Instant?,
  /** Only the kinds the file holds, in [RecordKind] order. */
  val counts: Map<RecordKind, KindCount>,
  val matches: List<ImportMatch>,
  val hasSettings: Boolean,
) {
  val isFull: Boolean
    get() = scope == ScopeKind.FULL

  val isEmpty: Boolean
    get() = counts.isEmpty() && !hasSettings
}

data class ImportOptions(
  /** Applies to every match without an override. */
  val choice: ConflictChoice = ConflictChoice.SKIP,
  /** Per match, by [ImportMatch.key]. */
  val overrides: Map<String, ConflictChoice> = emptyMap(),
  /** Full backups: clear the app first. The caller saves the current data before. */
  val replaceEverything: Boolean = false,
  /** Added after the names of "Keep both" copies. */
  val copySuffix: String = " (imported)",
)

data class KindResult(val added: Int = 0, val replaced: Int = 0, val skipped: Int = 0, val copied: Int = 0)

data class ImportResult(
  val byKind: Map<RecordKind, KindResult>,
  /** Settings from a full backup, for the caller to apply. */
  val settings: SettingsDto? = null,
) {
  val nothingChanged: Boolean
    get() = byKind.values.all { it.added + it.replaced + it.copied == 0 }

  operator fun get(kind: RecordKind): KindResult = byKind[kind] ?: KindResult()
}

/**
 * Imports Open Weight files. A record matches one in the app by id, and exercises and ingredients
 * also by name (ignoring case), so a friend's "Bench press" doesn't become a second one. Matches
 * follow the chosen [ConflictChoice]; links inside the file follow the records they point at (to a
 * copy, or to the app's record when skipped). Everything is checked before anything is written,
 * and the import runs in one transaction: it lands completely or not at all.
 */
@Singleton
class ImportService
@Inject
constructor(private val db: OpenWeightDatabase, private val clock: TimeAndIds) {

  fun parse(bytes: ByteArray): OpenWeightFile {
    if (bytes.size > MAX_FILE_BYTES) throw ImportException("The file is over 20 MB.")
    val file =
      try {
        // Some editors save JSON with a byte order mark in front; it isn't part of the file.
        TransferJson.decodeFromString(OpenWeightFile.serializer(), bytes.decodeToString().removePrefix("\uFEFF"))
      } catch (e: SerializationException) {
        throw ImportException("This isn't an Open Weight file, or it is damaged.", e)
      } catch (e: IllegalArgumentException) {
        throw ImportException("This isn't an Open Weight file, or it is damaged.", e)
      }
    if (file.format != OpenWeightFile.FORMAT) {
      throw ImportException("This isn't an Open Weight file.")
    }
    if (file.formatVersion > OpenWeightFile.CURRENT_VERSION) {
      throw ImportException("This file comes from a newer version of Open Weight. Update the app to import it.")
    }
    return file
  }

  /** Checks the file and lists what is new and what is already in the app. Writes nothing. */
  suspend fun preview(file: OpenWeightFile): ImportPreview =
    db.withTransaction {
      val existing = Existing.read(db)
      validate(file.data, existing)
      val d = file.data
      val recipeNames = existing.recipeNames + d.recipes.associate { it.id to it.name }
      fun <T> match(kind: RecordKind, items: List<T>, id: (T) -> String, name: (T) -> String, updated: (T) -> String?, date: (T) -> LocalDate? = { null }) =
        items.mapNotNull { item ->
          val (rec, byName) = existing.match(kind, id(item), name(item)) ?: return@mapNotNull null
          val fileUpdated = updated(item)?.let(::parseInstant)
          ImportMatch(kind, id(item), name(item), byName, fileUpdated?.let { it > rec.updatedAt }, date(item))
        }
      val matches =
        match(RecordKind.TRAINING_BLOCK, d.trainingBlocks, { it.id }, { it.name }, { it.updatedAt }) +
          match(RecordKind.WORKOUT, d.workouts, { it.id }, { it.name }, { it.updatedAt }) +
          match(RecordKind.EXERCISE, d.exercises, { it.id }, { it.name }, { it.updatedAt }) +
          match(RecordKind.DIET_PLAN, d.dietPlans, { it.id }, { it.name }, { it.updatedAt }) +
          match(RecordKind.RECIPE, d.recipes, { it.id }, { it.name }, { it.updatedAt }) +
          match(RecordKind.INGREDIENT, d.ingredients, { it.id }, { it.name }, { it.updatedAt }) +
          match(RecordKind.MEAL, d.mealEntries, { it.id }, { recipeNames[it.recipeId].orEmpty() }, { it.updatedAt }, { LocalDate.parse(it.date) }) +
          match(RecordKind.SHOPPING_LIST, d.shoppingLists, { it.id }, { it.name }, { it.updatedAt }) +
          match(RecordKind.SESSION, d.workoutSessions, { it.id }, { it.workoutName }, { it.updatedAt }, { LocalDate.parse(it.plannedDate) }) +
          match(RecordKind.BODY_WEIGHT, d.bodyWeights, { it.id }, { it.date }, { it.updatedAt }, { LocalDate.parse(it.date) }) +
          match(RecordKind.HOUSEHOLD_MEMBER, d.householdMembers, { it.id }, { it.name }, { it.updatedAt })
      val sizes =
        mapOf(
          RecordKind.TRAINING_BLOCK to d.trainingBlocks.size,
          RecordKind.WORKOUT to d.workouts.size,
          RecordKind.EXERCISE to d.exercises.size,
          RecordKind.DIET_PLAN to d.dietPlans.size,
          RecordKind.RECIPE to d.recipes.size,
          RecordKind.INGREDIENT to d.ingredients.size,
          RecordKind.MEAL to d.mealEntries.size,
          RecordKind.SHOPPING_LIST to d.shoppingLists.size,
          RecordKind.SESSION to d.workoutSessions.size,
          RecordKind.BODY_WEIGHT to d.bodyWeights.size,
          RecordKind.HOUSEHOLD_MEMBER to d.householdMembers.size,
        )
      ImportPreview(
        scope = ScopeKind.entries.firstOrNull { it.id == file.scope?.kind },
        description = file.scope?.description,
        exportedAt = file.exportedAt?.let(::parseInstant),
        counts = sizes.filterValues { it > 0 }.toSortedMap(compareBy { it.ordinal }).mapValues { (kind, n) -> KindCount(n, matches.count { it.kind == kind }) },
        matches = matches,
        hasSettings = d.settings != null,
      )
    }

  suspend fun import(bytes: ByteArray): ImportResult = import(parse(bytes))

  suspend fun import(file: OpenWeightFile, options: ImportOptions = ImportOptions()): ImportResult =
    db.withTransaction {
      val dao = db.transferDao()
      if (options.replaceEverything) {
        // Links must then resolve inside the file: check before clearing.
        validate(file.data, Existing.EMPTY)
        dao.clearAll()
      }
      val existing = Existing.read(db)
      validate(file.data, existing)
      val result = Writer(file.data, existing, options).run()
      ImportResult(result, file.data.settings)
    }

  /** One import: decides each record's fate, rewrites links, writes in dependency order. */
  private inner class Writer(val d: TransferData, val existing: Existing, val options: ImportOptions) {
    private val dao = db.transferDao()
    private val now = clock.now()
    private val ids = RecordKind.entries.associateWith { mutableMapOf<String, String>() }
    private val outcomes = mutableMapOf<String, Outcome>()
    private val results = mutableMapOf<RecordKind, KindResult>()
    private val takenNames =
      mapOf(
        RecordKind.EXERCISE to existing.exerciseNames.keys.toMutableSet(),
        RecordKind.INGREDIENT to existing.ingredientNames.keys.toMutableSet(),
        RecordKind.HOUSEHOLD_MEMBER to existing.memberNames.keys.toMutableSet(),
      )

    /** Where a link to [id] of [kind] now points: the record's new id, or the id itself. */
    private fun ref(kind: RecordKind, id: String) = ids.getValue(kind)[id] ?: id

    /** For optional links: null when the record is in neither the file nor the app. */
    private fun softRef(kind: RecordKind, id: String?) = id?.let { ids.getValue(kind)[it] ?: it.takeIf { existing.has(kind, it) } }

    suspend fun run(): Map<RecordKind, KindResult> {
      writeAll()
      return results
    }

    private fun decide(kind: RecordKind, fileId: String, name: String, fileUpdated: String?): Outcome {
      val found = existing.match(kind, fileId, name)
      val outcome =
        if (found == null) Outcome.Add(fileId)
        else {
          val (rec, _) = found
          when (options.overrides[ImportMatch.key(kind, fileId)] ?: options.choice) {
            ConflictChoice.SKIP -> Outcome.Skip(rec)
            ConflictChoice.REPLACE -> Outcome.Replace(rec)
            ConflictChoice.NEWER_WINS ->
              if (fileUpdated?.let(::parseInstant)?.let { it > rec.updatedAt } == true) Outcome.Replace(rec) else Outcome.Skip(rec)
            ConflictChoice.KEEP_BOTH ->
              when (kind) {
                RecordKind.BODY_WEIGHT -> Outcome.Skip(rec) // one weigh-in per day: no copy
                RecordKind.SESSION -> Outcome.Copy(clock.newId(), name) // the workout's name, as trained
                else -> Outcome.Copy(clock.newId(), copyName(kind, name))
              }
          }
        }
      ids.getValue(kind)[fileId] = outcome.targetId
      outcomes[ImportMatch.key(kind, fileId)] = outcome
      takenNames[kind]?.add(outcome.name(name).trim().lowercase())
      val r = results[kind] ?: KindResult()
      results[kind] =
        when (outcome) {
          is Outcome.Add -> r.copy(added = r.added + 1)
          is Outcome.Replace -> r.copy(replaced = r.replaced + 1)
          is Outcome.Skip -> r.copy(skipped = r.skipped + 1)
          is Outcome.Copy -> r.copy(copied = r.copied + 1)
        }
      return outcome
    }

    private fun copyName(kind: RecordKind, name: String): String {
      val taken = takenNames[kind] ?: return name.trim() + options.copySuffix
      // "Squat (imported)", then "Squat (imported 2)", …
      val suffix = options.copySuffix
      return generateSequence(1) { it + 1 }
        .map { n ->
          when {
            n == 1 -> name.trim() + suffix
            suffix.endsWith(")") -> name.trim() + suffix.dropLast(1) + " $n)"
            else -> name.trim() + suffix + " $n"
          }
        }
        .first { it.lowercase() !in taken }
    }

    /**
     * Writes one top-level record. [row] builds it for an id, name and timestamps; [children]
     * writes its children for the record's final id; [clearChildren] runs before an overwrite.
     */
    private suspend fun <E> write(
      kind: RecordKind,
      fileId: String,
      name: String,
      createdAt: String?,
      updatedAt: String?,
      row: (id: String, name: String, created: Instant, updated: Instant) -> E,
      insert: suspend (E) -> Unit,
      update: suspend (E) -> Unit,
      clearChildren: suspend (id: String) -> Unit = {},
      children: suspend (id: String) -> Unit = {},
    ) {
      when (val o = outcomes.getValue(ImportMatch.key(kind, fileId))) {
        is Outcome.Skip -> Unit
        is Outcome.Add -> {
          insert(row(o.targetId, name.trim(), instant(createdAt), instant(updatedAt)))
          children(o.targetId)
        }
        is Outcome.Copy -> {
          insert(row(o.targetId, o.newName, now, now))
          children(o.targetId)
        }
        is Outcome.Replace -> {
          update(row(o.targetId, name.trim(), o.existing.createdAt, instant(updatedAt)))
          clearChildren(o.targetId)
          children(o.targetId)
        }
      }
    }

    private suspend fun writeAll() {
      // Decide everything first, so links can point at records written later in the file.
      d.exercises.forEach { decide(RecordKind.EXERCISE, it.id, it.name, it.updatedAt) }
      d.ingredients.forEach { decide(RecordKind.INGREDIENT, it.id, it.name, it.updatedAt) }
      d.workouts.forEach { decide(RecordKind.WORKOUT, it.id, it.name, it.updatedAt) }
      d.trainingBlocks.forEach { decide(RecordKind.TRAINING_BLOCK, it.id, it.name, it.updatedAt) }
      d.recipes.forEach { decide(RecordKind.RECIPE, it.id, it.name, it.updatedAt) }
      d.dietPlans.forEach { decide(RecordKind.DIET_PLAN, it.id, it.name, it.updatedAt) }
      d.mealEntries.forEach { decide(RecordKind.MEAL, it.id, "", it.updatedAt) }
      d.shoppingLists.forEach { decide(RecordKind.SHOPPING_LIST, it.id, it.name, it.updatedAt) }
      d.householdMembers.forEach { decide(RecordKind.HOUSEHOLD_MEMBER, it.id, it.name, it.updatedAt) }
      d.workoutSessions.forEach { decide(RecordKind.SESSION, it.id, it.workoutName, it.updatedAt) }
      d.bodyWeights.forEach { decide(RecordKind.BODY_WEIGHT, it.id, it.date, it.updatedAt) }

      for (h in d.householdMembers) {
        write(
          RecordKind.HOUSEHOLD_MEMBER, h.id, h.name, h.createdAt, h.updatedAt,
          { id, name, c, u -> HouseholdMemberEntity(id, name, h.portion, h.eatsByDefault, c, u) },
          dao::insert, dao::update,
        )
      }

      for (e in d.exercises) {
        write(RecordKind.EXERCISE, e.id, e.name, e.createdAt, e.updatedAt, { id, name, c, u -> ExerciseEntity(id, name, e.muscleGroup, e.equipment, e.notes, c, u) }, dao::insert, dao::update)
      }
      for (i in d.ingredients) {
        write(
          RecordKind.INGREDIENT, i.id, i.name, i.createdAt, i.updatedAt,
          { id, name, c, u ->
            IngredientEntity(id, name, QuantityUnit.valueOf(i.defaultUnit), i.shopCategory.trim(), i.kcalPer100, i.proteinPer100, i.carbsPer100, i.fatPer100, i.gramsPerPiece, c, u)
          },
          dao::insert, dao::update,
        )
      }
      for (w in d.workouts) {
        write(
          RecordKind.WORKOUT, w.id, w.name, w.createdAt, w.updatedAt,
          { id, name, c, u -> WorkoutEntity(id, name, w.type, w.notes, c, u) },
          dao::insert, dao::update,
          clearChildren = dao::deleteWorkoutChildren,
          children = { workoutId ->
            val entries = mutableListOf<WorkoutExerciseEntity>()
            val sets = mutableListOf<SetPlanEntity>()
            w.exercises.forEachIndexed { position, item ->
              val entryId = clock.newId()
              entries += WorkoutExerciseEntity(entryId, workoutId, ref(RecordKind.EXERCISE, item.exerciseId), position, item.notes, now, now)
              item.sets.forEachIndexed { setPosition, s ->
                sets += SetPlanEntity(clock.newId(), entryId, setPosition, s.repsMin, s.repsMax, s.weightKg, s.restSeconds, now, now)
              }
            }
            dao.insertWorkoutExercises(entries)
            dao.insertSetPlans(sets)
          },
        )
      }
      for (b in d.trainingBlocks) {
        write(
          RecordKind.TRAINING_BLOCK, b.id, b.name, b.createdAt, b.updatedAt,
          { id, name, c, u -> TrainingBlockEntity(id, name, LocalDate.parse(b.startDate), LocalDate.parse(b.endDate), b.notes, c, u) },
          dao::insert, dao::update,
          clearChildren = { dao.deleteBlockWorkouts(it); dao.deleteScheduleDays(it) },
          children = { blockId ->
            dao.insertBlockWorkouts(b.workoutIds.mapIndexed { position, w -> BlockWorkoutEntity(clock.newId(), blockId, ref(RecordKind.WORKOUT, w), position, now, now) })
            dao.insertScheduleDays(
              DayOfWeek.entries.map { day ->
                val workoutId = b.schedule.firstOrNull { it.dayOfWeek == day.name }?.workoutId
                BlockScheduleDayEntity(clock.newId(), blockId, day, workoutId?.let { ref(RecordKind.WORKOUT, it) }, now, now)
              }
            )
          },
        )
      }
      for (r in d.recipes) {
        write(
          RecordKind.RECIPE, r.id, r.name, r.createdAt, r.updatedAt,
          { id, name, c, u -> RecipeEntity(id, name, r.servings, r.prepMinutes, r.steps, r.tags, r.notes, c, u) },
          dao::insert, dao::update,
          clearChildren = dao::deleteRecipeLines,
          children = { recipeId ->
            dao.insertRecipeLines(
              r.ingredients.mapIndexed { position, line ->
                RecipeIngredientEntity(clock.newId(), recipeId, ref(RecordKind.INGREDIENT, line.ingredientId), position, line.quantity, QuantityUnit.valueOf(line.unit), now, now)
              }
            )
          },
        )
      }
      for (p in d.dietPlans) {
        write(
          RecordKind.DIET_PLAN, p.id, p.name, p.createdAt, p.updatedAt,
          { id, name, c, u -> DietPlanEntity(id, name, p.notes, c, u) },
          dao::insert, dao::update,
          clearChildren = dao::deletePlanMeals,
          children = { planId ->
            dao.insertPlanMeals(
              p.meals.map { m ->
                DietPlanMealEntity(clock.newId(), planId, DayOfWeek.valueOf(m.dayOfWeek), MealSlot.valueOf(m.slot), ref(RecordKind.RECIPE, m.recipeId), m.servings, now, now)
              }
            )
          },
        )
      }
      for (m in d.mealEntries) {
        write(
          RecordKind.MEAL, m.id, "", m.createdAt, m.updatedAt,
          { id, _, c, u ->
            MealEntryEntity(
              id, LocalDate.parse(m.date), MealSlot.valueOf(m.slot), ref(RecordKind.RECIPE, m.recipeId), m.servings,
              softRef(RecordKind.DIET_PLAN, m.dietPlanId), c, u, m.eatenAt?.let(::parseInstant),
            )
          },
          dao::insert, dao::update,
          clearChildren = dao::deleteMealMembers,
          children = { mealId ->
            dao.insertMealMembers(m.memberIds.mapNotNull { softRef(RecordKind.HOUSEHOLD_MEMBER, it) }.distinct().map { MealMemberEntity(mealId, it, now) })
          },
        )
      }
      for (x in d.workoutSessions) {
        write(
          RecordKind.SESSION, x.id, x.workoutName, x.createdAt, x.updatedAt,
          { id, name, c, u ->
            WorkoutSessionEntity(id, softRef(RecordKind.WORKOUT, x.workoutId), name, LocalDate.parse(x.plannedDate), instant(x.startedAt), instant(x.finishedAt), x.notes, c, u)
          },
          dao::insert, dao::update,
          clearChildren = dao::deleteSessionSets,
          children = { sessionId ->
            dao.insertSessionSets(
              x.sets.map {
                SessionSetEntity(
                  clock.newId(), sessionId, softRef(RecordKind.EXERCISE, it.exerciseId), it.exerciseName, it.exercisePosition, it.setPosition,
                  it.targetRepsMin, it.targetRepsMax, it.restSeconds, it.weightKg, it.reps, it.done, it.completedAt?.let(::parseInstant), now, now,
                )
              }
            )
          },
        )
      }
      for (w in d.bodyWeights) {
        write(
          RecordKind.BODY_WEIGHT, w.id, w.date, w.createdAt, w.updatedAt,
          { id, _, c, u -> BodyWeightEntity(id, LocalDate.parse(w.date), w.weightKg, c, u) },
          dao::insert, dao::update,
        )
      }
      for (l in d.shoppingLists) {
        write(
          RecordKind.SHOPPING_LIST, l.id, l.name, l.createdAt, l.updatedAt,
          { id, name, c, u -> ShoppingListEntity(id, name, LocalDate.parse(l.fromDate), LocalDate.parse(l.toDate), instant(l.generatedAt), c, u) },
          dao::insert, dao::update,
          clearChildren = dao::deleteShoppingItems,
          children = { listId ->
            dao.insertShoppingItems(
              l.items.map { i ->
                ShoppingItemEntity(
                  clock.newId(), listId, softRef(RecordKind.INGREDIENT, i.ingredientId), i.name.trim(), i.quantity,
                  i.unit?.let(QuantityUnit::valueOf), i.category.trim(), i.checked, i.isManual, i.isQuantityEdited, now, now,
                )
              }
            )
          },
        )
      }
    }

    private fun instant(value: String?): Instant = value?.let(::parseInstant) ?: now
  }

  private sealed class Outcome(val targetId: String) {
    open fun name(fileName: String) = fileName

    class Add(id: String) : Outcome(id)

    class Skip(val existing: Existing.Rec) : Outcome(existing.id) {
      override fun name(fileName: String) = existing.name
    }

    class Replace(val existing: Existing.Rec) : Outcome(existing.id)

    class Copy(id: String, val newName: String) : Outcome(id) {
      override fun name(fileName: String) = newName
    }
  }

  /** Checks the whole file before anything is written; links must resolve in the file or [existing]. */
  private fun validate(data: TransferData, existing: Existing) {
    fun <T> unique(items: List<T>, what: String, key: (T) -> String) {
      val dup = items.groupBy(key).entries.firstOrNull { it.value.size > 1 } ?: return
      fail("The file lists the same $what twice (${dup.key}).")
    }
    unique(data.exercises, "exercise", { it.id })
    unique(data.exercises, "exercise", { it.name.trim().lowercase() })
    unique(data.workouts, "workout", { it.id })
    unique(data.trainingBlocks, "training block", { it.id })
    unique(data.ingredients, "ingredient", { it.id })
    unique(data.ingredients, "ingredient", { it.name.trim().lowercase() })
    unique(data.recipes, "recipe", { it.id })
    unique(data.dietPlans, "diet plan", { it.id })
    unique(data.mealEntries, "meal", { it.id })
    unique(data.shoppingLists, "shopping list", { it.id })

    val exerciseIds = data.exercises.map { it.id }.toSet()
    val workoutIds = data.workouts.map { it.id }.toSet()
    val ingredientIds = data.ingredients.map { it.id }.toSet()
    val recipeIds = data.recipes.map { it.id }.toSet()
    fun has(kind: RecordKind, inFile: Set<String>, id: String) = id in inFile || existing.has(kind, id)

    data.exercises.forEach { if (it.name.isBlank()) fail("An exercise has no name.") }
    data.workouts.forEach { w ->
      if (w.name.isBlank()) fail("A workout has no name.")
      w.exercises.forEach { item ->
        if (!has(RecordKind.EXERCISE, exerciseIds, item.exerciseId)) fail("Workout \"${w.name}\" uses an exercise that isn't in the file.")
        if (item.sets.isEmpty()) fail("Workout \"${w.name}\" has an exercise with no sets.")
        if (item.sets.size > WorkoutRepository.MAX_SETS) fail("Workout \"${w.name}\" has an exercise with more than ${WorkoutRepository.MAX_SETS} sets.")
        item.sets.forEach { s ->
          requireValid("Workout \"${w.name}\" has an invalid set") { SetValues(s.repsMin, s.repsMax, s.weightKg, s.restSeconds) }
        }
      }
    }
    data.trainingBlocks.forEach { b ->
      if (b.name.isBlank()) fail("A training block has no name.")
      val start = date(b.startDate, "Block \"${b.name}\"")
      val end = date(b.endDate, "Block \"${b.name}\"")
      if (end < start) fail("Block \"${b.name}\" ends before it starts.")
      if (b.workoutIds.size > MAX_WORKOUTS_PER_BLOCK) fail("Block \"${b.name}\" has more than $MAX_WORKOUTS_PER_BLOCK workouts.")
      if (b.workoutIds.toSet().size != b.workoutIds.size) fail("Block \"${b.name}\" lists the same workout twice.")
      b.workoutIds.forEach { if (!has(RecordKind.WORKOUT, workoutIds, it)) fail("Block \"${b.name}\" uses a workout that isn't in the file.") }
      val days = b.schedule.map { it.dayOfWeek }
      if (days.toSet().size != days.size) fail("Block \"${b.name}\" lists a day twice.")
      b.schedule.forEach { day ->
        if (DayOfWeek.entries.none { it.name == day.dayOfWeek }) fail("Block \"${b.name}\" has an unknown day \"${day.dayOfWeek}\".")
        if (day.workoutId != null && day.workoutId !in b.workoutIds) fail("Block \"${b.name}\" schedules a workout that isn't part of the block.")
      }
    }
    data.ingredients.forEach { i ->
      if (i.name.isBlank()) fail("An ingredient has no name.")
      val unit = enumOrNull<QuantityUnit>(i.defaultUnit)
      if (unit == null || unit !in INGREDIENT_UNITS) fail("Ingredient \"${i.name}\" has an unknown unit \"${i.defaultUnit}\".")
      requireValid("Ingredient \"${i.name}\" has invalid values") {
        IngredientValues(i.name, i.shopCategory, unit, i.kcalPer100, i.proteinPer100, i.carbsPer100, i.fatPer100, i.gramsPerPiece)
      }
    }
    data.recipes.forEach { r ->
      requireValid("Recipe \"${r.name}\" is invalid") { RecipeInfo(r.name, r.servings, r.prepMinutes, r.steps, r.tags, r.notes) }
      r.ingredients.forEach { line ->
        if (!has(RecordKind.INGREDIENT, ingredientIds, line.ingredientId)) fail("Recipe \"${r.name}\" uses an ingredient that isn't in the file.")
        val unit = enumOrNull<QuantityUnit>(line.unit) ?: fail("Recipe \"${r.name}\" has an unknown unit \"${line.unit}\".")
        if (!validQuantity(line.quantity, unit)) fail("Recipe \"${r.name}\" has an amount out of range.")
      }
    }
    fun meal(owner: String, slot: String, recipeId: String, servings: Double) {
      enumOrNull<MealSlot>(slot) ?: fail("$owner has an unknown meal \"$slot\".")
      if (!has(RecordKind.RECIPE, recipeIds, recipeId)) fail("$owner uses a recipe that isn't in the file.")
      if (!(servings > 0 && servings <= MealPlanRepository.MAX_SERVINGS)) fail("$owner has servings out of range.")
    }
    data.dietPlans.forEach { p ->
      if (p.name.isBlank()) fail("A diet plan has no name.")
      p.meals.forEach { m ->
        enumOrNull<DayOfWeek>(m.dayOfWeek) ?: fail("Diet plan \"${p.name}\" has an unknown day \"${m.dayOfWeek}\".")
        meal("Diet plan \"${p.name}\"", m.slot, m.recipeId, m.servings)
      }
    }
    data.mealEntries.forEach { m ->
      date(m.date, "A meal")
      meal("The meal on ${m.date}", m.slot, m.recipeId, m.servings)
    }
    unique(data.workoutSessions, "training session", { it.id })
    unique(data.bodyWeights, "weigh-in", { it.id })
    unique(data.bodyWeights, "weigh-in date", { it.date })
    unique(data.householdMembers, "person", { it.id })
    unique(data.householdMembers, "person", { it.name.trim().lowercase() })
    data.householdMembers.forEach { h ->
      if (h.name.isBlank()) fail("A person in the household has no name.")
      if (!(h.portion.isFinite() && h.portion in HouseholdRepository.MIN_PORTION..HouseholdRepository.MAX_PORTION)) fail("\"${h.name}\" has a portion out of range.")
    }
    data.bodyWeights.forEach { w ->
      date(w.date, "A weigh-in")
      if (!(w.weightKg.isFinite() && w.weightKg in WeightRules.MIN_KG..WeightRules.MAX_KG)) fail("The weigh-in on ${w.date} is out of range.")
    }
    data.workoutSessions.forEach { x ->
      val owner = "The session of \"${x.workoutName}\" on ${x.plannedDate}"
      if (x.workoutName.isBlank()) fail("A training session has no workout name.")
      date(x.plannedDate, owner)
      if (parseInstant(x.startedAt) == null || parseInstant(x.finishedAt) == null) fail("$owner has an invalid time.")
      x.sets.forEach {
        if (it.exerciseName.isBlank()) fail("$owner has a set without an exercise.")
        if (it.reps !in 0..SetValues.MAX_REPS || it.targetRepsMin !in 1..SetValues.MAX_REPS) fail("$owner has reps out of range.")
        if (it.weightKg != null && !(it.weightKg.isFinite() && it.weightKg in 0.0..SetValues.MAX_WEIGHT_KG)) fail("$owner has a weight out of range.")
        if (it.restSeconds !in 0..SetValues.MAX_REST_SECONDS || it.exercisePosition < 0 || it.setPosition < 0) fail("$owner has an invalid set.")
      }
    }
    data.mealEntries.forEach { m -> m.eatenAt?.let { if (parseInstant(it) == null) fail("The meal on ${m.date} has an invalid time.") } }
    data.shoppingLists.forEach { l ->
      val from = date(l.fromDate, "A shopping list")
      val to = date(l.toDate, "A shopping list")
      if (to < from) fail("A shopping list ends before it starts.")
      // "Update from the calendar" rebuilds the list for its dates, so they follow the same limit.
      if (ChronoUnit.DAYS.between(from, to) >= ShoppingRepository.MAX_DAYS) fail("A shopping list covers more than ${ShoppingRepository.MAX_DAYS} days.")
      l.items.forEach { i ->
        if (i.name.isBlank()) fail("A shopping list item has no name.")
        if (i.quantity != null) {
          val unit = i.unit?.let { enumOrNull<QuantityUnit>(it) } ?: fail("Shopping item \"${i.name}\" has an amount without a known unit.")
          if (!validQuantity(i.quantity, unit)) fail("Shopping item \"${i.name}\" has an amount out of range.")
        } else if (i.unit != null && enumOrNull<QuantityUnit>(i.unit) == null) {
          fail("Shopping item \"${i.name}\" has an unknown unit \"${i.unit}\".")
        }
      }
    }
  }

  private inline fun requireValid(what: String, block: () -> Unit) {
    try {
      block()
    } catch (e: IllegalArgumentException) {
      fail("$what: ${e.message}.")
    }
  }

  private fun date(value: String, owner: String): LocalDate =
    try {
      LocalDate.parse(value)
    } catch (e: RuntimeException) {
      fail("$owner has an invalid date \"$value\".")
    }

  private fun fail(message: String): Nothing = throw ImportException(message)

  companion object {
    const val MAX_FILE_BYTES = 20 * 1024 * 1024
  }
}

private inline fun <reified T : Enum<T>> enumOrNull(name: String): T? = enumValues<T>().firstOrNull { it.name == name }

private fun parseInstant(value: String): Instant? = runCatching { Instant.parse(value) }.getOrNull()

/** The app's records as the importer needs them: ids, names and dates, read in the import's transaction. */
private class Existing(
  private val byKind: Map<RecordKind, Map<String, Rec>>,
  val exerciseNames: Map<String, Rec>,
  val ingredientNames: Map<String, Rec>,
  val memberNames: Map<String, Rec> = emptyMap(),
  /** Weigh-ins by date: one per day, so a weigh-in on the same date is the same one. */
  val weightDates: Map<String, Rec> = emptyMap(),
) {
  class Rec(val id: String, val name: String, val createdAt: Instant, val updatedAt: Instant)

  val recipeNames: Map<String, String>
    get() = byKind[RecordKind.RECIPE].orEmpty().mapValues { it.value.name }

  fun has(kind: RecordKind, id: String) = byKind[kind]?.containsKey(id) == true

  /** Same id; for exercises, ingredients and people also the same name (ignoring case); weigh-ins the same date. */
  fun match(kind: RecordKind, id: String, name: String): Pair<Rec, Boolean>? {
    // One weigh-in per day: the date decides first, so a replace can never make two on one day.
    if (kind == RecordKind.BODY_WEIGHT) weightDates[name]?.let { return it to (it.id != id) }
    byKind[kind]?.get(id)?.let { return it to false }
    val byName =
      when (kind) {
        RecordKind.EXERCISE -> exerciseNames
        RecordKind.INGREDIENT -> ingredientNames
        RecordKind.HOUSEHOLD_MEMBER -> memberNames
        else -> return null
      }
    return byName[name.trim().lowercase()]?.let { it to true }
  }

  companion object {
    val EMPTY = Existing(emptyMap(), emptyMap(), emptyMap())

    suspend fun read(db: OpenWeightDatabase): Existing {
      val d = db.transferDao()
      val recipes = d.recipes()
      val recipeNames = recipes.associate { it.id to it.name }
      val map =
        mapOf(
          RecordKind.EXERCISE to d.exercises().map { Rec(it.id, it.name, it.createdAt, it.updatedAt) },
          RecordKind.WORKOUT to d.workouts().map { Rec(it.id, it.name, it.createdAt, it.updatedAt) },
          RecordKind.TRAINING_BLOCK to d.blocks().map { Rec(it.id, it.name, it.createdAt, it.updatedAt) },
          RecordKind.INGREDIENT to d.ingredients().map { Rec(it.id, it.name, it.createdAt, it.updatedAt) },
          RecordKind.RECIPE to recipes.map { Rec(it.id, it.name, it.createdAt, it.updatedAt) },
          RecordKind.DIET_PLAN to d.dietPlans().map { Rec(it.id, it.name, it.createdAt, it.updatedAt) },
          RecordKind.MEAL to d.mealEntries().map { Rec(it.id, recipeNames[it.recipeId].orEmpty(), it.createdAt, it.updatedAt) },
          RecordKind.SHOPPING_LIST to d.shoppingLists().map { Rec(it.id, it.name, it.createdAt, it.updatedAt) },
          RecordKind.SESSION to d.allSessions().map { Rec(it.id, it.workoutName, it.createdAt, it.updatedAt) },
          RecordKind.BODY_WEIGHT to d.bodyWeights().map { Rec(it.id, it.date.toString(), it.createdAt, it.updatedAt) },
          RecordKind.HOUSEHOLD_MEMBER to d.householdMembers().map { Rec(it.id, it.name, it.createdAt, it.updatedAt) },
        )
      val byKind = map.mapValues { (_, recs) -> recs.associateBy { it.id } }
      return Existing(
        byKind,
        exerciseNames = map.getValue(RecordKind.EXERCISE).associateBy { it.name.trim().lowercase() },
        ingredientNames = map.getValue(RecordKind.INGREDIENT).associateBy { it.name.trim().lowercase() },
        memberNames = map.getValue(RecordKind.HOUSEHOLD_MEMBER).associateBy { it.name.trim().lowercase() },
        weightDates = map.getValue(RecordKind.BODY_WEIGHT).associateBy { it.name },
      )
    }
  }
}
