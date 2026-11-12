package com.openweight.app.progress

import androidx.room.withTransaction
import com.openweight.app.core.TimeAndIds
import com.openweight.app.core.database.OpenWeightDatabase
import com.openweight.app.core.database.entity.BodyWeightEntity
import com.openweight.app.home.WeekPlan
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class WeighIn(val id: String, val date: LocalDate, val weightKg: Double)

/** The average of one week's weigh-ins (Monday to Sunday). */
data class WeekAverage(val monday: LocalDate, val averageKg: Double, val count: Int)

/**
 * The trend your plans adjust calories from: the latest week's average against the average two
 * weeks before it. [changeKg] is null until both weeks have a weigh-in.
 */
data class WeightTrend(val weeks: List<WeekAverage>, val latest: WeekAverage?, val twoWeeksBefore: WeekAverage?) {
  val changeKg: Double?
    get() = if (latest != null && twoWeeksBefore != null) latest.averageKg - twoWeeksBefore.averageKg else null
}

object WeightRules {
  const val MIN_KG = 20.0
  const val MAX_KG = 400.0

  /** Weekly averages, oldest first, and the two-week change from the latest week with a weigh-in. */
  fun trend(entries: List<WeighIn>): WeightTrend {
    val weeks =
      entries
        .groupBy { WeekPlan.mondayOf(it.date) }
        .toSortedMap()
        .map { (monday, list) -> WeekAverage(monday, list.map { it.weightKg }.average(), list.size) }
    val latest = weeks.lastOrNull()
    val before = latest?.let { l -> weeks.firstOrNull { it.monday == l.monday.minusWeeks(2) } }
    return WeightTrend(weeks, latest, before)
  }
}

/** Body weight: one weigh-in per day, kept in kilograms. Rules live here. */
@Singleton
class BodyWeightRepository @Inject constructor(private val db: OpenWeightDatabase, private val clock: TimeAndIds) {
  private val dao = db.bodyWeightDao()

  /** Every weigh-in, oldest first. */
  fun entries(): Flow<List<WeighIn>> = dao.observeAll().map { list -> list.map { WeighIn(it.id, it.date, it.weightKg) } }

  /** Records [weightKg] for [date] (not in the future), replacing that day's weigh-in if there is one. */
  suspend fun log(date: LocalDate, weightKg: Double) {
    require(weightKg.isFinite() && weightKg in WeightRules.MIN_KG..WeightRules.MAX_KG) { "Weight out of range" }
    require(!date.isAfter(clock.today())) { "Can't log a weight in the future" }
    db.withTransaction {
      val now = clock.now()
      val existing = dao.getByDate(date)
      if (existing != null) dao.update(existing.copy(weightKg = weightKg, updatedAt = now))
      else dao.insert(BodyWeightEntity(clock.newId(), date, weightKg, now, now))
    }
  }

  suspend fun delete(id: String) = dao.delete(id)
}
