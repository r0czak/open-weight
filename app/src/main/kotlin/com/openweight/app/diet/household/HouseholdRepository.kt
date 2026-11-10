package com.openweight.app.diet.household

import androidx.room.withTransaction
import com.openweight.app.core.TimeAndIds
import com.openweight.app.core.database.OpenWeightDatabase
import com.openweight.app.core.database.entity.HouseholdMemberEntity
import com.openweight.app.core.database.entity.MealMemberEntity
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class HouseholdMember(val id: String, val name: String, val portion: Double, val eatsByDefault: Boolean)

/** The household: who else eats your meals, and how much. Rules live here. */
@Singleton
class HouseholdRepository @Inject constructor(private val db: OpenWeightDatabase, private val clock: TimeAndIds) {
  private val dao = db.householdDao()

  fun members(): Flow<List<HouseholdMember>> = dao.observeMembers().map { list -> list.map { it.toModel() } }

  /** Adds ([id] null) or changes a member. Names are unique (ignoring case); a portion is 0.1–3 of yours. */
  suspend fun save(id: String?, name: String, portion: Double, eatsByDefault: Boolean): String {
    val clean = name.trim()
    require(clean.isNotEmpty()) { "Name can't be empty" }
    require(portion.isFinite() && portion in MIN_PORTION..MAX_PORTION) { "Portion must be between $MIN_PORTION and $MAX_PORTION" }
    return db.withTransaction {
      val all = dao.getMembers()
      require(all.none { it.id != id && it.name.equals(clean, ignoreCase = true) }) { "\"$clean\" is already in the household" }
      require(id != null || all.size < MAX_MEMBERS) { "At most $MAX_MEMBERS people" }
      val now = clock.now()
      val existing = id?.let { dao.getMember(it) }
      if (existing != null) {
        dao.updateMember(existing.copy(name = clean, portion = portion, eatsByDefault = eatsByDefault, updatedAt = now))
        existing.id
      } else {
        val newId = clock.newId()
        dao.insertMember(HouseholdMemberEntity(newId, clean, portion, eatsByDefault, now, now))
        newId
      }
    }
  }

  /** Removes a member; the meals they shared stay, now just yours. */
  suspend fun delete(id: String) = dao.deleteMember(id)

  /** Who else eats [mealId]; unknown ids are ignored. */
  suspend fun setMealMembers(mealId: String, memberIds: Set<String>) {
    db.withTransaction {
      val known = dao.getMembers().map { it.id }.toSet()
      dao.deleteMealMembers(mealId)
      val now = clock.now()
      dao.insertMealMembers(memberIds.filter { it in known }.map { MealMemberEntity(mealId, it, now) })
    }
  }

  companion object {
    const val MIN_PORTION = 0.1
    const val MAX_PORTION = 3.0
    const val MAX_MEMBERS = 10
  }
}

internal fun HouseholdMemberEntity.toModel() = HouseholdMember(id, name, portion, eatsByDefault)
